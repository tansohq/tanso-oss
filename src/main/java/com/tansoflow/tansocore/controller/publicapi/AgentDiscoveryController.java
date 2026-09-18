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
package com.tansoflow.tansocore.controller.publicapi;

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.AccountSetting;
import com.tansoflow.tansocore.repository.AccountRepository;
import com.tansoflow.tansocore.repository.AccountSettingRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The front door for an agent that has this instance's address and nothing else.
 *
 * <p>No {@code produces} on the mappings: an agent that sends {@code Accept: application/json} would get
 * 406 for the Markdown and text documents. The content type is set on each response instead.
 *
 * <p>Without it the catalog is unreachable in practice: {@code /public/v1/catalog/{slug}/pricing.json}
 * is public, but the slug appears nowhere an unauthenticated caller can read, and every other path on
 * the instance answers 403. These two documents name the enabled catalogs so discovery can start from
 * the host name alone.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Agent Discovery", description = "llms.txt and agent manifest, no authentication")
@ConditionalOnProperty(name = "app.modules.monetization.enabled", havingValue = "true", matchIfMissing = true)
public class AgentDiscoveryController {

    private final AccountRepository accountRepository;
    private final AccountSettingRepository accountSettingRepository;

    private record Catalog(String slug, boolean signupEnabled) {}

    /** Accounts that published a catalog. An account without a slug or with the catalog off is invisible here. */
    private List<Catalog> publishedCatalogs() {
        List<Catalog> catalogs = new ArrayList<>();
        for (Account account : accountRepository.findAll()) {
            if (account.getSlug() == null || account.getSlug().isBlank()) {
                continue;
            }
            AccountSetting settings = accountSettingRepository.findAccountSettingById(account.getId());
            if (settings == null || !settings.isPublicCatalogEnabled()) {
                continue;
            }
            catalogs.add(new Catalog(account.getSlug(),
                    settings.isAgentSignupEnabled() && settings.getAgentSignupDefaultPlanId() != null));
        }
        return catalogs;
    }

    private static String baseUrl(HttpServletRequest request) {
        return request.getRequestURL().toString().replace(request.getRequestURI(), "");
    }

