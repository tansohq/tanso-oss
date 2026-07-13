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
package com.tansoflow.tansocore.controller.client;

import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.model.entitlement.api.EntitlementEvaluationRequest;
import com.tansoflow.tansocore.model.entitlement.response.CustomerEntitlementsResponse;
import com.tansoflow.tansocore.model.entitlement.response.EntitlementResponse;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.model.response.PaginatedResponse;
import com.tansoflow.tansocore.service.client.ClientEntitlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/client/entitlements")
@PreAuthorize("hasRole('CLIENT')")
@Tag(name = "Client Entitlement", description = "Entitlement checks for client applications")
public class EntitlementClientController {
    private final ClientEntitlementService clientEntitlementService;

    @GetMapping("/{customerReferenceId}")
    @Operation(summary = "List all customer entitlements", description = "Returns all active entitlements for a customer, grouped by subscription", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved customer entitlements"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Unable to locate customer", content = @Content)})
    public ResponseEntity<ApiResponse<PaginatedResponse<CustomerEntitlementsResponse.SubscriptionEntitlements>>> getCustomerEntitlements(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable("customerReferenceId") String customerId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        CustomerEntitlementsResponse response = clientEntitlementService
                .getCustomerEntitlements(customerId, userContext.getAccountId());

        List<CustomerEntitlementsResponse.SubscriptionEntitlements> allSubs = response.getSubscriptions();
        int total = allSubs != null ? allSubs.size() : 0;
        int fromIndex = Math.min(offset, total);
        int toIndex = Math.min(offset + limit, total);
        List<CustomerEntitlementsResponse.SubscriptionEntitlements> page = allSubs != null
                ? allSubs.subList(fromIndex, toIndex) : List.of();

        PaginatedResponse<CustomerEntitlementsResponse.SubscriptionEntitlements> paginatedResponse =
                PaginatedResponse.<CustomerEntitlementsResponse.SubscriptionEntitlements>builder()
                        .items(page)
                        .pagination(PaginatedResponse.PaginationMeta.builder()
                                .total(total)
                                .limit(limit)
                                .offset(offset)
                                .hasMore(toIndex < total)
                                .build())
                        .build();

        return ResponseEntity.ok(ApiResponse.<PaginatedResponse<CustomerEntitlementsResponse.SubscriptionEntitlements>>builder()
                .data(paginatedResponse).success(true).build());
    }

    @GetMapping("/{customerReferenceId}/{feature-key}")
    @Operation(summary = "Check feature entitlement", description = "Checks if a customer has entitlement for a specific feature by its key", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved entitlement information"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Unable to locate customer", content = @Content)})
    public ResponseEntity<ApiResponse<EntitlementResponse>> getEntitlementByCustomerReferenceIdAndFeatureKey(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable("customerReferenceId") String customerId,
            @PathVariable("feature-key") String parameter,
            @RequestParam(defaultValue = "true") boolean record) {
        EntitlementResponse entitlementResponse = clientEntitlementService.checkEntitlement(customerId, userContext.getAccountId(), parameter, record);

        ApiResponse<EntitlementResponse> apiResponse = ApiResponse.<EntitlementResponse>builder()
                .data(entitlementResponse)
                .success(true)
                .build();

        return ResponseEntity.ok(apiResponse);
    }

    @PostMapping
    @Operation(summary = "Check feature entitlement with usage simulation", description = "Checks if a customer has entitlement for a feature. When usage context is provided, simulates whether the proposed usage would be allowed without recording real usage. The event is recorded with zero usage for audit purposes only. Use POST /api/v1/client/events to record actual usage.", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<EntitlementResponse>> evaluateEntitlement(@AuthenticationPrincipal UserContext userContext,
                                         @Valid @RequestBody EntitlementEvaluationRequest request) {
        log.info("Evaluating entitlement for customer: {} and feature: {}", request.getCustomerReferenceId(), request.getFeatureKey());
        EntitlementResponse response = clientEntitlementService.evaluateEntitlement(userContext.getAccountId(), request);
        return ResponseEntity.ok(ApiResponse.<EntitlementResponse>builder()
                .data(response)
                .success(true)
                .build());
    }

}
