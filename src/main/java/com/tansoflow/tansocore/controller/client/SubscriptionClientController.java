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

import com.tansoflow.tansocore.auth.RequiresFullPlatformMode;
import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.model.subscription.request.ClientChangeSubscriptionRequest;
import com.tansoflow.tansocore.model.subscription.request.ClientSubscriptionRequest;
import com.tansoflow.tansocore.model.subscription.response.SubscribedCustomerResponse;
import com.tansoflow.tansocore.model.subscription.type.SubscriptionChangeType;
import com.tansoflow.tansocore.service.internal.monetization.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/client/subscriptions")
@PreAuthorize("hasRole('CLIENT')")
@Tag(name = "Client Subscription",
        description = "Manage customer lifecycles. Supports Flat, Usage, and Hybrid models. " +
                "Hybrid plans combine a base price with usage-based feature rules.")
public class SubscriptionClientController {
    private final SubscriptionService subscriptionService;

    // TODO: Needs to return hosted invoice url in meta data
    @RequiresFullPlatformMode
    @PostMapping()
    @Operation(summary = "Create subscription", description = "Adds a new subscription to a customer with a specific plan", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Successfully created a subscription"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Invalid plan or customer ID", content = @Content)
    })
    public ResponseEntity<ApiResponse<SubscribedCustomerResponse>> createSubscription(@AuthenticationPrincipal UserContext userContext,
                                                          @Valid @RequestBody ClientSubscriptionRequest subscriptionRequest) {
        SubscribedCustomerResponse subscribedCustomerResponse =
                subscriptionService.clientSubscribeCustomer(subscriptionRequest, userContext.getAccountId());

        ApiResponse<SubscribedCustomerResponse> apiResponse = ApiResponse.<SubscribedCustomerResponse>builder()
                .data(subscribedCustomerResponse)
                .success(true)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(apiResponse);
    }

    @RequiresFullPlatformMode
    @PostMapping("/cancellation/{subscriptionId}")
    @Operation(summary = "Cancel subscription", description = "Cancels a customer subscription immediately or at the end of the period", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully cancelled the subscription"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Subscription not found", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> cancelSubscription(@AuthenticationPrincipal UserContext userContext,
                                                          @PathVariable("subscriptionId") String subscriptionId,
                                                          @RequestParam(required = false, defaultValue = "END_OF_PERIOD") String cancelMode) {
        subscriptionService.cancelSubscription(subscriptionId, cancelMode, userContext.getAccountId());

        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder().success(true).build();

        return ResponseEntity.ok(apiResponse);
    }

    @RequiresFullPlatformMode
    @DeleteMapping("/cancellation/{subscriptionId}/scheduled")
    @Operation(summary = "Cancel scheduled cancellation", description = "Cancels a previously scheduled subscription cancellation", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully cancelled the scheduled cancellation"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Subscription not found", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> cancelScheduledSubscriptionCancellation(@AuthenticationPrincipal UserContext userContext,
                                                                                     @PathVariable("subscriptionId") String subscriptionId) {
        subscriptionService.cancelScheduledSubscriptionCancellation(subscriptionId, userContext.getAccountId());
        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder().success(true).build();
        return ResponseEntity.ok(apiResponse);
    }

    @RequiresFullPlatformMode
    @PostMapping("/{subscriptionId}/plan-change")
    @Operation(summary = "Change plan", description = "Changes a customer subscription to a new plan by upgrading or downgrading", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully changed the plan or scheduled the change"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Subscription or plan not found", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> changeSubscription(@AuthenticationPrincipal UserContext userContext,
                                                                @Valid @RequestBody ClientChangeSubscriptionRequest request, @PathVariable("subscriptionId") String subscriptionId) {
        if (request.getChangeType() == SubscriptionChangeType.UPGRADE) {
            subscriptionService.upgradeSubscription(subscriptionId, userContext.getAccountId(), request.getChangeToPlanId(), true);
        }

        if (request.getChangeType() == SubscriptionChangeType.DOWNGRADE) {
            subscriptionService.scheduleDowngradeSubscription(subscriptionId, userContext.getAccountId(), request.getChangeToPlanId());
        }

        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder().success(true).build();

        return ResponseEntity.ok(apiResponse);
    }

    @RequiresFullPlatformMode
    @DeleteMapping("/{subscriptionId}/plan-change/scheduled")
    @Operation(summary = "Cancel scheduled plan change", description = "Cancels all scheduled changes for a subscription", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully cancelled scheduled changes"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Subscription not found", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> cancelScheduledSubscriptionChanges(@AuthenticationPrincipal UserContext userContext,
                                                                                @PathVariable("subscriptionId") String subscriptionId) {
        subscriptionService.cancelScheduledChangesForSubscription(UUID.fromString(subscriptionId), UUID.fromString(userContext.getAccountId()));
        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder().success(true).build();
        return ResponseEntity.ok(apiResponse);
    }

}