    @GetMapping("/llms.txt")
    @Operation(summary = "Agent-readable index of the published catalogs",
            description = "Plain text pointing at each enabled catalog's pricing.json, its signup URL, and the API spec.")
    public ResponseEntity<String> llmsTxt(HttpServletRequest request) {
        String base = baseUrl(request);
        List<Catalog> catalogs = publishedCatalogs();
        StringBuilder out = new StringBuilder();
        out.append("# Tanso\n\n");
        out.append("> Self-hosted monetization for this product: plans, usage, credits and entitlements.\n")
                .append("> This instance serves the billing API. Product documentation lives with the product.\n\n");

        if (catalogs.isEmpty()) {
            out.append("## Catalogs\n\n")
                    .append("No public catalog is published on this instance. Ask the operator for credentials\n")
                    .append("and the base URL, or ask them to enable the public catalog.\n\n");
        } else {
            out.append("## Catalogs\n\n");
            for (Catalog catalog : catalogs) {
                out.append("- [").append(catalog.slug()).append(" pricing](")
                        .append(base).append("/public/v1/catalog/").append(catalog.slug())
                        .append("/pricing.json): plans, prices, credit weights and governance flags in the ")
                        .append("agent-serve pricing.json format.\n");
                if (catalog.signupEnabled()) {
                    out.append("- [").append(catalog.slug()).append(" signup](")
                            .append(base).append("/public/v1/catalog/").append(catalog.slug())
                            .append("/signup): POST an empty body, or an optional email and name, to create a ")
                            .append("provisional customer and receive a scoped API key in the response. ")
                            .append("No CAPTCHA, no email confirmation.\n");
                }
            }
            out.append("\n");
        }

        out.append("## How to use this API\n\n")
                .append("Read pricing.json, POST to the catalog's signup URL (no fields required), then send the\n")
                .append("returned key as `X-API-Key` on every call. The key is pinned to its own customer:\n")
                .append("reading another customer's data returns 403.\n\n")
                .append("A new customer is provisional: it has the free plan's limits and expires after the\n")
                .append("operator's provisional window (14 days by default) unless it pays. Paying claims the\n")
                .append("account and removes the expiry. `GET ").append(base).append("/api/v1/client/customers/{referenceId}/status`\n")
                .append("returns status, expiry, remaining limits and spend. Every 402 and every limit or access 403 carries\n")
                .append("an `error` object with `gate` (`payment`, `budget` or `scope`), `action`, `url`, `poll`,\n")
                .append("`retry_after`, `message` and `detail` (the error id) that says what to do next. There is no claim\n")
                .append("gate: paying is the claim. Once the account expires its keys are revoked and every call returns\n")
                .append("401; sign up again. Step by step: [agent-signup.md](").append(base).append("/agent-signup.md).\n\n")
                .append("- Check an entitlement before doing work: `POST ").append(base).append("/api/v1/client/entitlements`\n")
                .append("- Record what you used afterwards: `POST ").append(base).append("/api/v1/client/events`\n")
                .append("- Read your own usage and credit burndown: `GET ").append(base).append("/api/v1/client/customers/{referenceId}/usage`\n")
                .append("- Buy credits: `POST ").append(base).append("/api/v1/client/credits/purchases`\n")
                .append("- Change plan: `POST ").append(base).append("/api/v1/client/subscriptions` with the plan key from pricing.json\n\n")
                .append("A paid action with no payment method answers 402 `payment_required` with `error.url` (the checkout\n")
                .append("page to hand to a human) and `error.poll` (`GET ").append(base).append("/api/v1/client/checkout-sessions/{id}`).\n")
                .append("Signup email is optional, recorded as the owner contact only, never sent to, and every signup\n")
                .append("creates a new customer.\n\n");

        out.append("## Specification\n\n")
                .append("- [OpenAPI](").append(base).append("/v3/api-docs)\n")
                .append("- [API reference](").append(base).append("/swagger-ui.html)\n")
                .append("- [Agent manifest](").append(base).append("/.well-known/agent.json)\n")
                .append("- [Signup runbook](").append(base).append("/agent-signup.md)\n")
                .append("- [Skills index](").append(base).append("/.well-known/agent-skills/index.json)\n\n")
                .append("Do not use this API to read other customers' data or to spend past a key's budget;\n")
                .append("both are refused and both are logged.\n");

        return ResponseEntity.ok().contentType(MediaType.valueOf("text/plain; charset=utf-8")).body(out.toString());
    }

