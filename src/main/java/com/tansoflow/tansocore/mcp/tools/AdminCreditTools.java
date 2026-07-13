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
import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.model.credit.request.CreateCreditModelRequest;
import com.tansoflow.tansocore.model.credit.request.CreateCreditPoolRequest;
import com.tansoflow.tansocore.model.credit.request.CreditDeductionRequest;
import com.tansoflow.tansocore.model.credit.request.CreditGrantRequest;
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.service.internal.monetization.CreditService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mcp.enabled", havingValue = "true")
public class AdminCreditTools {

    private final CreditService creditService;
    private final ObjectMapper objectMapper;

    // ── Credit Model Operations ──────────────────────────────────────────

    @Tool(description = "List all credit models in the account. "
            + "Credit models define a denomination (e.g. 'api_credits'), rollover policy, and hard limit behavior.")
    public String adminListCreditModels() {
        try {
            var models = creditService.getCreditModelsByAccount(getAccountId());
            return objectMapper.writeValueAsString(models);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize credit models\"}";
        }
    }

    @Tool(description = "Get a single credit model by ID.")
    public String adminGetCreditModel(
            @ToolParam(description = "The credit model ID") String creditModelId) {
        try {
            var model = creditService.getCreditModel(creditModelId, getAccountId());
            return objectMapper.writeValueAsString(model);
        } catch (ResourceNotFoundException e) {
            return "{\"error\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize credit model\"}";
        }
    }

    @Tool(description = "CREATES a new credit model. "
            + "SIDE EFFECT: Permanently creates a credit model record. "
            + "A credit model defines the denomination and behavior for credit pools.")
    public String adminCreateCreditModel(
            @ToolParam(description = "Display name, e.g. 'API Credits'") String name,
            @ToolParam(description = "Denomination key, e.g. 'api_credits'") String denomination,
            @ToolParam(description = "Optional description", required = false) String description,
            @ToolParam(description = "Whether pools enforce hard limits (default true)", required = false) Boolean hardLimit,
            @ToolParam(description = "Rollover policy: NONE, FULL, or CAPPED (default NONE)", required = false) String rolloverPolicy,
            @ToolParam(description = "Maximum credits to roll over (only used when rolloverPolicy is CAPPED)", required = false) String rolloverCap) {
        try {
            var request = new CreateCreditModelRequest();
            request.setName(name);
            request.setDenomination(denomination);
            if (description != null) request.setDescription(description);
            if (hardLimit != null) request.setHardLimit(hardLimit);
            if (rolloverPolicy != null) request.setRolloverPolicy(rolloverPolicy);
            if (rolloverCap != null && !rolloverCap.isBlank()) {
                request.setRolloverCap(new BigDecimal(rolloverCap));
            }

            var result = creditService.createCreditModel(request, getAccountId());
            return objectMapper.writeValueAsString(result);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return "{\"error\": \"invalid_request\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize credit model\"}";
        }
    }

    @Tool(description = "DELETES a credit model (soft delete). "
            + "SIDE EFFECT: Marks the credit model as deleted. Existing pools using this model are not affected.")
    public String adminDeleteCreditModel(
            @ToolParam(description = "The credit model ID to delete") String creditModelId,
            @ToolParam(description = "Set to true to confirm deletion.") boolean confirmAction) {
        if (!confirmAction) {
            return "{\"error\": \"confirmation_required\", \"message\": \"Set confirmAction to true to delete this credit model.\"}";
        }
        try {
            creditService.deleteCreditModel(creditModelId, getAccountId());
            return "{\"success\": true, \"message\": \"Credit model deleted\"}";
        } catch (ResourceNotFoundException e) {
            return "{\"error\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}";
        }
    }

    // ── Credit Allocation (Model ↔ Plan) Operations ─────────────────────

