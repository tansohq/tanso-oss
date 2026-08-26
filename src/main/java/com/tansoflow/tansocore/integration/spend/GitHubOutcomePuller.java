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
import java.util.List;

/**
 * Merged pull requests per repo. GitHub lists closed PRs newest-updated first
 * and has no "merged since" filter, so the pull walks pages until it passes
 * the window's start. Scope is a comma-separated "owner/repo" list.
 */
@Slf4j
@Component
public class GitHubOutcomePuller implements OutcomePuller {
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 20;
    private final RestClient client;

    public GitHubOutcomePuller(RestClient.Builder builder, @Value("${app.spend.github-base-url:https://api.github.com}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    @Override
    public OutcomeSource source() {
        return OutcomeSource.GITHUB;
    }

    @Override
    public void probe(String token, String scope) {
        for (String repo : repos(scope)) {
            get(token, "/repos/" + repo);
        }
    }

    @Override
    public List<OutcomeRecord> pull(String token, String scope, Instant from, Instant to) {
        List<OutcomeRecord> out = new ArrayList<>();
        for (String repo : repos(scope)) {
            for (int page = 1; page <= MAX_PAGES; page++) {
                JsonNode prs = get(token, "/repos/" + repo + "/pulls?state=closed&sort=updated&direction=desc&per_page="
                        + PAGE_SIZE + "&page=" + page);
                if (!prs.isArray() || prs.isEmpty()) {
                    break;
                }
                boolean pastWindow = false;
                for (JsonNode pr : prs) {
                    if (!pr.hasNonNull("merged_at")) {
                        continue;
                    }
                    Instant mergedAt = Instant.parse(pr.get("merged_at").asText());
                    if (!mergedAt.isBefore(to) ) {
                        continue;
                    }
                    if (mergedAt.isBefore(from)) {
                        // updated_at is monotone per page order; once merges fall before the window and
                        // updates are older than the window start, nothing further can be inside it.
                        if (pr.hasNonNull("updated_at") && Instant.parse(pr.get("updated_at").asText()).isBefore(from)) {
                            pastWindow = true;
                        }
                        continue;
                    }
                    String tool = aiTool(pr);
                    out.add(new OutcomeRecord(OutcomeKind.PR_MERGED,
                            repo + "#" + pr.path("number").asLong(),
                            text(pr, "title"), text(pr, "html_url"),
                            null, pr.path("user").path("login").asText(null), mergedAt,
                            tool != null, tool));
                }
                if (pastWindow || prs.size() < PAGE_SIZE) {
                    break;
                }
            }
        }
        return out;
    }

    /**
     * Which assistant was in a pull request, from what GitHub already shows:
     * a label ("claude-code-assisted", "copilot", "cursor", "ai-assisted"), a
     * co-author or Made-with trailer in the body, or a bot author. Null when
     * nothing says so — absence is not evidence.
     */
    static String aiTool(JsonNode pr) {
        for (JsonNode label : pr.path("labels")) {
            String name = label.path("name").asText("").toLowerCase();
            if (name.contains("claude")) return "claude-code";
            if (name.contains("copilot")) return "copilot";
            if (name.contains("cursor")) return "cursor";
            if (name.contains("codex")) return "codex";
            if (name.contains("ai-assisted") || name.contains("ai_assisted") || name.equals("ai")) return "ai";
        }
        String body = pr.path("body").asText("").toLowerCase();
        if (body.contains("co-authored-by: claude") || body.contains("generated with [claude code]") || body.contains("claude code")) return "claude-code";
        if (body.contains("co-authored-by: copilot") || body.contains("copilot")) return "copilot";
        if (body.contains("made-with: cursor") || body.contains("co-authored-by: cursor")) return "cursor";
        if (body.contains("co-authored-by: codex") || body.contains("codex")) return "codex";
        JsonNode user = pr.path("user");
        String login = user.path("login").asText("").toLowerCase();
        if ("bot".equalsIgnoreCase(user.path("type").asText("")) || login.endsWith("[bot]")) {
            String bot = login.replace("[bot]", "");
            if (bot.contains("copilot")) return "copilot";
            if (bot.contains("claude")) return "claude-code";
            if (bot.contains("cursor")) return "cursor";
            if (bot.contains("codex")) return "codex";
            if (bot.contains("devin")) return "devin";
            return bot;
        }
        return null;
    }

    public static List<String> repos(String scope) {
        List<String> repos = new ArrayList<>();
        for (String s : scope.split(",")) {
            String r = s.trim();
            if (!r.isEmpty()) {
                if (!r.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
                    throw new IllegalArgumentException("Not an owner/repo: " + r);
                }
                repos.add(r);
            }
        }
        if (repos.isEmpty()) {
            throw new IllegalArgumentException("List at least one owner/repo");
        }
        return repos;
    }

    private JsonNode get(String token, String path) {
        try {
            return client.get().uri(path)
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("User-Agent", VendorErrors.USER_AGENT)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new VendorApiException(e.getStatusCode().value(),
                    "GitHub returned " + e.getStatusCode().value() + " for " + path + ": " + VendorErrors.message(e.getResponseBodyAsString()));
        } catch (ResourceAccessException e) {
            throw new VendorApiException("Could not reach GitHub: " + e.getMessage(), e);
        }
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
