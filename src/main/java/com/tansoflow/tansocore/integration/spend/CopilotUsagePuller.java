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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tansoflow.tansocore.model.exception.VendorApiException;
import com.tansoflow.tansocore.model.spend.type.VendorProvider;
import com.tansoflow.tansocore.model.spend.type.VendorUsageSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import java.util.List;

/**
 * GitHub Copilot usage metrics for one org (the scope). Each day has a
 * per-user report reachable through signed download links; a record per user
 * carries interactions, accepted code, lines, AI credits and, for CLI and app
 * use, token counts. There is no dollar figure — credits are "not for
 * invoicing" — so only tokens land as usage; credits go on the person.
 */
@Slf4j
@Component
public class CopilotUsagePuller implements VendorUsagePuller {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RestClient client;
    private final RestClient anyHost;

    public CopilotUsagePuller(RestClient.Builder builder, @Value("${app.spend.github-base-url:https://api.github.com}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
        this.anyHost = builder.build();
    }

    @Override
    public VendorProvider provider() {
        return VendorProvider.COPILOT;
    }

    @Override
    public boolean requiresScope() {
        return true;
    }

    @Override
    public void probe(String adminKey) {
        throw new IllegalArgumentException("Copilot needs the GitHub org as scope");
    }

    @Override
    public void probe(String adminKey, String scope) {
        get(adminKey, "/orgs/" + org(scope));
    }

    @Override
    public List<UsageBucketRecord> pull(String adminKey, LocalDate from, LocalDate toExclusive) {
        throw new IllegalArgumentException("Copilot needs the GitHub org as scope");
    }

    @Override
    public List<UsageBucketRecord> pull(String adminKey, String scope, LocalDate from, LocalDate toExclusive) {
        List<UsageBucketRecord> out = new ArrayList<>();
        for (LocalDate day = from; day.isBefore(toExclusive); day = day.plusDays(1)) {
            for (JsonNode r : dayRecords(adminKey, scope, day)) {
                long prompt = 0;
                long output = 0;
                long requests = 0;
                for (String bucket : new String[]{"totals_by_cli", "totals_by_copilot_app"}) {
                    JsonNode t = r.path(bucket);
                    if (t.isObject()) {
                        prompt += t.path("token_usage").path("prompt_tokens_sum").asLong();
                        output += t.path("token_usage").path("output_tokens_sum").asLong();
                        requests += t.path("request_count").asLong();
                    }
                }
                if (prompt == 0 && output == 0) {
                    continue;
                }
                Instant s = day.atStartOfDay(ZoneOffset.UTC).toInstant();
                out.add(new UsageBucketRecord(VendorUsageSource.USAGE_API, s, day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
                        null, null, null, r.path("user_login").asText(null), null, "Copilot CLI/app tokens",
                        prompt, 0, 0, output, requests, null, null));
            }
        }
        return out;
    }

    @Override
    public List<ActorMetricRecord> pullActorMetrics(String adminKey, String scope, LocalDate from, LocalDate toExclusive) {
        List<ActorMetricRecord> out = new ArrayList<>();
        for (LocalDate day = from; day.isBefore(toExclusive); day = day.plusDays(1)) {
            for (JsonNode r : dayRecords(adminKey, scope, day)) {
                int sessions = r.path("totals_by_cli").path("session_count").asInt() + r.path("totals_by_copilot_app").path("session_count").asInt();
                String tool = r.path("used_copilot_coding_agent").asBoolean(false) ? "coding-agent"
                        : r.path("used_agent").asBoolean(false) ? "agent"
                        : r.path("used_cli").asBoolean(false) ? "cli"
                        : r.path("used_chat").asBoolean(false) ? "chat" : "completions";
                out.add(new ActorMetricRecord(day, r.path("user_login").asText(null), tool,
                        sessions == 0 ? null : sessions,
                        r.path("user_initiated_interaction_count").asInt(),
                        r.path("loc_added_sum").asInt(), r.path("loc_deleted_sum").asInt(), r.path("loc_suggested_to_add_sum").asInt(),
                        r.path("code_acceptance_activity_count").asInt(), null, null, null,
                        r.hasNonNull("ai_credits_used") ? new BigDecimal(r.get("ai_credits_used").asText()) : null, null));
            }
        }
        return out;
    }

    /** The day's per-user report: 204 = nothing that day; otherwise follow every download link. */
    private List<JsonNode> dayRecords(String adminKey, String scope, LocalDate day) {
        List<JsonNode> records = new ArrayList<>();
        ResponseEntity<JsonNode> resp;
        try {
            resp = client.get().uri("/orgs/" + org(scope) + "/copilot/metrics/reports/users-1-day?day=" + day)
                    .header("Authorization", "Bearer " + adminKey)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("User-Agent", VendorErrors.USER_AGENT)
                    .retrieve().toEntity(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new VendorApiException(e.getStatusCode().value(),
                    "GitHub returned " + e.getStatusCode().value() + " for Copilot metrics: " + VendorErrors.message(e.getResponseBodyAsString()));
        } catch (ResourceAccessException e) {
            throw new VendorApiException("Could not reach GitHub: " + e.getMessage(), e);
        }
        if (resp.getStatusCode() == HttpStatus.NO_CONTENT || resp.getBody() == null) {
            return records;
        }
        for (JsonNode link : resp.getBody().path("download_links")) {
            String body;
            try {
                body = anyHost.get().uri(URI.create(link.asText()))
                        .header("User-Agent", VendorErrors.USER_AGENT)
                        .retrieve().body(String.class);
            } catch (RestClientResponseException e) {
                throw new VendorApiException(e.getStatusCode().value(), "Copilot report download returned " + e.getStatusCode().value());
            } catch (ResourceAccessException e) {
                throw new VendorApiException("Could not download the Copilot report: " + e.getMessage(), e);
            }
            records.addAll(parseReport(body));
        }
        return records;
    }

    /** Reports are documented as NDJSON; the example schema is a JSON array. Accept both. */
    static List<JsonNode> parseReport(String body) {
        List<JsonNode> out = new ArrayList<>();
        if (body == null || body.isBlank()) {
            return out;
        }
        String trimmed = body.trim();
        try {
            if (trimmed.startsWith("[")) {
                for (JsonNode n : MAPPER.readTree(trimmed)) {
                    out.add(n);
                }
                return out;
            }
            for (String line : trimmed.split("\\r?\\n")) {
                if (!line.isBlank()) {
                    out.add(MAPPER.readTree(line));
                }
            }
        } catch (java.io.IOException e) {
            throw new VendorApiException("Copilot report was not JSON: " + e.getMessage(), e);
        }
        return out;
    }

    static String org(String scope) {
        if (scope == null || scope.isBlank() || !scope.trim().matches("[A-Za-z0-9-]+")) {
            throw new IllegalArgumentException("Scope must be the GitHub organization name");
        }
        return scope.trim();
    }

    private JsonNode get(String adminKey, String path) {
        try {
            return client.get().uri(path).header("Authorization", "Bearer " + adminKey)
                    .header("Accept", "application/vnd.github+json").header("X-GitHub-Api-Version", "2022-11-28")
                    .header("User-Agent", VendorErrors.USER_AGENT).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new VendorApiException(e.getStatusCode().value(),
                    "GitHub returned " + e.getStatusCode().value() + " for " + path + ": " + VendorErrors.message(e.getResponseBodyAsString()));
        } catch (ResourceAccessException e) {
            throw new VendorApiException("Could not reach GitHub: " + e.getMessage(), e);
        }
    }
}
