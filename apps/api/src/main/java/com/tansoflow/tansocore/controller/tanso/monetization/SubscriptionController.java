package com.tansoflow.tansocore.controller.tanso.monetization;

import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.model.subscription.SubscriptionDto;
import com.tansoflow.tansocore.model.subscription.SubscriptionScheduledChangeDto;
import com.tansoflow.tansocore.model.subscription.request.SubscriptionRequest;
import com.tansoflow.tansocore.model.subscription.response.SubscribedCustomerResponse;
import com.tansoflow.tansocore.service.internal.audit.AuditHelper;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/monetization/subscriptions")
@PreAuthorize("hasRole('TANSO_UI')")
@Tag(name = "Subscription", description = "Subscription management operations")
public class SubscriptionController {
    private final SubscriptionService subscriptionService;
    private final AuditHelper auditHelper;

    @GetMapping("/customer/{customerUUID}/")
    @Operation(summary = "List customer subscriptions", description = "Retrieves all subscriptions for a specific customer", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved subscriptions"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Customer not found", content = @Content)})
    public ResponseEntity<ApiResponse<List<SubscriptionDto>>> getSubscriptionsByCustomer(@AuthenticationPrincipal UserContext userContext, @PathVariable("customerUUID") String customerUUID) {
        List<SubscriptionDto> subscriptionDtos = subscriptionService.getSubscriptionsByCustomer(customerUUID, userContext.getAccountId());
        ApiResponse<List<SubscriptionDto>> apiResponse = ApiResponse.<List<SubscriptionDto>>builder()
                .data(subscriptionDtos)
                .success(true)
                .build();
        return ResponseEntity.ok(apiResponse);
    }

    @GetMapping("/{subscriptionUuid}")
    @Operation(summary = "Get subscription", description = "Retrieves a specific subscription by its UUID", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved subscription"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Subscription not found", content = @Content)})
    public ResponseEntity<ApiResponse<SubscriptionDto>> getSubscription(@AuthenticationPrincipal UserContext userContext,
                                                                         @PathVariable("subscriptionUuid") String subscriptionUuid) {
        SubscriptionDto subscriptionDto = subscriptionService.getSubscriptionDtoById(subscriptionUuid, userContext.getAccountId());
        ApiResponse<SubscriptionDto> apiResponse = ApiResponse.<SubscriptionDto>builder()
                .data(subscriptionDto)
                .success(true)
                .build();
        return ResponseEntity.ok(apiResponse);
    }

    @GetMapping()
    @Operation(summary = "List all subscriptions", description = "Retrieves all subscriptions for the authenticated account", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved subscriptions"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content)})
    public ResponseEntity<ApiResponse<List<SubscriptionDto>>> getSubscriptions(@AuthenticationPrincipal UserContext userContext) {
        ApiResponse<List<SubscriptionDto>> apiResponse = ApiResponse.<List<SubscriptionDto>>builder().success(true).build();
        apiResponse.setData(subscriptionService.getSubscriptionsByAccount(userContext.getAccountId()));
        apiResponse.setSuccess(true);
        return ResponseEntity.ok(apiResponse);
    }

    @PostMapping()
    @Operation(summary = "Create subscription", description = "Creates a new subscription for a customer with a specific plan", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Successfully created a subscription"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Invalid plan or customer ID", content = @Content)})
    public ResponseEntity<ApiResponse<SubscribedCustomerResponse>> createSubscription(@AuthenticationPrincipal UserContext userContext,
                                                                                       @Valid @RequestBody SubscriptionRequest subscriptionRequest) {
        SubscribedCustomerResponse subscribedCustomerResponse =
                subscriptionService.subscribeCustomer(subscriptionRequest, userContext.getAccountId());
        ApiResponse<SubscribedCustomerResponse> apiResponse = ApiResponse.<SubscribedCustomerResponse>builder()
                .data(subscribedCustomerResponse)
                .success(true)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(apiResponse);
    }

    @DeleteMapping("/{subscriptionUuid}")
    @Operation(summary = "Delete subscription", description = "Deletes a specific subscription by its UUID", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully deleted subscription"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Subscription not found", content = @Content)})
    public ResponseEntity<ApiResponse<Void>> deleteSubscription(@AuthenticationPrincipal UserContext userContext, @PathVariable(name = "subscriptionUuid") String subscriptionUuid) {
        String accountId = userContext.getAccountId();
        Subscription subscription = subscriptionService.getSubscriptionById(subscriptionUuid, accountId);
        subscriptionService.deleteSubscription(subscription);
        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder().success(true).build();
        return ResponseEntity.status(HttpStatus.OK).body(apiResponse);
    }

    @PatchMapping("/{subscriptionUuid}")
    @Operation(summary = "Update subscription", description = "Modifies an existing subscription", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully updated subscription"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Subscription not found", content = @Content)})
    public ResponseEntity<ApiResponse<SubscriptionDto>> editSubscription(@AuthenticationPrincipal UserContext userContext,
                                                        @Valid @RequestBody SubscriptionRequest subscriptionRequest,
                                                        @PathVariable(name = "subscriptionUuid") String subscriptionUuid) {
        SubscriptionDto subscriptionDto = subscriptionService
                .editSubscriptionById(subscriptionRequest, subscriptionUuid, userContext.getAccountId());
        ApiResponse<SubscriptionDto> apiResponse = ApiResponse.<SubscriptionDto>builder()
                .success(true)
                .data(subscriptionDto)
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(apiResponse);

    }

    @PostMapping("/{subscriptionUuid}/activate")
    @Operation(summary = "Activate subscription", description = "Activates a draft subscription by marking its initial invoice as paid and granting entitlements", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully activated subscription"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Subscription not found", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Subscription is already active or has no initial invoice", content = @Content)})
    public ResponseEntity<ApiResponse<Void>> activateSubscription(@AuthenticationPrincipal UserContext userContext,
                                                                  @PathVariable String subscriptionUuid) {
        subscriptionService.activateSubscription(subscriptionUuid, userContext.getAccountId());
        auditHelper.audit("SUBSCRIPTION_ACTIVATED",
                UUID.fromString(userContext.getUserId()),
                UUID.fromString(userContext.getAccountId()),
                "SUBSCRIPTION", subscriptionUuid,
                "Activated subscription " + subscriptionUuid);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).build());
    }

    @PostMapping("/{subscriptionUuid}/deactivate")
    @Operation(summary = "Deactivate subscription", description = "Deactivates an active subscription", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "501", description = "Not yet implemented"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Subscription not found", content = @Content)})
    public ResponseEntity<ApiResponse<Void>> deactivateSubscription(@AuthenticationPrincipal UserContext userContext,
                                                              @PathVariable(name = "subscriptionUuid") String subscriptionUuid) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(ApiResponse.<Void>builder().success(false)
                        .error(new com.tansoflow.tansocore.model.response.Error("Deactivate subscription is not yet implemented")).build());
    }

    @PostMapping("/{subscriptionUuid}/cancel")
    @Operation(summary = "Cancel subscription", description = "Cancels a subscription immediately or at end of billing period", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully cancelled subscription"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Subscription not found", content = @Content)})
    public ResponseEntity<ApiResponse<Void>> cancelSubscription(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable("subscriptionUuid") String subscriptionUuid,
            @RequestParam(required = false, defaultValue = "END_OF_PERIOD") String cancelMode) {
        subscriptionService.cancelSubscription(subscriptionUuid, cancelMode, userContext.getAccountId());
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).build());
    }

    @DeleteMapping("/{subscriptionUuid}/scheduled-changes")
    @Operation(summary = "Cancel scheduled changes", description = "Cancels all scheduled plan changes for a subscription", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<Void>> cancelScheduledChanges(@AuthenticationPrincipal UserContext userContext,
                                                                    @PathVariable(name = "subscriptionUuid") String subscriptionUuid) {
        subscriptionService.cancelScheduledChangesForSubscription(java.util.UUID.fromString(subscriptionUuid), java.util.UUID.fromString(userContext.getAccountId()));
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).build());
    }

    @DeleteMapping("/{subscriptionUuid}/scheduled-cancellation")
    @Operation(summary = "Cancel scheduled cancellation", description = "Cancels a scheduled cancellation for a subscription", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<Void>> cancelScheduledCancellation(@AuthenticationPrincipal UserContext userContext,
                                                                         @PathVariable(name = "subscriptionUuid") String subscriptionUuid) {
        subscriptionService.cancelScheduledSubscriptionCancellation(subscriptionUuid, userContext.getAccountId());
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).build());
    }

    @GetMapping("/scheduled-changes")
    @Operation(summary = "Get all scheduled plan changes", description = "Retrieves all scheduled plan changes for the account", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<List<SubscriptionScheduledChangeDto>>> getScheduledChanges(@AuthenticationPrincipal UserContext userContext) {
        List<SubscriptionScheduledChangeDto> changes = subscriptionService.getScheduledChangesByAccount(userContext.getAccountId());
        return ResponseEntity.ok(ApiResponse.<List<SubscriptionScheduledChangeDto>>builder().data(changes).success(true).build());
    }

    @GetMapping("/scheduled-cancellations")
    @Operation(summary = "Get all scheduled cancellations", description = "Retrieves all scheduled cancellations for the account", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<List<SubscriptionDto>>> getScheduledCancellations(@AuthenticationPrincipal UserContext userContext) {
        List<SubscriptionDto> cancellations = subscriptionService.getScheduledCancellationsByAccount(userContext.getAccountId());
        return ResponseEntity.ok(ApiResponse.<List<SubscriptionDto>>builder().data(cancellations).success(true).build());
    }
}
