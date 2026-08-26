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
import java.util.function.Function;

/**
 * Anthropic Admin API: the org usage report (tokens by model / workspace /
 * key / tier), the cost report (the vendor's own price, in cents), and the
 * Claude Code report (per-user tokens and estimated cost). All three are
 * daily buckets, at most 31 per page, cursor-paginated.
 */
@Slf4j
@Component
public class AnthropicUsagePuller implements VendorUsagePuller {
    static final String VERSION_HEADER = "2023-06-01";
    private static final int MAX_DAILY_BUCKETS = 31;
    private final RestClient client;

    public AnthropicUsagePuller(RestClient.Builder builder, @Value("${app.spend.anthropic-base-url:https://api.anthropic.com}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    @Override
    public VendorProvider provider() {
        return VendorProvider.ANTHROPIC;
    }

    @Override
    public void probe(String adminKey) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        get(adminKey, b -> b.path("/v1/organizations/usage_report/messages")
                .queryParam("starting_at", startOf(today))
                .queryParam("bucket_width", "1d")
                .queryParam("limit", 1)
                .build());
    }

    @Override
    public List<UsageBucketRecord> pull(String adminKey, LocalDate from, LocalDate toExclusive) {
        List<UsageBucketRecord> rows = new ArrayList<>();
        rows.addAll(paginate(adminKey, page -> b -> b.path("/v1/organizations/usage_report/messages")
                        .queryParam("starting_at", startOf(from))
                        .queryParam("ending_at", startOf(toExclusive))
                        .queryParam("bucket_width", "1d")
                        .queryParam("limit", MAX_DAILY_BUCKETS)
                        .queryParam("group_by[]", "model", "workspace_id", "api_key_id", "service_tier")
                        .queryParamIfPresent("page", java.util.Optional.ofNullable(page))
                        .build(),
                this::usageRows));
        rows.addAll(paginate(adminKey, page -> b -> b.path("/v1/organizations/cost_report")
                        .queryParam("starting_at", startOf(from))
                        .queryParam("ending_at", startOf(toExclusive))
                        .queryParam("bucket_width", "1d")
                        .queryParam("limit", MAX_DAILY_BUCKETS)
                        .queryParam("group_by[]", "workspace_id", "description")
                        .queryParamIfPresent("page", java.util.Optional.ofNullable(page))
                        .build(),
                this::costRows));
        for (LocalDate day = from; day.isBefore(toExclusive); day = day.plusDays(1)) {
            LocalDate d = day;
            rows.addAll(paginate(adminKey, page -> b -> b.path("/v1/organizations/usage_report/claude_code")
                            .queryParam("starting_at", d.toString())
                            .queryParam("limit", 1000)
                            .queryParamIfPresent("page", java.util.Optional.ofNullable(page))
                            .build(),
                    body -> claudeCodeRows(body, d)));
        }
        return rows;
    }

    @Override
    public List<ActorMetricRecord> pullActorMetrics(String adminKey, String scope, LocalDate from, LocalDate toExclusive) {
        List<ActorMetricRecord> out = new ArrayList<>();
        for (LocalDate day = from; day.isBefore(toExclusive); day = day.plusDays(1)) {
            LocalDate d = day;
            String page = null;
            do {
                String pageToken = page;
                JsonNode body = get(adminKey, b -> b.path("/v1/organizations/usage_report/claude_code")
                        .queryParam("starting_at", d.toString())
                        .queryParam("limit", 1000)
                        .queryParamIfPresent("page", java.util.Optional.ofNullable(pageToken))
                        .build());
                for (JsonNode r : body.path("data")) {
                    JsonNode actor = r.path("actor");
                    String actorId = actor.hasNonNull("email_address") ? actor.get("email_address").asText() : text(actor, "api_key_name");
                    JsonNode core = r.path("core_metrics");
                    int accepted = 0;
                    int rejected = 0;
                    for (JsonNode tool : r.path("tool_actions")) {
                        accepted += tool.path("accepted").asInt();
                        rejected += tool.path("rejected").asInt();
                    }
                    long cents = 0;
                    for (JsonNode m : r.path("model_breakdown")) {
                        cents += m.path("estimated_cost").path("amount").asLong();
                    }
                    out.add(new ActorMetricRecord(d, actorId, text(r, "terminal_type"),
                            core.path("num_sessions").asInt(), null,
                            core.path("lines_of_code").path("added").asInt(), core.path("lines_of_code").path("removed").asInt(), null,
                            accepted, rejected,
                            core.path("commits_by_claude_code").asInt(), core.path("pull_requests_by_claude_code").asInt(),
                            null, BigDecimal.valueOf(cents)));
                }
                page = body.path("has_more").asBoolean(false) ? text(body, "next_page") : null;
            } while (page != null);
        }
        return out;
    }

    private List<UsageBucketRecord> usageRows(JsonNode body) {
        List<UsageBucketRecord> out = new ArrayList<>();
        for (JsonNode bucket : body.path("data")) {
            Instant start = Instant.parse(bucket.path("starting_at").asText());
            Instant end = Instant.parse(bucket.path("ending_at").asText());
            for (JsonNode r : bucket.path("results")) {
                long cacheCreation = r.path("cache_creation").path("ephemeral_5m_input_tokens").asLong()
                        + r.path("cache_creation").path("ephemeral_1h_input_tokens").asLong();
                out.add(new UsageBucketRecord(VendorUsageSource.USAGE_API, start, end,
                        text(r, "model"), text(r, "workspace_id"), text(r, "api_key_id"), null,
                        text(r, "service_tier"), null,
                        r.path("uncached_input_tokens").asLong(), r.path("cache_read_input_tokens").asLong(),
                        cacheCreation, r.path("output_tokens").asLong(), null, null, null));
            }
        }
        return out;
    }

    private List<UsageBucketRecord> costRows(JsonNode body) {
        List<UsageBucketRecord> out = new ArrayList<>();
        for (JsonNode bucket : body.path("data")) {
            Instant start = Instant.parse(bucket.path("starting_at").asText());
            Instant end = Instant.parse(bucket.path("ending_at").asText());
            for (JsonNode r : bucket.path("results")) {
                // amount is a decimal string in the currency's lowest unit (cents for USD)
                out.add(new UsageBucketRecord(VendorUsageSource.COST_API, start, end,
                        text(r, "model"), text(r, "workspace_id"), null, null,
                        text(r, "service_tier"), text(r, "description"),
                        0, 0, 0, 0, null,
                        new BigDecimal(r.path("amount").asText("0")), r.path("currency").asText("USD")));
            }
        }
        return out;
    }

    private List<UsageBucketRecord> claudeCodeRows(JsonNode body, LocalDate day) {
        List<UsageBucketRecord> out = new ArrayList<>();
        Instant start = startOf(day);
        Instant end = startOf(day.plusDays(1));
        for (JsonNode r : body.path("data")) {
            JsonNode actor = r.path("actor");
            String actorId = actor.hasNonNull("email_address")
                    ? actor.get("email_address").asText()
                    : text(actor, "api_key_name");
            for (JsonNode m : r.path("model_breakdown")) {
                JsonNode tokens = m.path("tokens");
                JsonNode cost = m.path("estimated_cost");
                out.add(new UsageBucketRecord(VendorUsageSource.CLAUDE_CODE_API, start, end,
                        text(m, "model"), null, null, actorId, null, text(r, "terminal_type"),
                        tokens.path("input").asLong(), tokens.path("cache_read").asLong(),
                        tokens.path("cache_creation").asLong(), tokens.path("output").asLong(),
                        r.path("core_metrics").path("num_sessions").isNumber()
                                ? r.path("core_metrics").path("num_sessions").asLong() : null,
                        cost.isMissingNode() ? null : BigDecimal.valueOf(cost.path("amount").asLong()),
                        cost.path("currency").asText("USD")));
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
                    .header("x-api-key", adminKey)
                    .header("anthropic-version", VERSION_HEADER)
                    .header("User-Agent", VendorErrors.USER_AGENT)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new VendorApiException(e.getStatusCode().value(),
                    "Anthropic admin API returned " + e.getStatusCode().value() + ": " + VendorErrors.message(e.getResponseBodyAsString()));
        } catch (ResourceAccessException e) {
            throw new VendorApiException("Could not reach the Anthropic admin API: " + e.getMessage(), e);
        }
    }

    private static Instant startOf(LocalDate day) {
        return day.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
