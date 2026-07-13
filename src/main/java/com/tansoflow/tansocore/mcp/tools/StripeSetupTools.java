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
package com.tansoflow.tansocore.mcp.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.StripeException;
import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.AccountSetting;
import com.tansoflow.tansocore.integration.stripe.StripeImportService;
import com.tansoflow.tansocore.integration.stripe.StripeService;
import com.tansoflow.tansocore.model.api.external.StripeMode;
import com.tansoflow.tansocore.model.data.stripe.request.StripeDiscoverRequest;
import com.tansoflow.tansocore.model.data.stripe.response.StripeApiKeysResponse;
import com.tansoflow.tansocore.model.data.stripe.response.StripeDiscoveryResponse;
import com.tansoflow.tansocore.model.data.stripe.response.StripeImportStatusResponse;
import com.tansoflow.tansocore.model.event.service.StripeModeChangedEvent;
import com.tansoflow.tansocore.repository.AccountSettingRepository;
import com.tansoflow.tansocore.service.internal.account.AccountService;
import com.tansoflow.tansocore.service.internal.audit.AuditHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mcp.enabled", havingValue = "true")
public class StripeSetupTools {

    private final StripeService stripeService;
    private final StripeImportService stripeImportService;
    private final AccountService accountService;
    private final AccountSettingRepository accountSettingRepository;
    private final AuditHelper auditHelper;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    // --- Setup Tools ---

    @Tool(description = "Returns the current Stripe integration status for the account. "
            + "Shows whether an API key is registered, whether a webhook exists, the current Stripe mode, "
            + "and currency. Call this first to determine which setup steps remain.")
    public String getStripeIntegrationStatus() {
        try {
            String accountId = getAccountId();
            Account account = accountService.retrieveAccount(accountId);
            StripeApiKeysResponse keys = stripeService.getStripeKeys(account);
            AccountSetting setting = accountSettingRepository.findAccountSettingById(
                    UUID.fromString(accountId));

            Map<String, Object> status = new LinkedHashMap<>();
            status.put("hasApiKey", keys.getStripeApiKey() != null && !keys.getStripeApiKey().isBlank());
            status.put("maskedApiKey", keys.getStripeApiKey());
            status.put("hasWebhook", keys.getWebhookSecret() != null && !keys.getWebhookSecret().isBlank());
            status.put("maskedWebhookSecret", keys.getWebhookSecret());
            status.put("stripeMode", setting.getStripeMode().name());
            status.put("stripeEnabled", setting.isStripeEnabled());
            status.put("currency", setting.getCurrency());

            boolean hasApiKey = keys.getStripeApiKey() != null && !keys.getStripeApiKey().isBlank();
            boolean hasWebhook = keys.getWebhookSecret() != null && !keys.getWebhookSecret().isBlank();
            status.put("setupComplete", hasApiKey && hasWebhook
                    && setting.getStripeMode() == StripeMode.STRIPE_DRIVEN);

            return objectMapper.writeValueAsString(status);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize integration status\"}";
        }
    }

