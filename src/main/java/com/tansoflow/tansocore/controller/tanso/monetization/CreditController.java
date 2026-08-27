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
package com.tansoflow.tansocore.controller.tanso.monetization;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.model.credit.CreditFeatureWeightDto;
import com.tansoflow.tansocore.model.credit.CreditGrantDto;
import com.tansoflow.tansocore.model.credit.CreditModelDto;
import com.tansoflow.tansocore.model.credit.CreditPoolDto;
import com.tansoflow.tansocore.model.credit.CreditTransactionDto;
import com.tansoflow.tansocore.model.credit.PlanCreditAllocationDto;
import com.tansoflow.tansocore.model.credit.request.CreateCreditModelRequest;
import com.tansoflow.tansocore.model.credit.request.CreateCreditPoolRequest;
import com.tansoflow.tansocore.model.credit.request.CreditDeductionRequest;
import com.tansoflow.tansocore.model.credit.CreditPriceDto;
import com.tansoflow.tansocore.model.credit.request.CreditGrantRequest;
import com.tansoflow.tansocore.model.credit.request.PublishCreditPricesRequest;
import com.tansoflow.tansocore.model.credit.request.PublishCreditWeightsRequest;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.service.internal.monetization.CreditPriceService;
import com.tansoflow.tansocore.service.internal.monetization.CreditService;
import com.tansoflow.tansocore.service.internal.monetization.CreditWeightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/monetization/credits")
@PreAuthorize("hasRole('TANSO_UI')")
@Tag(name = "Credits", description = "Credit pool management operations")
@ConditionalOnProperty(name = "app.modules.monetization.enabled", havingValue = "true", matchIfMissing = true)
public class CreditController {
    private final CreditService creditService;
    private final CreditWeightService creditWeightService;
    private final CreditPriceService creditPriceService;

    // ─── Credit Model endpoints ───

    @PostMapping("/models")
    @Operation(summary = "Create a credit model", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<CreditModelDto>> createModel(
            @AuthenticationPrincipal UserContext userContext,
            @Valid @RequestBody CreateCreditModelRequest request) {
        CreditModelDto model = creditService.createCreditModel(request, userContext.getAccountId());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<CreditModelDto>builder().data(model).success(true).build());
    }

