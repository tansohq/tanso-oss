package com.tansoflow.tansocore.controller.tanso.monetization;

import com.tansoflow.tansocore.auth.RequiresFullPlatformMode;
import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.model.credit.CreditGrantDto;
import com.tansoflow.tansocore.model.credit.CreditModelDto;
import com.tansoflow.tansocore.model.credit.CreditPoolDto;
import com.tansoflow.tansocore.model.credit.CreditTransactionDto;
import com.tansoflow.tansocore.model.credit.PlanCreditAllocationDto;
import com.tansoflow.tansocore.model.credit.request.CreateCreditModelRequest;
import com.tansoflow.tansocore.model.credit.request.CreateCreditPoolRequest;
import com.tansoflow.tansocore.model.credit.request.CreditDeductionRequest;
import com.tansoflow.tansocore.model.credit.request.CreditGrantRequest;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.service.internal.monetization.CreditService;
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
import java.util.List;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/monetization/credits")
@PreAuthorize("hasRole('TANSO_UI')")
@Tag(name = "Credits", description = "Credit pool management operations")
public class CreditController {
    private final CreditService creditService;

    // ─── Credit Model endpoints ───

    @RequiresFullPlatformMode
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

    @RequiresFullPlatformMode
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

    @RequiresFullPlatformMode
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

    @RequiresFullPlatformMode
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

    @RequiresFullPlatformMode
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

    @RequiresFullPlatformMode
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

    @RequiresFullPlatformMode
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

    @RequiresFullPlatformMode
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

    @RequiresFullPlatformMode
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

    @RequiresFullPlatformMode
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
}