    @Tool(description = "REGISTERS a Stripe API key for the account. "
            + "SIDE EFFECT: Stores the secret key. If a key already exists, deletes the old one first. "
            + "The key must start with 'sk_test_' or 'sk_live_'. "
            + "You MUST set confirmAction to true to execute.")
    public String registerStripeApiKey(
            @ToolParam(description = "The Stripe secret API key (starts with sk_test_ or sk_live_)") String stripeApiKey,
            @ToolParam(description = "Must be true to execute this action") boolean confirmAction) {
        if (!confirmAction) {
            return "{\"error\": \"confirmation_required\", \"message\": \"Set confirmAction to true to execute this action\"}";
        }
        if (stripeApiKey == null || (!stripeApiKey.startsWith("sk_test_") && !stripeApiKey.startsWith("sk_live_"))) {
            return "{\"error\": \"invalid_request\", \"message\": \"Stripe API key must start with 'sk_test_' or 'sk_live_'\"}";
        }
        try {
            String accountId = getAccountId();
            Account account = accountService.retrieveAccount(accountId);

            // Delete existing keys if present
            StripeApiKeysResponse existingKeys = stripeService.getStripeKeys(account);
            if (existingKeys.getStripeApiKey() != null && !existingKeys.getStripeApiKey().isBlank()) {
                stripeService.deleteStripeKeys(account);
            }

            stripeService.registerStripeApiKey(stripeApiKey, account);
            return "{\"success\": true, \"message\": \"Stripe API key registered successfully\"}";
        } catch (Exception e) {
            return "{\"error\": \"stripe_api_error\", \"message\": \"" + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "SETS UP the full Stripe integration: creates a webhook endpoint and switches mode to STRIPE_DRIVEN. "
            + "SIDE EFFECT: Registers a Stripe webhook and changes the account's billing mode. "
            + "Pre-check: An API key must already be registered. "
            + "Idempotent: skips webhook if already registered, skips mode switch if already STRIPE_DRIVEN. "
            + "The webhook creation implicitly validates the API key (Stripe API call will fail if key is bad). "
            + "You MUST set confirmAction to true to execute.")
    public String setupStripeIntegration(
            @ToolParam(description = "Must be true to execute this action") boolean confirmAction) {
        if (!confirmAction) {
            return "{\"error\": \"confirmation_required\", \"message\": \"Set confirmAction to true to execute this action\"}";
        }
        try {
            String accountId = getAccountId();
            Account account = accountService.retrieveAccount(accountId);
            StripeApiKeysResponse keys = stripeService.getStripeKeys(account);

            if (keys.getStripeApiKey() == null || keys.getStripeApiKey().isBlank()) {
                return "{\"error\": \"precondition_failed\", \"message\": \"No Stripe API key registered. Call registerStripeApiKey first.\"}";
            }

            boolean webhookCreated = false;
            boolean modeChanged = false;

            // Create webhook if not already registered
            if (keys.getWebhookSecret() == null || keys.getWebhookSecret().isBlank()) {
                stripeService.createNewWebhookEndpoint(account);
                webhookCreated = true;
            }

            // Switch mode to STRIPE_DRIVEN if not already
            AccountSetting setting = accountSettingRepository.findAccountSettingById(
                    UUID.fromString(accountId));
            if (setting.getStripeMode() != StripeMode.STRIPE_DRIVEN) {
                StripeMode oldMode = setting.getStripeMode();
                setting.setStripeMode(StripeMode.STRIPE_DRIVEN);
                accountSettingRepository.save(setting);

                UUID actorUserId = getUserId();
                UUID actorAccountId = UUID.fromString(accountId);
                auditHelper.audit("STRIPE_MODE_CHANGED", actorUserId, actorAccountId,
                        "AccountSetting", actorAccountId.toString(),
                        oldMode + " -> " + StripeMode.STRIPE_DRIVEN);
                eventPublisher.publishEvent(new StripeModeChangedEvent(
                        actorAccountId, oldMode, StripeMode.STRIPE_DRIVEN));
                modeChanged = true;
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("webhookCreated", webhookCreated);
            result.put("modeChanged", modeChanged);
            result.put("stripeMode", StripeMode.STRIPE_DRIVEN.name());
            return objectMapper.writeValueAsString(result);
        } catch (StripeException e) {
            return "{\"error\": \"stripe_api_error\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize response\"}";
        }
    }

    // --- Import Tools ---

    @Tool(description = "Discovers existing data in the connected Stripe account. "
            + "Lists products, customers, and subscriptions from Stripe and shows which items "
            + "are already mapped to Tanso entities. Use this to preview what will be imported. "
            + "Pre-check: A Stripe API key must be registered.")
    public String discoverStripeData() {
        try {
            String accountId = getAccountId();
            Account account = accountService.retrieveAccount(accountId);
            StripeApiKeysResponse keys = stripeService.getStripeKeys(account);

            if (keys.getStripeApiKey() == null || keys.getStripeApiKey().isBlank()) {
                return "{\"error\": \"precondition_failed\", \"message\": \"No Stripe API key registered. Call registerStripeApiKey first.\"}";
            }

            StripeDiscoverRequest request = new StripeDiscoverRequest();
            request.setIncludeProducts(true);
            request.setIncludeCustomers(true);
            request.setIncludeSubscriptions(true);

            StripeDiscoveryResponse response = stripeImportService.discover(
                    UUID.fromString(accountId), request);
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize discovery response\"}";
        } catch (RuntimeException e) {
            return "{\"error\": \"stripe_api_error\", \"message\": \"" + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "IMPORTS Stripe data by auto-creating Tanso Plans, Features, Customers, and Subscriptions "
            + "from the connected Stripe account. Skips items already mapped. "
            + "SIDE EFFECT: Creates multiple entities in Tanso. "
            + "Pre-check: A Stripe API key must be registered. Stripe mode should be STRIPE_DRIVEN. "
            + "You MUST set confirmAction to true to execute.")
    public String importStripeData(
            @ToolParam(description = "Must be true to execute this action") boolean confirmAction) {
        if (!confirmAction) {
            return "{\"error\": \"confirmation_required\", \"message\": \"Set confirmAction to true to execute this action\"}";
        }
        try {
            String accountId = getAccountId();
            Account account = accountService.retrieveAccount(accountId);
            StripeApiKeysResponse keys = stripeService.getStripeKeys(account);

            if (keys.getStripeApiKey() == null || keys.getStripeApiKey().isBlank()) {
                return "{\"error\": \"precondition_failed\", \"message\": \"No Stripe API key registered. Call registerStripeApiKey first.\"}";
            }

            // Warn if not in STRIPE_DRIVEN mode
            AccountSetting setting = accountSettingRepository.findAccountSettingById(
                    UUID.fromString(accountId));
            Map<String, Object> result = new LinkedHashMap<>();
            if (setting.getStripeMode() != StripeMode.STRIPE_DRIVEN) {
                result.put("warning", "Stripe mode is " + setting.getStripeMode().name()
                        + ", not STRIPE_DRIVEN. Import will proceed but setup may be incomplete.");
            }

            StripeImportStatusResponse response = stripeImportService.startAutoCreateImport(
                    UUID.fromString(accountId));
            result.put("jobId", response.getJobId());
            result.put("status", response.getStatus());
            result.put("totalItems", response.getTotalItems());
            result.put("processedItems", response.getProcessedItems());
            result.put("failedItems", response.getFailedItems());
            if (response.getErrorDetails() != null) {
                result.put("errorDetails", response.getErrorDetails());
            }
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize import response\"}";
        } catch (Exception e) {
            return "{\"error\": \"stripe_api_error\", \"message\": \"" + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "Checks the status of a Stripe import job by its ID. "
            + "Returns progress details including total, processed, and failed item counts.")
    public String getStripeImportStatus(
            @ToolParam(description = "The import job ID (UUID format)") String jobId) {
        UUID jobUuid;
        try {
            jobUuid = UUID.fromString(jobId);
        } catch (IllegalArgumentException e) {
            return "{\"error\": \"invalid_request\", \"message\": \"Invalid job ID format. Must be a valid UUID.\"}";
        }
        try {
            String accountId = getAccountId();
            StripeImportStatusResponse response = stripeImportService.getImportStatus(
                    UUID.fromString(accountId), jobUuid);
            return objectMapper.writeValueAsString(response);
        } catch (IllegalArgumentException e) {
            return "{\"error\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize import status\"}";
        }
    }

    private String getAccountId() {
        UserContext ctx = (UserContext) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return ctx.getAccountId();
    }

    private UUID getUserId() {
        UserContext ctx = (UserContext) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return ctx.getUserId() != null ? UUID.fromString(ctx.getUserId()) : null;
    }
}
