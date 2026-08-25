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
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cursor Admin API (Enterprise). Usage events carry tokens and what was
 * actually charged per call, so one pull yields both a usage row and a cost
 * row per (day, person, model). Daily usage data is the per-person activity:
 * accepts, rejects, lines, requests. Windows are capped at 30 days by Cursor;
 * the key goes in as the Basic-auth username.
 */
@Slf4j
@Component
public class CursorUsagePuller implements VendorUsagePuller {
    private static final int PAGE_SIZE = 1000;
    private static final int MAX_PAGES = 200;
    private final RestClient client;

    public CursorUsagePuller(RestClient.Builder builder, @Value("${app.spend.cursor-base-url:https://api.cursor.com}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    @Override
    public VendorProvider provider() {
        return VendorProvider.CURSOR;
    }

    @Override
    public int maxWindowDays() {
        return 30;
    }

    @Override
    public void probe(String adminKey) {
        get(adminKey, "/teams/members");
    }

    @Override
    public List<UsageBucketRecord> pull(String adminKey, LocalDate from, LocalDate toExclusive) {
        long start = epochMs(from);
        long end = epochMs(toExclusive) - 1;
        // (day, email, model) → [uncached, cacheRead, cacheWrite, output, requests, chargedCents]
        Map<String, long[]> agg = new LinkedHashMap<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            JsonNode body = post(adminKey, "/teams/filtered-usage-events",
                    Map.of("startDate", start, "endDate", end, "page", page, "pageSize", PAGE_SIZE));
            for (JsonNode e : body.path("usageEvents")) {
                Instant ts = Instant.ofEpochMilli(e.path("timestamp").asLong());
                LocalDate day = ts.atZone(ZoneOffset.UTC).toLocalDate();
                String email = e.path("userEmail").asText(null);
                String model = e.path("model").asText(null);
                String key = day + "|" + email + "|" + model;
                long[] a = agg.computeIfAbsent(key, k -> new long[6]);
                JsonNode t = e.path("tokenUsage");
                a[0] += t.path("inputTokens").asLong();
                a[1] += t.path("cacheReadTokens").asLong();
                a[2] += t.path("cacheWriteTokens").asLong();
                a[3] += t.path("outputTokens").asLong();
                a[4] += 1;
                a[5] += e.path("chargedCents").asLong();
            }
            if (!body.path("pagination").path("hasNextPage").asBoolean(false)) {
                break;
            }
        }
        List<UsageBucketRecord> out = new ArrayList<>();
        for (Map.Entry<String, long[]> en : agg.entrySet()) {
            String[] k = en.getKey().split("\\|", -1);
            LocalDate day = LocalDate.parse(k[0]);
            String email = "null".equals(k[1]) ? null : k[1];
            String model = "null".equals(k[2]) ? null : k[2];
            Instant s = day.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant e2 = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            long[] a = en.getValue();
            out.add(new UsageBucketRecord(VendorUsageSource.USAGE_API, s, e2, model, null, null, email, null, null,
                    a[0], a[1], a[2], a[3], a[4], null, null));
            out.add(new UsageBucketRecord(VendorUsageSource.COST_API, s, e2, model, null, null, email, null, "Cursor charged",
                    0, 0, 0, 0, null, BigDecimal.valueOf(a[5]), "USD"));
        }
        return out;
    }

    @Override
    public List<ActorMetricRecord> pullActorMetrics(String adminKey, String scope, LocalDate from, LocalDate toExclusive) {
        long start = epochMs(from);
        long end = epochMs(toExclusive) - 1;
        List<ActorMetricRecord> out = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            JsonNode body = post(adminKey, "/teams/daily-usage-data",
                    Map.of("startDate", start, "endDate", end, "page", page, "pageSize", 500));
            for (JsonNode d : body.path("data")) {
                if (d.has("isActive") && !d.path("isActive").asBoolean(true)) {
                    continue;
                }
                LocalDate day = d.hasNonNull("day") ? LocalDate.parse(d.get("day").asText().substring(0, 10))
                        : Instant.ofEpochMilli(d.path("date").asLong()).atZone(ZoneOffset.UTC).toLocalDate();
                int requests = d.path("agentRequests").asInt() + d.path("chatRequests").asInt()
                        + d.path("composerRequests").asInt() + d.path("cmdkUsages").asInt();
                out.add(new ActorMetricRecord(day, d.path("email").asText(null), d.path("mostUsedModel").asText(null),
                        null, requests,
                        d.path("acceptedLinesAdded").asInt(), d.path("acceptedLinesDeleted").asInt(), d.path("totalLinesAdded").asInt(),
                        d.path("totalAccepts").asInt() + d.path("totalTabsAccepted").asInt(), d.path("totalRejects").asInt(),
                        null, null, null, null));
            }
            if (!body.path("pagination").path("hasNextPage").asBoolean(false)) {
                break;
            }
        }
        return out;
    }

    private JsonNode get(String adminKey, String path) {
        try {
            return client.get().uri(path).header("Authorization", basic(adminKey))
                    .header("User-Agent", VendorErrors.USER_AGENT).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new VendorApiException(e.getStatusCode().value(),
                    "Cursor returned " + e.getStatusCode().value() + ": " + VendorErrors.message(e.getResponseBodyAsString()));
        } catch (ResourceAccessException e) {
            throw new VendorApiException("Could not reach Cursor: " + e.getMessage(), e);
        }
    }

    private JsonNode post(String adminKey, String path, Map<String, Object> body) {
        try {
            return client.post().uri(path).header("Authorization", basic(adminKey))
                    .header("Content-Type", "application/json").header("User-Agent", VendorErrors.USER_AGENT)
                    .body(body).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new VendorApiException(e.getStatusCode().value(),
                    "Cursor returned " + e.getStatusCode().value() + ": " + VendorErrors.message(e.getResponseBodyAsString()));
        } catch (ResourceAccessException e) {
            throw new VendorApiException("Could not reach Cursor: " + e.getMessage(), e);
        }
    }

    static String basic(String adminKey) {
        return "Basic " + Base64.getEncoder().encodeToString((adminKey + ":").getBytes(StandardCharsets.UTF_8));
    }

    private static long epochMs(LocalDate day) {
        return day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }
}