    @GetMapping("/.well-known/agent.json")
    @Operation(summary = "Agent manifest",
            description = "Machine-readable pointer to the published catalogs, the signup path, and the auth scheme.")
    public ResponseEntity<Map<String, Object>> agentManifest(HttpServletRequest request) {
        String base = baseUrl(request);
        List<Catalog> catalogs = publishedCatalogs();

        List<Map<String, Object>> catalogEntries = new ArrayList<>();
        for (Catalog catalog : catalogs) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("slug", catalog.slug());
            entry.put("pricing", base + "/public/v1/catalog/" + catalog.slug() + "/pricing.json");
            if (catalog.signupEnabled()) {
                entry.put("signup", base + "/public/v1/catalog/" + catalog.slug() + "/signup");
            }
            catalogEntries.add(entry);
        }

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("name", "Tanso");
        manifest.put("description", "Self-hosted monetization API: plans, usage, credits and entitlements.");
        manifest.put("url", base);
        manifest.put("catalogs", catalogEntries);
        manifest.put("agent_signup", catalogs.stream().anyMatch(Catalog::signupEnabled));
        manifest.put("auth", Map.of(
                "scheme", "api-key",
                "header", "X-API-Key",
                "obtained_from", catalogs.stream().filter(Catalog::signupEnabled).findFirst()
                        .map(c -> base + "/public/v1/catalog/" + c.slug() + "/signup")
                        .orElse("the operator of this instance")));
        manifest.put("payment", Map.of(
                "protocol", "http-402",
                "description", "Paid actions without a payment method answer 402 payment_required. Every 402 and "
                        + "every limit or access 403 body carries error.gate (payment, budget or scope), error.action, "
                        + "error.url, error.poll and error.retry_after; poll error.poll until checkout completes."));
        manifest.put("openapi", base + "/v3/api-docs");
        manifest.put("docs", base + "/llms.txt");
        manifest.put("runbook", base + "/agent-signup.md");
        manifest.put("skills", base + "/.well-known/agent-skills/index.json");

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(manifest);
    }

    @GetMapping("/.well-known/agent-skills/index.json")
    @Operation(summary = "Agent skills index",
            description = "Lists the signup runbook as a skill an agent can load.")
    public ResponseEntity<Map<String, Object>> skillsIndex(HttpServletRequest request) {
        String base = baseUrl(request);
        Map<String, Object> skill = new LinkedHashMap<>();
        skill.put("name", "tanso-agent-signup");
        skill.put("description", "Sign up for a provisional customer account on this Tanso instance, store the "
                + "returned API key, use the free plan, and handle the 402/403 gate envelope, status, owner "
                + "and expiry rules.");
        skill.put("url", base + "/agent-signup.md");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(Map.of("skills", List.of(skill)));
    }

    @GetMapping("/agent-signup.md")
    @Operation(summary = "Agent signup runbook",
            description = "Step-by-step Markdown: read pricing.json, sign up, store the key, check status, "
                    + "use the free plan, handle gates, set the owner, and what expiry means.")
    public ResponseEntity<String> runbook(HttpServletRequest request) {
        String base = baseUrl(request);
        List<Catalog> catalogs = publishedCatalogs();

        StringBuilder catalogSection = new StringBuilder();
        String exampleSlug = "{slug}";
        if (catalogs.isEmpty()) {
            catalogSection.append("No public catalog is published on this instance. Ask the operator to enable ")
                    .append("the public catalog and agent signup. The steps below use `{slug}` as a placeholder.\n");
        } else {
            catalogSection.append("Catalogs on this instance:\n\n");
            for (Catalog catalog : catalogs) {
                catalogSection.append("- `").append(catalog.slug()).append("`: pricing at ")
                        .append(base).append("/public/v1/catalog/").append(catalog.slug()).append("/pricing.json");
                if (catalog.signupEnabled()) {
                    catalogSection.append(", signup at ")
                            .append(base).append("/public/v1/catalog/").append(catalog.slug()).append("/signup");
                    if ("{slug}".equals(exampleSlug)) {
                        exampleSlug = catalog.slug();
                    }
                } else {
                    catalogSection.append(", signup not enabled");
                }
                catalogSection.append("\n");
            }
            if ("{slug}".equals(exampleSlug)) {
                catalogSection.append("\nNone of these catalogs has signup enabled. The steps below use `{slug}` ")
                        .append("as a placeholder.\n");
            } else if (catalogs.size() > 1) {
                catalogSection.append("\nThe examples below use `").append(exampleSlug)
                        .append("`. Swap in the slug you want.\n");
            }
        }

        String body = RUNBOOK
                .replace("{catalogs}", catalogSection.toString())
                .replace("{base}", base)
                .replace("{slug}", exampleSlug);
        return ResponseEntity.ok().contentType(MediaType.valueOf("text/markdown; charset=utf-8")).body(body);
    }

    private static final String RUNBOOK = loadRunbook();

    private static String loadRunbook() {
        try {
            return new org.springframework.core.io.ClassPathResource("agent/agent-signup.md")
                    .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("agent/agent-signup.md is missing from the classpath", e);
        }
    }
}
