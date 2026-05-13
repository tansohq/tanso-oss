package com.tansoflow.tansocore.controller.tanso.data.stripe;

import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.integration.stripe.StripeImportService;
import com.tansoflow.tansocore.integration.stripe.StripeObserveSyncService;
import com.tansoflow.tansocore.model.data.stripe.request.StripeDiscoverRequest;
import com.tansoflow.tansocore.model.data.stripe.request.StripeImportStartRequest;
import com.tansoflow.tansocore.model.data.stripe.request.StripeMapProductRequest;
import com.tansoflow.tansocore.model.data.stripe.response.StripeDiscoveryResponse;
import com.tansoflow.tansocore.model.data.stripe.response.StripeImportStatusResponse;
import com.tansoflow.tansocore.model.data.stripe.response.StripeObserveSyncResponse;
import com.tansoflow.tansocore.model.response.ApiResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/data/stripe/import")
@PreAuthorize("hasRole('TANSO_UI')")
@Tag(name = "Stripe Import", description = "Operations for importing existing Stripe data into Tanso")
public class StripeImportController {
    private final StripeImportService stripeImportService;
    private final StripeObserveSyncService stripeObserveSyncService;

    @PostMapping("/discover")
    @Operation(summary = "Discover Stripe objects", description = "Lists products, customers, and subscriptions from the connected Stripe account", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<StripeDiscoveryResponse>> discover(
            @AuthenticationPrincipal UserContext userContext,
            @Valid @RequestBody StripeDiscoverRequest request) {
        UUID accountId = UUID.fromString(userContext.getAccountId());
        StripeDiscoveryResponse response = stripeImportService.discover(accountId, request);
        return ResponseEntity.ok(ApiResponse.<StripeDiscoveryResponse>builder()
                .success(true)
                .data(response)
                .build());
    }

    @PostMapping("/start")
    @Operation(summary = "Start import", description = "Kicks off a bulk import with user-defined product and customer mappings", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<StripeImportStatusResponse>> startImport(
            @AuthenticationPrincipal UserContext userContext,
            @Valid @RequestBody StripeImportStartRequest request) {
        UUID accountId = UUID.fromString(userContext.getAccountId());
        StripeImportStatusResponse response = stripeImportService.startImport(accountId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<StripeImportStatusResponse>builder()
                .success(true)
                .data(response)
                .build());
    }

    @GetMapping("/status/{jobId}")
    @Operation(summary = "Get import status", description = "Returns the progress of an import job", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<StripeImportStatusResponse>> getImportStatus(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable UUID jobId) {
        UUID accountId = UUID.fromString(userContext.getAccountId());
        StripeImportStatusResponse response = stripeImportService.getImportStatus(accountId, jobId);
        return ResponseEntity.ok(ApiResponse.<StripeImportStatusResponse>builder()
                .success(true)
                .data(response)
                .build());
    }

    @PostMapping("/start-auto-create")
    @Operation(summary = "Auto-create import",
        description = "Auto-creates Plans, Features, and Customers from Stripe, then imports subscriptions",
        security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<StripeImportStatusResponse>> startAutoCreateImport(
            @AuthenticationPrincipal UserContext userContext) {
        UUID accountId = UUID.fromString(userContext.getAccountId());
        StripeImportStatusResponse response = stripeImportService.startAutoCreateImport(accountId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<StripeImportStatusResponse>builder()
                .success(true)
                .data(response)
                .build());
    }

    @PostMapping("/observe-sync")
    @Operation(summary = "Observe mode sync",
        description = "Lightweight sync that pulls customers, products/prices, and subscriptions from Stripe for cost-tracking analytics",
        security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<StripeObserveSyncResponse>> observeSync(
            @AuthenticationPrincipal UserContext userContext) {
        UUID accountId = UUID.fromString(userContext.getAccountId());
        StripeObserveSyncResponse response = stripeObserveSyncService.sync(accountId);
        return ResponseEntity.ok(ApiResponse.<StripeObserveSyncResponse>builder()
                .success(true)
                .data(response)
                .build());
    }

    @PostMapping("/map-product")
    @Operation(summary = "Map Stripe product to Tanso plan", description = "Creates an ad-hoc mapping between a Stripe product and a Tanso plan", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<Void>> mapProduct(
            @AuthenticationPrincipal UserContext userContext,
            @Valid @RequestBody StripeMapProductRequest request) {
        UUID accountId = UUID.fromString(userContext.getAccountId());
        stripeImportService.mapProduct(accountId, request);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .build());
    }
}
