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
import com.tansoflow.tansocore.model.spend.type.OutcomeKind;
import com.tansoflow.tansocore.model.spend.type.OutcomeSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Completed issues, by team key, over Linear's GraphQL API. Scope is a comma-separated team-key list, or "*". */
@Slf4j
@Component
public class LinearOutcomePuller implements OutcomePuller {
    private static final String QUERY = """
            query($after: String, $from: DateTimeOrDuration!, $to: DateTimeOrDuration!) {
              issues(first: 100, after: $after, filter: { completedAt: { gte: $from, lt: $to } }) {
                pageInfo { hasNextPage endCursor }
                nodes { id identifier title url completedAt team { key } assignee { email displayName } }
              }
            }""";
    private final RestClient client;

    public LinearOutcomePuller(RestClient.Builder builder, @Value("${app.spend.linear-base-url:https://api.linear.app/graphql}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    @Override
    public OutcomeSource source() {
        return OutcomeSource.LINEAR;
    }

    @Override
    public void probe(String token, String scope) {
        post(token, "{ viewer { id } }", Map.of());
    }

    @Override
    public List<OutcomeRecord> pull(String token, String scope, Instant from, Instant to) {
        List<String> teams = teams(scope);
        List<OutcomeRecord> out = new ArrayList<>();
        String after = null;
        do {
            Map<String, Object> vars = new HashMap<>();
            vars.put("from", from.toString());
            vars.put("to", to.toString());
            if (after != null) {
                vars.put("after", after);
            }
            JsonNode issues = post(token, QUERY, vars).path("data").path("issues");
            for (JsonNode n : issues.path("nodes")) {
                String team = n.path("team").path("key").asText(null);
                if (!teams.isEmpty() && (team == null || !teams.contains(team))) {
                    continue;
                }
                Instant completedAt = Instant.parse(n.path("completedAt").asText());
                if (completedAt.isBefore(from) || !completedAt.isBefore(to)) {
                    continue; // trust the filter, verify the row
                }
                out.add(new OutcomeRecord(OutcomeKind.ISSUE_DONE, n.path("identifier").asText(),
                        n.path("title").asText(null), n.path("url").asText(null),
                        n.path("assignee").path("email").asText(null), null,
                        completedAt));
            }
            after = issues.path("pageInfo").path("hasNextPage").asBoolean(false)
                    ? issues.path("pageInfo").path("endCursor").asText(null) : null;
        } while (after != null);
        return out;
    }

    public static List<String> teams(String scope) {
        List<String> teams = new ArrayList<>();
        for (String s : scope.split(",")) {
            String t = s.trim();
            if (!t.isEmpty() && !t.equals("*")) {
                teams.add(t.toUpperCase());
            }
        }
        return teams; // empty = every team
    }

    private JsonNode post(String token, String query, Map<String, Object> variables) {
        try {
            JsonNode body = client.post().uri("")
                    .header("Authorization", token)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", VendorErrors.USER_AGENT)
                    .body(Map.of("query", query, "variables", variables))
                    .retrieve()
                    .body(JsonNode.class);
            if (body != null && body.has("errors") && body.get("errors").size() > 0) {
                throw new VendorApiException(200, "Linear returned an error: " + body.get("errors").get(0).path("message").asText());
            }
            return body;
        } catch (RestClientResponseException e) {
            throw new VendorApiException(e.getStatusCode().value(),
                    "Linear returned " + e.getStatusCode().value() + ": " + VendorErrors.message(e.getResponseBodyAsString()));
        } catch (ResourceAccessException e) {
            throw new VendorApiException("Could not reach Linear: " + e.getMessage(), e);
        }
    }
}
