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
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A self-hosted LiteLLM proxy as a vendor source. The connection's scope is
 * the proxy URL and the admin key is its master key. Every request through
 * the proxy is a spend-log row with the team, key and user LiteLLM resolved,
 * so this is the one source with per-request identity across every model
 * provider — and the one place a budget can actually be enforced.
 */
@Slf4j
@Component
public class LiteLlmUsagePuller implements VendorUsagePuller {
    private final RestClient client;

    public LiteLlmUsagePuller(RestClient.Builder builder) {
        this.client = builder.build();
    }

    @Override
    public VendorProvider provider() {
        return VendorProvider.LITELLM;
    }

    @Override
    public boolean requiresScope() {
        return true;
    }

    @Override
    public void probe(String adminKey) {
        throw new IllegalArgumentException("LiteLLM needs the proxy URL as scope");
    }

    @Override
    public void probe(String adminKey, String scope) {
        get(adminKey, base(scope) + "/health/liveliness");
    }

    @Override
    public List<UsageBucketRecord> pull(String adminKey, LocalDate from, LocalDate toExclusive) {
        throw new IllegalArgumentException("LiteLLM needs the proxy URL as scope");
    }

    @Override
    public List<UsageBucketRecord> pull(String adminKey, String scope, LocalDate from, LocalDate toExclusive) {
        JsonNode rows = get(adminKey, base(scope) + "/spend/logs?start_date=" + from + "&end_date=" + toExclusive.minusDays(1) + "&summarize=false");
        // (day, model, team, key, user) → [prompt, completion, requests, spend in millionths of a dollar]
        Map<String, long[]> agg = new LinkedHashMap<>();
        Map<String, String> providers = new LinkedHashMap<>();
        for (JsonNode r : rows.isArray() ? rows : rows.path("data")) {
            String start = r.path("startTime").asText(null);
            if (start == null) {
                continue;
            }
            LocalDate day = parseDay(start);
            if (day.isBefore(from) || !day.isBefore(toExclusive)) {
                continue;
            }
            String key = String.join("|", day.toString(), text(r, "model"), text(r, "team_id"), text(r, "api_key"), actor(r));
            long[] a = agg.computeIfAbsent(key, k -> new long[4]);
            a[0] += r.path("prompt_tokens").asLong();
            a[1] += r.path("completion_tokens").asLong();
            a[2] += 1;
            a[3] += Math.round(r.path("spend").asDouble() * 1_000_000);
            if (r.hasNonNull("custom_llm_provider")) {
                providers.put(key, r.get("custom_llm_provider").asText());
            }
        }
        List<UsageBucketRecord> out = new ArrayList<>();
        for (Map.Entry<String, long[]> e : agg.entrySet()) {
            String[] k = e.getKey().split("\\|", -1);
            LocalDate day = LocalDate.parse(k[0]);
            Instant s = day.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant en = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            String model = nullIfEmpty(k[1]);
            String team = nullIfEmpty(k[2]);
            String apiKey = nullIfEmpty(k[3]);
            String user = nullIfEmpty(k[4]);
            long[] a = e.getValue();
            out.add(new UsageBucketRecord(VendorUsageSource.USAGE_API, s, en, model, team, apiKey, user, null, null,
                    a[0], 0, 0, a[1], a[2], null, null));
            BigDecimal cents = BigDecimal.valueOf(a[3]).movePointLeft(4); // millionths of a dollar → cents
            String provider = providers.get(e.getKey());
            out.add(new UsageBucketRecord(VendorUsageSource.COST_API, s, en, model, team, apiKey, user, null,
                    provider == null ? "LiteLLM spend" : "LiteLLM spend via " + provider,
                    0, 0, 0, 0, null, cents, "USD"));
        }
        return out;
    }

    @Override
    public List<ActorMetricRecord> pullActorMetrics(String adminKey, String scope, LocalDate from, LocalDate toExclusive) {
        JsonNode rows = get(adminKey, base(scope) + "/spend/logs?start_date=" + from + "&end_date=" + toExclusive.minusDays(1) + "&summarize=false");
        Map<String, int[]> perActorDay = new LinkedHashMap<>();
        for (JsonNode r : rows.isArray() ? rows : rows.path("data")) {
            String start = r.path("startTime").asText(null);
            String actor = actor(r);
            if (start == null || actor.isEmpty()) {
                continue;
            }
            LocalDate day = parseDay(start);
            if (day.isBefore(from) || !day.isBefore(toExclusive)) {
                continue;
            }
            perActorDay.computeIfAbsent(day + "|" + actor, k -> new int[1])[0]++;
        }
        List<ActorMetricRecord> out = new ArrayList<>();
        for (Map.Entry<String, int[]> e : perActorDay.entrySet()) {
            String[] k = e.getKey().split("\\|", 2);
            out.add(new ActorMetricRecord(LocalDate.parse(k[0]), k[1], "litellm", null, e.getValue()[0],
                    null, null, null, null, null, null, null, null, null));
        }
        return out;
    }

    /** Prefer the end-user the caller passed; fall back to the internal user who owns the key. */
    private static String actor(JsonNode r) {
        String endUser = text(r, "end_user");
        if (!endUser.isEmpty()) {
            return endUser;
        }
        return text(r, "user");
    }

    static LocalDate parseDay(String startTime) {
        String t = startTime.trim();
        if (t.length() >= 10) {
            try {
                return LocalDate.parse(t.substring(0, 10));
            } catch (java.time.format.DateTimeParseException ignored) {
                // fall through
            }
        }
        return Instant.parse(t).atZone(ZoneOffset.UTC).toLocalDate();
    }

    static String base(String scope) {
        if (scope == null || scope.isBlank() || !(scope.trim().startsWith("http://") || scope.trim().startsWith("https://"))) {
            throw new IllegalArgumentException("Scope must be the LiteLLM proxy URL, e.g. https://llm.internal:4000");
        }
        String s = scope.trim();
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private JsonNode get(String adminKey, String url) {
        try {
            return client.get().uri(URI.create(url))
                    .header("Authorization", "Bearer " + adminKey)
                    .header("User-Agent", VendorErrors.USER_AGENT)
                    .retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new VendorApiException(e.getStatusCode().value(),
                    "LiteLLM returned " + e.getStatusCode().value() + ": " + VendorErrors.message(e.getResponseBodyAsString()));
        } catch (ResourceAccessException e) {
            throw new VendorApiException("Could not reach LiteLLM at " + url + ": " + e.getMessage(), e);
        }
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : "";
    }

    private static String nullIfEmpty(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