    @Tool(description = "Link a credit model to a plan with a specific credit allocation. "
            + "SIDE EFFECT: When a subscription is created for this plan, a credit pool with this allocation is automatically provisioned. "
            + "Existing subscriptions are not retroactively affected.")
    public String adminAddCreditAllocationToPlan(
            @ToolParam(description = "The credit model ID") String creditModelId,
            @ToolParam(description = "The plan ID to link to") String planId,
            @ToolParam(description = "Amount of credits to allocate per subscription cycle") String creditAmount,
            @ToolParam(description = "Number of months before granted credits expire (null = no expiry)", required = false) String grantExpiresMonths,
            @ToolParam(description = "Whether this allocation enforces a hard spending limit", required = false) Boolean hardLimit) {
        try {
            BigDecimal parsedAmount = new BigDecimal(creditAmount);
            Integer parsedExpiry = null;
            if (grantExpiresMonths != null && !grantExpiresMonths.isBlank()) {
                parsedExpiry = Integer.parseInt(grantExpiresMonths);
            }

            creditService.addCreditAllocationToPlan(planId, creditModelId, parsedAmount, parsedExpiry, hardLimit, getAccountId());
            return "{\"success\": true, \"message\": \"Credit allocation added to plan\"}";
        } catch (NumberFormatException e) {
            return "{\"error\": \"invalid_request\", \"message\": \"creditAmount and grantExpiresMonths must be valid numbers\"}";
        } catch (ResourceNotFoundException e) {
            return "{\"error\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (Exception e) {
            return "{\"error\": \"failed\", \"message\": \"" + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "List all credit allocations configured for a plan. "
            + "Shows which credit models are linked and how many credits are allocated per cycle.")
    public String adminListCreditAllocationsForPlan(
            @ToolParam(description = "The plan ID") String planId) {
        try {
            var allocations = creditService.getCreditAllocationsForPlan(planId, getAccountId());
            return objectMapper.writeValueAsString(allocations);
        } catch (ResourceNotFoundException e) {
            return "{\"error\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize allocations\"}";
        }
    }

    @Tool(description = "REMOVES a credit allocation from a plan. "
            + "SIDE EFFECT: Future subscriptions will no longer receive this credit allocation. "
            + "Existing pools are not affected.")
    public String adminRemoveCreditAllocationFromPlan(
            @ToolParam(description = "The credit model ID") String creditModelId,
            @ToolParam(description = "The plan ID to unlink from") String planId,
            @ToolParam(description = "Set to true to confirm removal.") boolean confirmAction) {
        if (!confirmAction) {
            return "{\"error\": \"confirmation_required\", \"message\": \"Set confirmAction to true to remove this allocation.\"}";
        }
        try {
            creditService.removeCreditAllocationFromPlan(planId, creditModelId, getAccountId());
            return "{\"success\": true, \"message\": \"Credit allocation removed from plan\"}";
        } catch (ResourceNotFoundException e) {
            return "{\"error\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}";
        }
    }

    // ── Credit Pool Operations ──────────────────────────────────────────

    @Tool(description = "List all credit pools in the account.")
    public String adminListCreditPools() {
        try {
            var pools = creditService.getCreditPoolsByAccount(getAccountId());
            return objectMapper.writeValueAsString(pools);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize credit pools\"}";
        }
    }

    @Tool(description = "Get a single credit pool by ID. "
            + "Returns balance, total granted/consumed/expired/reversed, status, and metadata.")
    public String adminGetCreditPool(
            @ToolParam(description = "The credit pool ID") String poolId) {
        try {
            var pool = creditService.getCreditPool(poolId, getAccountId());
            return objectMapper.writeValueAsString(pool);
        } catch (ResourceNotFoundException e) {
            return "{\"error\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize credit pool\"}";
        }
    }

    @Tool(description = "List all credit pools for a specific customer.")
    public String adminGetCreditPoolsByCustomer(
            @ToolParam(description = "The customer ID (Tanso internal UUID)") String customerId) {
        try {
            var pools = creditService.getCreditPoolsByCustomer(customerId, getAccountId());
            return objectMapper.writeValueAsString(pools);
        } catch (ResourceNotFoundException e) {
            return "{\"error\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize credit pools\"}";
        }
    }

    @Tool(description = "CREATES a new credit pool. "
            + "SIDE EFFECT: Permanently creates a credit pool. Pools hold a balance of credits for a customer.")
    public String adminCreateCreditPool(
            @ToolParam(description = "Display name, e.g. 'Pro Plan Credits'") String name,
            @ToolParam(description = "Denomination, e.g. 'CREDITS'") String denomination,
            @ToolParam(description = "ISO currency code, e.g. 'USD'", required = false) String currency,
            @ToolParam(description = "Customer ID to associate with this pool", required = false) String customerId,
            @ToolParam(description = "Whether this pool enforces hard spending limits (default false)", required = false) Boolean hardLimit,
            @ToolParam(description = "Rollover policy: NONE, FULL, or CAPPED (default NONE)", required = false) String rolloverPolicy,
            @ToolParam(description = "Maximum credits to roll over when policy is CAPPED", required = false) String rolloverCap) {
        try {
            var request = new CreateCreditPoolRequest();
            request.setName(name);
            request.setDenomination(denomination);
            if (currency != null) request.setCurrency(currency);
            if (customerId != null) request.setCustomerId(customerId);
            if (hardLimit != null) request.setHardLimit(hardLimit);
            if (rolloverPolicy != null) request.setRolloverPolicy(rolloverPolicy);
            if (rolloverCap != null && !rolloverCap.isBlank()) {
                request.setRolloverCap(new BigDecimal(rolloverCap));
            }

            var result = creditService.createCreditPool(request, getAccountId());
            return objectMapper.writeValueAsString(result);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return "{\"error\": \"invalid_request\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize credit pool\"}";
        }
    }

    // ── Grant & Deduction Operations ────────────────────────────────────

    @Tool(description = "GRANTS credits to a pool, increasing its balance. "
            + "SIDE EFFECT: Adds credits and creates a transaction record. "
            + "Grant types: PLAN_INCLUDED, PURCHASED, PROMOTIONAL, REFUND, SYSTEM, ROLLOVER, MANUAL.")
    public String adminGrantCredits(
            @ToolParam(description = "The credit pool ID to grant credits to") String creditPoolId,
            @ToolParam(description = "Amount of credits to grant (must be positive)") String amount,
            @ToolParam(description = "Grant type: PLAN_INCLUDED, PURCHASED, PROMOTIONAL, REFUND, SYSTEM, ROLLOVER, or MANUAL") String grantType,
            @ToolParam(description = "Associated subscription ID", required = false) String subscriptionId,
            @ToolParam(description = "When this grant expires in ISO-8601 (null = no expiry)", required = false) String expiresAt,
            @ToolParam(description = "Description of the grant", required = false) String description,
            @ToolParam(description = "Idempotency key to prevent duplicate grants", required = false) String idempotencyKey) {
        try {
            var request = new CreditGrantRequest();
            request.setCreditPoolId(creditPoolId);
            request.setAmount(new BigDecimal(amount));
            request.setGrantType(grantType);
            if (subscriptionId != null) request.setSubscriptionId(subscriptionId);
            if (expiresAt != null && !expiresAt.isBlank()) {
                request.setExpiresAt(Instant.parse(expiresAt));
            }
            if (description != null) request.setDescription(description);
            if (idempotencyKey != null) request.setIdempotencyKey(idempotencyKey);

            var result = creditService.grantCredits(request, getAccountId());
            return objectMapper.writeValueAsString(result);
        } catch (NumberFormatException e) {
            return "{\"error\": \"invalid_request\", \"message\": \"amount must be a valid number\"}";
        } catch (ResourceNotFoundException e) {
            return "{\"error\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize grant\"}";
        } catch (Exception e) {
            return "{\"error\": \"grant_failed\", \"message\": \"" + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "List all grants for a credit pool. Shows amount, remaining balance, type, and expiry.")
    public String adminGetPoolGrants(
            @ToolParam(description = "The credit pool ID") String poolId) {
        try {
            var grants = creditService.getGrantsByPool(poolId, getAccountId());
            return objectMapper.writeValueAsString(grants);
        } catch (ResourceNotFoundException e) {
            return "{\"error\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize grants\"}";
        }
    }

    @Tool(description = "DEDUCTS credits from a pool, decreasing its balance. "
            + "SIDE EFFECT: Removes credits and creates a transaction record.")
    public String adminDeductCredits(
            @ToolParam(description = "The credit pool ID to deduct from") String creditPoolId,
            @ToolParam(description = "Amount of credits to deduct (must be positive)") String amount,
            @ToolParam(description = "Description of the deduction", required = false) String description,
            @ToolParam(description = "Idempotency key to prevent duplicate deductions", required = false) String idempotencyKey,
            @ToolParam(description = "Set to true to confirm this deduction.") boolean confirmAction) {
        if (!confirmAction) {
            return "{\"error\": \"confirmation_required\", \"message\": \"Set confirmAction to true to deduct credits.\"}";
        }
        try {
            var request = new CreditDeductionRequest();
            request.setCreditPoolId(creditPoolId);
            request.setAmount(new BigDecimal(amount));
            if (description != null) request.setDescription(description);
            if (idempotencyKey != null) request.setIdempotencyKey(idempotencyKey);

            var result = creditService.deductCredits(request, getAccountId());
            return objectMapper.writeValueAsString(result);
        } catch (NumberFormatException e) {
            return "{\"error\": \"invalid_request\", \"message\": \"amount must be a valid number\"}";
        } catch (ResourceNotFoundException e) {
            return "{\"error\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize deduction\"}";
        } catch (Exception e) {
            return "{\"error\": \"deduction_failed\", \"message\": \"" + e.getMessage() + "\"}";
        }
    }

    // ── Transaction & Ledger Operations ─────────────────────────────────

    @Tool(description = "List all transactions (grants, deductions, expirations, reversals) for a credit pool. "
            + "Shows balance before/after each transaction.")
    public String adminGetPoolTransactions(
            @ToolParam(description = "The credit pool ID") String poolId) {
        try {
            var transactions = creditService.getTransactionsByPool(poolId, getAccountId());
            return objectMapper.writeValueAsString(transactions);
        } catch (ResourceNotFoundException e) {
            return "{\"error\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize transactions\"}";
        }
    }

    @Tool(description = "REVERSES a credit transaction. "
            + "SIDE EFFECT: Creates a reversal transaction that undoes the original. "
            + "Only deduction transactions can be reversed.")
    public String adminReverseTransaction(
            @ToolParam(description = "The transaction ID to reverse") String transactionId,
            @ToolParam(description = "Reason for the reversal", required = false) String description,
            @ToolParam(description = "Set to true to confirm this reversal.") boolean confirmAction) {
        if (!confirmAction) {
            return "{\"error\": \"confirmation_required\", \"message\": \"Set confirmAction to true to reverse this transaction.\"}";
        }
        try {
            var result = creditService.reverseTransaction(transactionId, description, getAccountId());
            return objectMapper.writeValueAsString(result);
        } catch (ResourceNotFoundException e) {
            return "{\"error\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (IllegalStateException e) {
            return "{\"error\": \"invalid_request\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize reversal\"}";
        }
    }

    // ── Pool ↔ Subscription Linkage ─────────────────────────────────────

    @Tool(description = "Links a credit pool to a subscription so credits can be drawn during billing. "
            + "SIDE EFFECT: Creates a linkage record. Credits from this pool will be applied to the subscription's invoices.")
    public String adminLinkPoolToSubscription(
            @ToolParam(description = "The credit pool ID") String poolId,
            @ToolParam(description = "The subscription ID to link to") String subscriptionId,
            @ToolParam(description = "Draw priority (lower = drawn first, default 0)", required = false) String drawPriority,
            @ToolParam(description = "Maximum credits to draw per cycle (null = unlimited)", required = false) String drawLimit) {
        try {
            int priority = 0;
            if (drawPriority != null && !drawPriority.isBlank()) {
                priority = Integer.parseInt(drawPriority);
            }
            BigDecimal limit = null;
            if (drawLimit != null && !drawLimit.isBlank()) {
                limit = new BigDecimal(drawLimit);
            }

            creditService.linkPoolToSubscription(poolId, subscriptionId, getAccountId(), priority, limit);
            return "{\"success\": true, \"message\": \"Pool linked to subscription\"}";
        } catch (NumberFormatException e) {
            return "{\"error\": \"invalid_request\", \"message\": \"drawPriority must be an integer, drawLimit must be a number\"}";
        } catch (ResourceNotFoundException e) {
            return "{\"error\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (Exception e) {
            return "{\"error\": \"link_failed\", \"message\": \"" + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "UNLINKS a credit pool from a subscription. "
            + "SIDE EFFECT: Removes the linkage. Credits from this pool will no longer be applied to the subscription's invoices.")
    public String adminUnlinkPoolFromSubscription(
            @ToolParam(description = "The credit pool ID") String poolId,
            @ToolParam(description = "The subscription ID to unlink from") String subscriptionId,
            @ToolParam(description = "Set to true to confirm unlinking.") boolean confirmAction) {
        if (!confirmAction) {
            return "{\"error\": \"confirmation_required\", \"message\": \"Set confirmAction to true to unlink this pool.\"}";
        }
        try {
            creditService.unlinkPoolFromSubscription(poolId, subscriptionId, getAccountId());
            return "{\"success\": true, \"message\": \"Pool unlinked from subscription\"}";
        } catch (ResourceNotFoundException e) {
            return "{\"error\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}";
        }
    }

    private String getAccountId() {
        UserContext ctx = (UserContext) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return ctx.getAccountId();
    }
}
