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
 * <p>Without it the catalog is unreachable in practice: {@code /public/v1/catalog/{slug}/pricing.json}
 * is public, but the slug appears nowhere an unauthenticated caller can read, and every other path on
 * the instance answers 403. These two documents name the enabled catalogs so discovery can start from
 * the host name alone.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Agent Discovery", description = "llms.txt and agent manifest — no authentication")
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

    @GetMapping(value = "/llms.txt", produces = "text/plain; charset=utf-8")
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
                .append("returns status, expiry, remaining limits and spend. Every 402 and every limit 403 carries an\n")
                .append("`error` object with `gate`, `action`, `url`, `poll`, `retry_after` and `message` that says\n")
                .append("what to do next. Step by step: [agent-signup.md](").append(base).append("/agent-signup.md).\n\n")
                .append("- Check an entitlement before doing work: `POST ").append(base).append("/api/v1/client/entitlements`\n")
                .append("- Record what you used afterwards: `POST ").append(base).append("/api/v1/client/events`\n")
                .append("- Read your own usage and credit burndown: `GET ").append(base).append("/api/v1/client/customers/{referenceId}/usage`\n")
                .append("- Buy credits: `POST ").append(base).append("/api/v1/client/credits/purchases`\n")
                .append("- Change plan: `POST ").append(base).append("/api/v1/client/subscriptions` with the plan key from pricing.json\n\n")
                .append("A paid action with no payment method answers 402 with a `checkoutUrl` to hand to a human,\n")
                .append("and a `checkoutSessionId` to poll at `GET ").append(base).append("/api/v1/client/checkout-sessions/{id}`.\n\n");

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

    @GetMapping(value = "/.well-known/agent.json", produces = MediaType.APPLICATION_JSON_VALUE)
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
                "description", "Paid actions without a payment method answer 402 with checkoutUrl and checkoutSessionId."));
        manifest.put("openapi", base + "/v3/api-docs");
        manifest.put("docs", base + "/llms.txt");
        manifest.put("runbook", base + "/agent-signup.md");
        manifest.put("skills", base + "/.well-known/agent-skills/index.json");

        return ResponseEntity.ok(manifest);
    }

    @GetMapping(value = "/.well-known/agent-skills/index.json", produces = MediaType.APPLICATION_JSON_VALUE)
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
        return ResponseEntity.ok(Map.of("skills", List.of(skill)));
    }

    @GetMapping(value = "/agent-signup.md", produces = "text/markdown; charset=utf-8")
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

    private static final String RUNBOOK = """
            # Tanso agent signup runbook

            How an agent gets an account on this instance, uses the free plan, and pays when it needs more.
            No CAPTCHA, no email confirmation, no human in the loop until money moves.

            ## Catalogs

            {catalogs}
            ## 1. Read the catalog

            ```
            curl {base}/public/v1/catalog/{slug}/pricing.json
            ```

            Plans, features, credit weights and governance flags. Plan keys from this document are what
            you pass to `POST /api/v1/client/subscriptions` later.

            ## 2. Sign up

            Every field is optional. An empty body works.

            ```
            curl -X POST {base}/public/v1/catalog/{slug}/signup \\
              -H 'Content-Type: application/json' \\
              -d '{}'
            ```

            With an owner email, a display name and a spend mandate:

            ```
            curl -X POST {base}/public/v1/catalog/{slug}/signup \\
              -H 'Content-Type: application/json' \\
              -d '{
                "email": "ops@example.com",
                "name": "research-agent-1",
                "spend_mandate": { "max_amount": 50.00, "currency": "usd", "period": "month" }
              }'
            ```

            - `email`: optional. Must be a valid address if present. Signing up again with the same email
              on the same catalog returns the same `customerReferenceId` with a new key. No second customer.
            - `name`: optional, up to 100 characters.
            - `spend_mandate`: optional. Only honored when the operator enabled spend mandates; otherwise the
              response has `"spend_mandate": null`.

            Errors: `404` when there is no catalog at that slug or signup is not enabled on it. `429` when a
            rate limit is hit (see Rate limits).

            ## 3. The response (201)

            ```json
            {
              "success": true,
              "data": {
                "customerReferenceId": "agent_7f3c9a2e",
                "apiKey": "ck_test_9b1d...e4",
                "apiKeyScopes": ["read", "purchase"],
                "plan": "free",
                "status": "provisional",
                "expires_at": "2026-10-01T00:00:00Z",
                "limits": {
                  "features": {
                    "ai.chat": { "included": 5, "period": "month", "unlimited": false }
                  },
                  "spend_cap": null,
                  "currency": "usd"
                },
                "spend_mandate": {
                  "status": "pending",
                  "setup_url": "https://checkout.stripe.com/c/pay/cs_test_...",
                  "max_amount": 50.00,
                  "period": "month"
                },
                "status_url": "{base}/api/v1/client/customers/agent_7f3c9a2e/status",
                "owner_url": "{base}/api/v1/client/customers/agent_7f3c9a2e/owner",
                "nextSteps": {
                  "base_url": "{base}",
                  "pricing": "{base}/public/v1/catalog/{slug}/pricing.json",
                  "check_entitlement_template": "{base}/api/v1/client/entitlements/agent_7f3c9a2e/{featureKey}",
                  "record_usage": "{base}/api/v1/client/events",
                  "usage_summary": "{base}/api/v1/client/customers/agent_7f3c9a2e/usage",
                  "credit_balances": "{base}/api/v1/client/credits/agent_7f3c9a2e/pools",
                  "buy_credits": "{base}/api/v1/client/credits/purchases",
                  "change_plan": "{base}/api/v1/client/subscriptions",
                  "docs": "{base}/swagger-ui.html"
                }
              }
            }
            ```

            `expires_at` is null once the account is claimed. `spend_mandate` is null when you did not send
            one or the operator has mandates off. `limits.features` is keyed by feature key; `included` is
            null when the plan does not meter that feature; `unlimited: true` means no cap.

            ## 4. Store the key once

            `apiKey` is returned exactly once. It is not retrievable later. Store it before doing anything
            else. Send it on every call:

            ```
            -H 'X-API-Key: ck_test_9b1d...e4'
            ```

            The key is pinned to its own customer. Reading another customer's data returns 403.

            ## 5. Check status

            ```
            curl {base}/api/v1/client/customers/agent_7f3c9a2e/status \\
              -H 'X-API-Key: ck_test_9b1d...e4'
            ```

            ```json
            {
              "success": true,
              "data": {
                "customerReferenceId": "agent_7f3c9a2e",
                "status": "provisional",
                "expires_at": "2026-10-01T00:00:00Z",
                "claimed_at": null,
                "plan": "free",
                "limits": { "features": { "ai.chat": { "included": 5, "period": "month", "unlimited": false } }, "spend_cap": null, "currency": "usd" },
                "remaining": { "ai.chat": 3 },
                "spend": null,
                "owner_email": "ops@example.com",
                "spend_mandate": { "status": "pending", "max_amount": 50.00, "period": "month" }
              }
            }
            ```

            `status` is `provisional`, `claimed` or `expired`. `remaining` is per feature key, null when
            the feature is unmetered. `spend` is null until a spend cap exists; then it has `cap`, `spent`,
            `remaining`, `currency` and `resets_at`. `spend_mandate.status` is `none`, `pending` or `active`.

            ## 6. Use the free plan

            Check before you do work, record after:

            ```
            curl {base}/api/v1/client/entitlements/agent_7f3c9a2e/ai.chat \\
              -H 'X-API-Key: ck_test_9b1d...e4'

            curl -X POST {base}/api/v1/client/events \\
              -H 'X-API-Key: ck_test_9b1d...e4' -H 'Content-Type: application/json' \\
              -d '{ "customerReferenceId": "agent_7f3c9a2e", "featureKey": "ai.chat",
                    "eventName": "chat completion", "eventIdempotencyKey": "evt-1", "usageUnits": 1 }'
            ```

            Usage and burndown: `GET {base}/api/v1/client/customers/agent_7f3c9a2e/usage`.
            Buy credits: `POST {base}/api/v1/client/credits/purchases`.
            Change plan: `POST {base}/api/v1/client/subscriptions` with a plan key from pricing.json.
            Mutating requests accept an `Idempotency-Key` header; replays return the stored response for 24h.

            ## 7. Gates: 402 and 403

            Every 402, and every 403 caused by a limit or access rule, has this `error` object. A 402 also
            keeps its normal `data` payload (the subscription or credit purchase result).

            ```json
            {
              "success": false,
              "error": {
                "code": "payment_required",
                "gate": "payment",
                "action": "complete_checkout",
                "url": "https://checkout.stripe.com/c/pay/cs_test_...",
                "poll": "{base}/api/v1/client/checkout-sessions/cs_test_...",
                "retry_after": null,
                "message": "Open url to add a payment method, then poll the checkout session until it is complete."
              },
              "data": { "checkoutUrl": "https://checkout.stripe.com/c/pay/cs_test_...", "checkoutSessionId": "cs_test_..." }
            }
            ```

            What to do per `gate`:

            | gate | code | action | do this |
            |------|------|--------|---------|
            | `payment` | `payment_required` | `complete_checkout` | Hand `url` to the human who owns the account. Poll `poll` until the session is complete, then retry the call. |
            | `budget` | `budget_exceeded` or `spend_cap_exceeded` | `wait` or `raise_spend_cap` | Wait `retry_after` seconds and retry, or ask the owner to raise the cap. Do not retry in a loop. |
            | `claim` | `claim_required` | `claim_account` | This operation is for claimed accounts. Paying claims the account. `poll` is your status URL. |
            | `scope` | `scope_denied` | `request_scope` | Your key lacks the scope. Ask the operator for a key with it. |

            `url`, `poll` and `retry_after` are null when they do not apply. `message` is one sentence that
            says what to do. It is safe to show it to a human.

            ## 8. Set the owner

            Optional. Records a human contact on the customer. Sends nothing.

            ```
            curl -X PUT {base}/api/v1/client/customers/agent_7f3c9a2e/owner \\
              -H 'X-API-Key: ck_test_9b1d...e4' -H 'Content-Type: application/json' \\
              -d '{ "email": "ops@example.com" }'
            ```

            Returns the status body from step 5 with `owner_email` set.

            ## 9. Expiry and claiming

            A provisional account expires at `expires_at`, 14 days after signup by default (the operator can
            change the window). Paying claims the account: the first completed checkout or paid invoice sets
            `status` to `claimed` and `expires_at` to null. Completing a spend mandate setup also claims it.

            When the window passes without a payment, `status` becomes `expired` and the account's API keys
            are revoked. Usage history is kept. Sign up again to get a new provisional account.

            ## Rate limits

            - Per account: the operator's hourly signup cap (10 per hour by default).
            - Per IP: 5 signups per hour by default.

            Both return `429` with a `Retry-After` header in seconds and this body:

            ```json
            { "success": false, "error": { "code": "rate_limited", "message": "Signup rate limit reached (errorId=...)" } }
            ```

            Wait `Retry-After` seconds before trying again.

            ## Reference

            - OpenAPI: {base}/v3/api-docs
            - Manifest: {base}/.well-known/agent.json
            - Index: {base}/llms.txt
            """;
}
