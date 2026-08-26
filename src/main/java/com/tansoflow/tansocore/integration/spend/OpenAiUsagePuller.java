/*
 * Tanso Core - open-source B2B SaaS monetization engine
 * Copyright (C) 2026  Douglas Baek
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.tansoflow.tansocore.integration.spend;

import com.fasterxml.jackson.databind.JsonNode;
import com.tansoflow.tansocore.model.exception.VendorApiException;
import com.tansoflow.tansocore.model.spend.type.VendorProvider;
import com.tansoflow.tansocore.model.spend.type.VendorUsageSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.util.UriBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * OpenAI organisation usage (completions, by project / user / key / model)
 * and costs (by project / line item, in dollars). Daily buckets, at most 31
 * per page, cursor-paginated. group_by is a repeated plain parameter here;
 * Anthropic wants the bracketed form. Cached input tokens are reported as a subset of
 * input_tokens, so uncached = input - cached.
 */
@Slf4j
@Component
public class OpenAiUsagePuller implements VendorUsagePuller {
    private static final int MAX_DAILY_BUCKETS = 31;
    private final RestClient client;

    public OpenAiUsagePuller(RestClient.Builder builder, @Value("${app.spend.openai-base-url:https://api.openai.com}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    @Override
    public VendorProvider provider() {
        return VendorProvider.OPENAI;
    }

    @Override
    public void probe(String adminKey) {
        get(adminKey, b -> b.path("/v1/organization/costs")
                .queryParam("start_time", epoch(LocalDate.now(ZoneOffset.UTC)))
                .queryParam("limit", 1)
                .build());
    }

    @Override
    public List<UsageBucketRecord> pull(String adminKey, LocalDate from, LocalDate toExclusive) {
        List<UsageBucketRecord> rows = new ArrayList<>();
        rows.addAll(paginate(adminKey, page -> b -> b.path("/v1/organization/usage/completions")
                        .queryParam("start_time", epoch(from))
                        .queryParam("end_time", epoch(toExclusive))
                        .queryParam("bucket_width", "1d")
                        .queryParam("limit", MAX_DAILY_BUCKETS)
                        .queryParam("group_by", "project_id", "user_id", "api_key_id", "model")
                        .queryParamIfPresent("page", Optional.ofNullable(page))
                        .build(),
                this::usageRows));
        rows.addAll(paginate(adminKey, page -> b -> b.path("/v1/organization/costs")
                        .queryParam("start_time", epoch(from))
                        .queryParam("end_time", epoch(toExclusive))
                        .queryParam("bucket_width", "1d")
                        .queryParam("limit", MAX_DAILY_BUCKETS)
                        .queryParam("group_by", "project_id", "line_item")
                        .queryParamIfPresent("page", Optional.ofNullable(page))
                        .build(),
                this::costRows));
        return rows;
    }

    private List<UsageBucketRecord> usageRows(JsonNode body) {
        List<UsageBucketRecord> out = new ArrayList<>();
        for (JsonNode bucket : body.path("data")) {
            Instant start = Instant.ofEpochSecond(bucket.path("start_time").asLong());
            Instant end = Instant.ofEpochSecond(bucket.path("end_time").asLong());
            for (JsonNode r : bucket.path("results")) {
                long input = r.path("input_tokens").asLong();
                long cached = r.path("input_cached_tokens").asLong();
                out.add(new UsageBucketRecord(VendorUsageSource.USAGE_API, start, end,
                        text(r, "model"), text(r, "project_id"), text(r, "api_key_id"), text(r, "user_id"),
                        r.path("batch").asBoolean(false) ? "batch" : null, null,
                        Math.max(0, input - cached), cached, 0, r.path("output_tokens").asLong(),
                        r.path("num_model_requests").isNumber() ? r.path("num_model_requests").asLong() : null,
                        null, null));
            }
        }
        return out;
    }

    private List<UsageBucketRecord> costRows(JsonNode body) {
        List<UsageBucketRecord> out = new ArrayList<>();
        for (JsonNode bucket : body.path("data")) {
            Instant start = Instant.ofEpochSecond(bucket.path("start_time").asLong());
            Instant end = Instant.ofEpochSecond(bucket.path("end_time").asLong());
            for (JsonNode r : bucket.path("results")) {
                JsonNode amount = r.path("amount");
                // amount.value is in whole currency units (dollars); the ledger keeps cents
                BigDecimal cents = new BigDecimal(amount.path("value").asText("0")).movePointRight(2);
                String lineItem = text(r, "line_item");
                out.add(new UsageBucketRecord(VendorUsageSource.COST_API, start, end,
                        modelFromLineItem(lineItem), text(r, "project_id"), null, null, null, lineItem,
                        0, 0, 0, 0, null, cents, amount.path("currency").asText("usd").toUpperCase()));
            }
        }
        return out;
    }

    private List<UsageBucketRecord> paginate(String adminKey,
                                             Function<String, Function<UriBuilder, URI>> request,
                                             Function<JsonNode, List<UsageBucketRecord>> parse) {
        List<UsageBucketRecord> out = new ArrayList<>();
        String page = null;
        do {
            JsonNode body = get(adminKey, request.apply(page));
            out.addAll(parse.apply(body));
            page = body.path("has_more").asBoolean(false) ? text(body, "next_page") : null;
        } while (page != null);
        return out;
    }

    private JsonNode get(String adminKey, Function<UriBuilder, URI> uri) {
        try {
            return client.get().uri(uri)
                    .header("Authorization", "Bearer " + adminKey)
                    .header("User-Agent", VendorErrors.USER_AGENT)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new VendorApiException(e.getStatusCode().value(),
                    "OpenAI admin API returned " + e.getStatusCode().value() + ": " + VendorErrors.message(e.getResponseBodyAsString()));
        } catch (ResourceAccessException e) {
            throw new VendorApiException("Could not reach the OpenAI admin API: " + e.getMessage(), e);
        }
    }

    /** OpenAI line items read "gpt-4o-mini, input" / "gpt-4o-mini, output"; the model is the part before the comma. */
    static String modelFromLineItem(String lineItem) {
        if (lineItem == null) {
            return null;
        }
        int comma = lineItem.indexOf(',');
        String head = (comma < 0 ? lineItem : lineItem.substring(0, comma)).trim();
        return head.isEmpty() ? null : head;
    }

    private static long epoch(LocalDate day) {
        return day.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