    @GetMapping("/models")
    @Operation(summary = "List credit models for account", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<List<CreditModelDto>>> listModels(
            @AuthenticationPrincipal UserContext userContext) {
        List<CreditModelDto> models = creditService.getCreditModelsByAccount(userContext.getAccountId());
        return ResponseEntity.ok(
                ApiResponse.<List<CreditModelDto>>builder().data(models).success(true).build());
    }

    @GetMapping("/models/{id}")
    @Operation(summary = "Get credit model by ID", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<CreditModelDto>> getModel(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String id) {
        CreditModelDto model = creditService.getCreditModel(id, userContext.getAccountId());
        return ResponseEntity.ok(
                ApiResponse.<CreditModelDto>builder().data(model).success(true).build());
    }

    @DeleteMapping("/models/{id}")
    @Operation(summary = "Soft-delete a credit model", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<Void>> deleteModel(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String id) {
        creditService.deleteCreditModel(id, userContext.getAccountId());
        return ResponseEntity.ok(
                ApiResponse.<Void>builder().success(true).build());
    }

    // ─── Credit Allocation endpoints ───

    @PostMapping("/models/{id}/plans/{planId}")
    @Operation(summary = "Add credit allocation to plan", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<Void>> addAllocationToPlan(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String id,
            @PathVariable String planId,
            @RequestParam BigDecimal creditAmount,
            @RequestParam(required = false) Integer grantExpiresMonths,
            @RequestParam(required = false) Boolean hardLimit) {
        creditService.addCreditAllocationToPlan(planId, id, creditAmount, grantExpiresMonths, hardLimit, userContext.getAccountId());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<Void>builder().success(true).build());
    }

    @GetMapping("/plans/{planId}/allocations")
    @Operation(summary = "List credit allocations for a plan", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<List<PlanCreditAllocationDto>>> getAllocationsForPlan(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String planId) {
        List<PlanCreditAllocationDto> allocations = creditService.getCreditAllocationsForPlan(planId, userContext.getAccountId());
        return ResponseEntity.ok(
                ApiResponse.<List<PlanCreditAllocationDto>>builder().data(allocations).success(true).build());
    }

    @DeleteMapping("/models/{id}/plans/{planId}")
    @Operation(summary = "Remove credit allocation from plan", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<Void>> removeAllocationFromPlan(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String id,
            @PathVariable String planId) {
        creditService.removeCreditAllocationFromPlan(planId, id, userContext.getAccountId());
        return ResponseEntity.ok(
                ApiResponse.<Void>builder().success(true).build());
    }

    // ─── Pool endpoints ───

    @PostMapping("/pools")
    @Operation(summary = "Create a credit pool", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<CreditPoolDto>> createPool(
            @AuthenticationPrincipal UserContext userContext,
            @Valid @RequestBody CreateCreditPoolRequest request) {
        CreditPoolDto pool = creditService.createCreditPool(request, userContext.getAccountId());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<CreditPoolDto>builder().data(pool).success(true).build());
    }

    @GetMapping("/pools")
    @Operation(summary = "List credit pools", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<List<CreditPoolDto>>> listPools(
            @AuthenticationPrincipal UserContext userContext) {
        List<CreditPoolDto> pools = creditService.getCreditPoolsByAccount(userContext.getAccountId());
        return ResponseEntity.ok(
                ApiResponse.<List<CreditPoolDto>>builder().data(pools).success(true).build());
    }

    @GetMapping("/pools/{poolId}")
    @Operation(summary = "Get a credit pool", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<CreditPoolDto>> getPool(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String poolId) {
        CreditPoolDto pool = creditService.getCreditPool(poolId, userContext.getAccountId());
        return ResponseEntity.ok(
                ApiResponse.<CreditPoolDto>builder().data(pool).success(true).build());
    }

    @GetMapping("/pools/customer/{customerId}")
    @Operation(summary = "List credit pools for a customer", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<List<CreditPoolDto>>> getPoolsByCustomer(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String customerId) {
        List<CreditPoolDto> pools = creditService.getCreditPoolsByCustomer(customerId, userContext.getAccountId());
        return ResponseEntity.ok(
                ApiResponse.<List<CreditPoolDto>>builder().data(pools).success(true).build());
    }

    // ─── Grant endpoints ───

    @PostMapping("/grants")
    @Operation(summary = "Grant credits to a pool", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<CreditGrantDto>> grantCredits(
            @AuthenticationPrincipal UserContext userContext,
            @Valid @RequestBody CreditGrantRequest request) {
        CreditGrantDto grant = creditService.grantCredits(request, userContext.getAccountId());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<CreditGrantDto>builder().data(grant).success(true).build());
    }

    @GetMapping("/pools/{poolId}/grants")
    @Operation(summary = "List grants for a pool", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<List<CreditGrantDto>>> getGrants(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String poolId) {
        List<CreditGrantDto> grants = creditService.getGrantsByPool(poolId, userContext.getAccountId());
        return ResponseEntity.ok(
                ApiResponse.<List<CreditGrantDto>>builder().data(grants).success(true).build());
    }

    // ─── Deduction endpoints ───

    @PostMapping("/deductions")
    @Operation(summary = "Deduct credits from a pool", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<CreditTransactionDto>> deductCredits(
            @AuthenticationPrincipal UserContext userContext,
            @Valid @RequestBody CreditDeductionRequest request) {
        CreditTransactionDto tx = creditService.deductCredits(request, userContext.getAccountId());
        return ResponseEntity.ok(
                ApiResponse.<CreditTransactionDto>builder().data(tx).success(true).build());
    }

    // ─── Reversal ───

    @PostMapping("/transactions/{transactionId}/reverse")
    @Operation(summary = "Reverse a credit transaction", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<CreditTransactionDto>> reverseTransaction(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String transactionId,
            @RequestParam(required = false) String description) {
        CreditTransactionDto tx = creditService.reverseTransaction(transactionId, description, userContext.getAccountId());
        return ResponseEntity.ok(
                ApiResponse.<CreditTransactionDto>builder().data(tx).success(true).build());
    }

    // ─── Pool-subscription linkage ───

    @PostMapping("/pools/{poolId}/subscriptions/{subscriptionId}")
    @Operation(summary = "Link a credit pool to a subscription", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<Void>> linkPoolToSubscription(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String poolId,
            @PathVariable String subscriptionId,
            @RequestParam(defaultValue = "0") int drawPriority,
            @RequestParam(required = false) BigDecimal drawLimit) {
        creditService.linkPoolToSubscription(poolId, subscriptionId, userContext.getAccountId(), drawPriority, drawLimit);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<Void>builder().success(true).build());
    }

    @DeleteMapping("/pools/{poolId}/subscriptions/{subscriptionId}")
    @Operation(summary = "Unlink a credit pool from a subscription", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<Void>> unlinkPoolFromSubscription(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String poolId,
            @PathVariable String subscriptionId) {
        creditService.unlinkPoolFromSubscription(poolId, subscriptionId, userContext.getAccountId());
        return ResponseEntity.ok(
                ApiResponse.<Void>builder().success(true).build());
    }

    // ─── Ledger ───

    @GetMapping("/pools/{poolId}/transactions")
    @Operation(summary = "List transactions for a credit pool", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<List<CreditTransactionDto>>> getTransactions(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String poolId) {
        List<CreditTransactionDto> txs = creditService.getTransactionsByPool(poolId, userContext.getAccountId());
        return ResponseEntity.ok(
                ApiResponse.<List<CreditTransactionDto>>builder().data(txs).success(true).build());
    }

    // ─── Credit Feature Weights (tariff) ───

    @GetMapping("/weights")
    @Operation(summary = "List current and scheduled credit weights", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<List<CreditFeatureWeightDto>>> getWeights(
            @AuthenticationPrincipal UserContext userContext) {
        List<CreditFeatureWeightDto> weights = creditWeightService.getWeights(userContext.getAccountId());
        return ResponseEntity.ok(
                ApiResponse.<List<CreditFeatureWeightDto>>builder().data(weights).success(true).build());
    }

    @GetMapping("/weights/history")
    @Operation(summary = "Tariff history for a (feature, model) pair", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<List<CreditFeatureWeightDto>>> getWeightHistory(
            @AuthenticationPrincipal UserContext userContext,
            @RequestParam String featureId,
            @RequestParam(required = false) String model) {
        List<CreditFeatureWeightDto> history = creditWeightService.getHistory(userContext.getAccountId(), featureId, model);
        return ResponseEntity.ok(
                ApiResponse.<List<CreditFeatureWeightDto>>builder().data(history).success(true).build());
    }

    @PostMapping("/weights/publish")
    @Operation(summary = "Publish a tariff batch — one transaction, one shared effective time",
            security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<List<CreditFeatureWeightDto>>> publishWeights(
            @AuthenticationPrincipal UserContext userContext,
            @Valid @RequestBody PublishCreditWeightsRequest request) {
        List<CreditFeatureWeightDto> published = creditWeightService.publishWeights(
                request, userContext.getAccountId(),
                userContext.getUserId() != null ? UUID.fromString(userContext.getUserId()) : null);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<List<CreditFeatureWeightDto>>builder().data(published).success(true).build());
    }

    @DeleteMapping("/weights/{weightId}")
    @Operation(summary = "Delete a scheduled (not-yet-effective) weight row", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<Void>> deleteScheduledWeight(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String weightId) {
        creditWeightService.deleteScheduledWeight(weightId, userContext.getAccountId());
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).build());
    }

    // ─── Credit Prices (price book) ───

    @GetMapping("/prices")
    @Operation(summary = "List current and scheduled credit prices", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<List<CreditPriceDto>>> getPrices(
            @AuthenticationPrincipal UserContext userContext) {
        List<CreditPriceDto> prices = creditPriceService.getPrices(userContext.getAccountId());
        return ResponseEntity.ok(
                ApiResponse.<List<CreditPriceDto>>builder().data(prices).success(true).build());
    }

    @GetMapping("/prices/history")
    @Operation(summary = "Price history for a denomination", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<List<CreditPriceDto>>> getPriceHistory(
            @AuthenticationPrincipal UserContext userContext,
            @RequestParam String denomination) {
        List<CreditPriceDto> history = creditPriceService.getHistory(userContext.getAccountId(), denomination);
        return ResponseEntity.ok(
                ApiResponse.<List<CreditPriceDto>>builder().data(history).success(true).build());
    }

    @PostMapping("/prices/publish")
    @Operation(summary = "Publish a price batch — one transaction, one shared effective time",
            security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<List<CreditPriceDto>>> publishPrices(
            @AuthenticationPrincipal UserContext userContext,
            @Valid @RequestBody PublishCreditPricesRequest request) {
        List<CreditPriceDto> published = creditPriceService.publishPrices(
                request, userContext.getAccountId(),
                userContext.getUserId() != null ? UUID.fromString(userContext.getUserId()) : null);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<List<CreditPriceDto>>builder().data(published).success(true).build());
    }

    @DeleteMapping("/prices/{priceId}")
    @Operation(summary = "Delete a scheduled (not-yet-effective) price row", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<Void>> deleteScheduledPrice(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String priceId) {
        creditPriceService.deleteScheduledPrice(priceId, userContext.getAccountId());
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).build());
    }

    @GetMapping("/weights/unit-costs")
    @Operation(summary = "Observed average cost per usage unit, keyed \"featureId|model\" (last 30 days)",
            security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<Map<String, BigDecimal>>> getObservedUnitCosts(
            @AuthenticationPrincipal UserContext userContext) {
        Map<String, BigDecimal> costs = creditWeightService.getObservedUnitCosts(
                userContext.getAccountId(), Instant.now().minus(30, ChronoUnit.DAYS));
        return ResponseEntity.ok(
                ApiResponse.<Map<String, BigDecimal>>builder().data(costs).success(true).build());
    }

    @GetMapping("/weights/denominations")
    @Operation(summary = "Credit denomination each feature burns, keyed by featureId (ambiguous features omitted)",
            security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<Map<String, String>>> getFeatureDenominations(
            @AuthenticationPrincipal UserContext userContext) {
        Map<String, String> denominations = creditWeightService.getFeatureDenominations(userContext.getAccountId());
        return ResponseEntity.ok(
                ApiResponse.<Map<String, String>>builder().data(denominations).success(true).build());
    }
}
