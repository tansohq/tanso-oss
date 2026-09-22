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

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.tansoflow.tansocore.auth.CustomerAccessGuard;
import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.model.response.GateError;
import com.tansoflow.tansocore.model.subscription.UpgradeResult;
import com.tansoflow.tansocore.model.subscription.request.ClientChangeSubscriptionRequest;
import com.tansoflow.tansocore.model.subscription.request.ClientSubscriptionRequest;
import com.tansoflow.tansocore.model.subscription.response.PlanChangeResponse;
import com.tansoflow.tansocore.model.subscription.response.SubscribedCustomerResponse;
import com.tansoflow.tansocore.model.subscription.type.SubscriptionChangeType;
import com.tansoflow.tansocore.service.internal.monetization.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
import com.tansoflow.tansocore.entity.AccountSetting;
import com.tansoflow.tansocore.model.api.external.StripeMode;
import com.tansoflow.tansocore.model.data.stripe.StripePaymentLinkDto;
import com.stripe.exception.StripeException;
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
@ConditionalOnProperty(name = "app.modules.monetization.enabled", havingValue = "true", matchIfMissing = true)
public class SubscriptionClientController {
    private final CustomerAccessGuard customerAccessGuard;
    private final SubscriptionService subscriptionService;
    private final com.tansoflow.tansocore.service.internal.account.AccountService accountService;
    private final com.tansoflow.tansocore.integration.stripe.StripeSyncService stripeSyncService;
    private final com.tansoflow.tansocore.service.internal.account.CustomerService customerService;

    /** ck_ callers may only touch subscriptions belonging to their own customer. */
    private void requireOwnSubscription(UserContext userContext, String subscriptionId) {
        if (!userContext.isCustomerScoped()) {
            return;
        }
        com.tansoflow.tansocore.entity.Subscription subscription =
                subscriptionService.getSubscriptionById(subscriptionId, userContext.getAccountId());
        if (subscription == null
                || !subscription.getCustomer().getId().toString().equals(userContext.getCustomerId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "This API key is scoped to another customer");
        }
    }

    // TODO: Needs to return hosted invoice url in meta data
    @PostMapping()
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('CLIENT','CUSTOMER')")
    @Operation(summary = "Create subscription", description = "Adds a new subscription to a customer with a specific plan. "
            + "For paid plans with a supplied or saved payment method the subscription is created synchronously; "
            + "without one, customer-key callers get 402 with a checkout URL and a pollable checkout session.", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Successfully created a subscription"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "402", description =
                    "Payment required: the plan is paid and the customer has no usable payment method. "
                            + "success is false and error is the gate envelope: code=payment_required, gate=payment, "
                            + "action=complete_checkout, url=the checkout URL to hand to a human, poll=the checkout-session "
                            + "GET URL, retry_after=null. data still carries the SubscribedCustomerResponse "
                            + "(checkoutUrl, checkoutSessionId). Customer-scoped (ck_) keys only."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description =
                    "planId is missing or names no plan on this account", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Invalid plan or customer ID", content = @Content)
    })
    public ResponseEntity<ApiResponse<SubscribedCustomerResponse>> createSubscription(@AuthenticationPrincipal UserContext userContext,
                                                          @Valid @RequestBody ClientSubscriptionRequest subscriptionRequest,
                                                          HttpServletRequest httpRequest) {
        subscriptionRequest.setCustomerReferenceId(
                customerAccessGuard.resolveCustomerRef(userContext, subscriptionRequest.getCustomerReferenceId()));
        customerAccessGuard.requirePurchaseScope(userContext);
        SubscribedCustomerResponse subscribedCustomerResponse =
                subscriptionService.clientSubscribeCustomer(subscriptionRequest, userContext.getAccountId());

        // Pass-through billing leaves a paid plan as an inactive subscription plus a DUE invoice, and
        // until now only the operator could mint the link to pay it. The subscribe transaction has
        // committed by here, so the hosted invoice link can be created and handed to the agent.
        if (userContext.isCustomerScoped()
                && subscribedCustomerResponse.getCheckoutUrl() == null
                && subscribedCustomerResponse.getInvoice() != null
                && "DUE".equals(subscribedCustomerResponse.getInvoice().getStatus())) {
            AccountSetting accountSetting = accountService.retrieveAccountSettings(userContext.getAccountId());
            if (accountSetting != null && accountSetting.getStripeMode() == StripeMode.PAYMENT_PASS_THROUGH) {
                // Stripe will not send an invoice to a customer without an email. An agent that signed up
                // without one gets told exactly where to put it instead of a 500.
                com.tansoflow.tansocore.entity.Customer customer = customerService
                        .retrieveCustomerByExternalClientCustomerIdAndAccount(
                                subscriptionRequest.getCustomerReferenceId(), userContext.getAccountId());
                if (customer.getEmail() == null || customer.getEmail().isBlank()) {
                    String baseUrl = httpRequest.getRequestURL().toString().replace(httpRequest.getRequestURI(), "");
                    String ownerUrl = baseUrl + "/api/v1/client/customers/" + customer.getExternalClientCustomerId() + "/owner";
                    return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).contentType(MediaType.APPLICATION_JSON)
                            .body(ApiResponse.<SubscribedCustomerResponse>builder()
                                    .data(subscribedCustomerResponse)
                                    .error(GateError.ownerEmailRequired(ownerUrl))
                                    .success(false)
                                    .build());
                }
                try {
                    StripePaymentLinkDto link = stripeSyncService.syncNewInvoice(
                            UUID.fromString(subscribedCustomerResponse.getInvoice().getId()),
                            UUID.fromString(userContext.getAccountId()));
                    subscribedCustomerResponse.setCheckoutUrl(link.getPaymentLink());
                } catch (StripeException e) {
                    throw new IllegalStateException("Could not create the Stripe payment link for invoice "
                            + subscribedCustomerResponse.getInvoice().getId() + ": " + e.getMessage(), e);
                }
            }
        }

        // Agent-facing 402: a checkout URL with no active subscription means "payment required, hand
        // this to your principal". Covers both the Stripe-driven path (no subscription yet) and the
        // pass-through path (inactive subscription plus a DUE invoice). Tenant (sk_) callers keep 201.
        var subscription = subscribedCustomerResponse.getSubscription();
        boolean paymentRequired = subscribedCustomerResponse.getCheckoutUrl() != null
                && (subscription == null || !Boolean.TRUE.equals(subscription.getIsActive()));
        HttpStatus status = paymentRequired && userContext.isCustomerScoped()
                ? HttpStatus.PAYMENT_REQUIRED
                : HttpStatus.CREATED;

        GateError gateError = null;
        if (status == HttpStatus.PAYMENT_REQUIRED) {
            String baseUrl = httpRequest.getRequestURL().toString().replace(httpRequest.getRequestURI(), "");
            // A hosted invoice link has no checkout session to poll; the customer's status shows the plan and
            // claim flip once the invoice is paid, so an agent is never left without something to watch.
            String pollUrl = subscribedCustomerResponse.getCheckoutSessionId() != null
                    ? baseUrl + "/api/v1/client/checkout-sessions/" + subscribedCustomerResponse.getCheckoutSessionId()
                    : baseUrl + "/api/v1/client/customers/" + subscriptionRequest.getCustomerReferenceId() + "/status";
            gateError = GateError.paymentRequired(subscribedCustomerResponse.getCheckoutUrl(), pollUrl);
        }

        ApiResponse<SubscribedCustomerResponse> apiResponse = ApiResponse.<SubscribedCustomerResponse>builder()
                .data(subscribedCustomerResponse)
                .error(gateError)
                .success(status != HttpStatus.PAYMENT_REQUIRED)
                .build();
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(apiResponse);
    }

    @PostMapping("/cancellation/{subscriptionId}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('CLIENT','CUSTOMER')")
    @Operation(summary = "Cancel subscription", description = "Cancels a customer subscription immediately or at the end of the period", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully cancelled the subscription"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Subscription not found", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> cancelSubscription(@AuthenticationPrincipal UserContext userContext,
                                                          @PathVariable("subscriptionId") String subscriptionId,
                                                          @RequestParam(required = false, defaultValue = "END_OF_PERIOD") String cancelMode) {
        requireOwnSubscription(userContext, subscriptionId);
        subscriptionService.cancelSubscription(subscriptionId, cancelMode, userContext.getAccountId());

        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder().success(true).build();

        return ResponseEntity.ok(apiResponse);
    }

    @DeleteMapping("/cancellation/{subscriptionId}/scheduled")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('CLIENT','CUSTOMER')")
    @Operation(summary = "Cancel scheduled cancellation", description = "Cancels a previously scheduled subscription cancellation", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully cancelled the scheduled cancellation"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Subscription not found", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> cancelScheduledSubscriptionCancellation(@AuthenticationPrincipal UserContext userContext,
                                                                                     @PathVariable("subscriptionId") String subscriptionId) {
        requireOwnSubscription(userContext, subscriptionId);
        subscriptionService.cancelScheduledSubscriptionCancellation(subscriptionId, userContext.getAccountId());
        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder().success(true).build();
        return ResponseEntity.ok(apiResponse);
    }

    @PostMapping("/{subscriptionId}/plan-change")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('CLIENT','CUSTOMER')")
    @Operation(summary = "Change plan", description = "Changes a customer subscription to a new plan by upgrading or downgrading", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description =
                    "The plan changed, or the downgrade was scheduled. Where Stripe runs the billing, an upgrade "
                            + "that costs money answers 200 only after Stripe charged the prorated amount."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description =
                    "Tenant (sk_) keys on STRIPE_INTEGRATION: the upgrade was invoiced but not paid yet. "
                            + "data.status=payment_pending, data.paymentUrl=Stripe's hosted invoice. The plan changes "
                            + "when it is paid."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "402", description =
                    "The upgrade costs money and the caller holds a customer key: the plan change waits on payment. "
                            + "error.code=payment_required, gate=payment, action=complete_checkout with the hosted "
                            + "invoice url, poll=the customer's status URL, retry_after=null. The plan swaps when the "
                            + "invoice is paid. Where Stripe runs the billing (STRIPE_DRIVEN, STRIPE_INTEGRATION) Stripe "
                            + "first charges the saved card; this is returned when that charge did not go through, or "
                            + "when Stripe sends the invoice by email instead of charging, and url is Stripe's hosted "
                            + "invoice. Also action=nominate_owner when Tanso needs an email to send an invoice to and "
                            + "no card is saved. Customer-scoped (ck_) keys only."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description =
                    "The proration charge exceeds the account's spend cap or the calling key's budget: "
                            + "error.code=spend_cap_exceeded or budget_exceeded", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Subscription or plan not found", content = @Content)
    })
    public ResponseEntity<ApiResponse<PlanChangeResponse>> changeSubscription(@AuthenticationPrincipal UserContext userContext,
                                                                @Valid @RequestBody ClientChangeSubscriptionRequest request, @PathVariable("subscriptionId") String subscriptionId,
                                                                HttpServletRequest httpRequest) {
        requireOwnSubscription(userContext, subscriptionId);
        customerAccessGuard.requirePurchaseScope(userContext);

        // Stripe will not send an invoice to a customer with no email, so ask for the owner before raising one.
        // Checking afterwards would leave a DUE invoice nobody could ever pay. Where Stripe runs the billing and a
        // card is saved, Stripe charges the card and needs no email, so the gate is skipped.
        com.tansoflow.tansocore.entity.Customer customer = subscriptionService
                .getSubscriptionById(subscriptionId, userContext.getAccountId()).getCustomer();
        String base = httpRequest.getRequestURL().toString().replace(httpRequest.getRequestURI(), "");
        AccountSetting accountSetting = accountService.retrieveAccountSettings(userContext.getAccountId());
        boolean stripeBilled = accountSetting != null
                && (accountSetting.getStripeMode() == StripeMode.STRIPE_DRIVEN
                || accountSetting.getStripeMode().isStripeIntegration());
        boolean stripeChargesSavedCard = stripeBilled && customer.getStripeDefaultPaymentMethodId() != null;
        if (userContext.isCustomerScoped() && request.getChangeType() == SubscriptionChangeType.UPGRADE
                && !stripeChargesSavedCard
                && (customer.getEmail() == null || customer.getEmail().isBlank())) {
            String ownerUrl = base + "/api/v1/client/customers/" + customer.getExternalClientCustomerId() + "/owner";
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).contentType(MediaType.APPLICATION_JSON)
                    .body(ApiResponse.<PlanChangeResponse>builder().error(GateError.ownerEmailRequired(ownerUrl)).success(false).build());
        }

        UpgradeResult upgrade = UpgradeResult.notWaiting();
        if (request.getChangeType() == SubscriptionChangeType.UPGRADE) {
            upgrade = subscriptionService.upgradeSubscription(subscriptionId, userContext.getAccountId(), request.getChangeToPlanId(), true);
        }

        if (request.getChangeType() == SubscriptionChangeType.DOWNGRADE) {
            subscriptionService.scheduleDowngradeSubscription(subscriptionId, userContext.getAccountId(), request.getChangeToPlanId());
        }

        // A tenant key is the operator's own server, not an agent: the upgrade is accepted (202) and data carries the
        // invoice to pay, the same way subscribe hands tenant callers a checkoutUrl without a gate. Only
        // STRIPE_INTEGRATION reaches this for tenant keys; the other modes wait on payment only for customer keys.
        if (upgrade.waitingOnPayment() && !userContext.isCustomerScoped()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON)
                    .body(ApiResponse.<PlanChangeResponse>builder()
                            .data(PlanChangeResponse.paymentPending(upgrade.stripePaymentUrl()))
                            .success(true)
                            .build());
        }

        // The upgrade is raised but not granted: the agent gets the invoice to hand to a human, and something to
        // poll. Paying it swaps the plan. An agent must not reach a paid tier before its principal pays for it.
        if (upgrade.waitingOnPayment()) {
            String pollUrl = base + "/api/v1/client/customers/" + customer.getExternalClientCustomerId() + "/status";
            UUID pendingInvoiceId = upgrade.pendingInvoiceId();

            GateError gate;
            if (upgrade.stripePaymentUrl() != null) {
                // Stripe-billed: Stripe could not charge the saved card (it holds the change as a pending_update),
                // or the subscription is send_invoice and the invoice waits for a human.
                gate = GateError.paymentRequired(upgrade.stripePaymentUrl(), pollUrl);
            } else if (accountSetting != null && accountSetting.getStripeMode() == StripeMode.PAYMENT_PASS_THROUGH) {
                try {
                    StripePaymentLinkDto link = stripeSyncService.syncNewInvoice(pendingInvoiceId, UUID.fromString(userContext.getAccountId()));
                    gate = GateError.paymentRequired(link.getPaymentLink(), pollUrl);
                } catch (StripeException e) {
                    throw new IllegalStateException("Could not create the Stripe payment link for adjustment invoice "
                            + pendingInvoiceId + ": " + e.getMessage(), e);
                }
            } else {
                gate = GateError.paymentRequiredNoProcessor();
            }
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).contentType(MediaType.APPLICATION_JSON)
                    .body(ApiResponse.<PlanChangeResponse>builder().error(gate).success(false).build());
        }

        ApiResponse<PlanChangeResponse> apiResponse = ApiResponse.<PlanChangeResponse>builder().success(true).build();

        return ResponseEntity.ok(apiResponse);
    }

    @DeleteMapping("/{subscriptionId}/plan-change/scheduled")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('CLIENT','CUSTOMER')")
    @Operation(summary = "Cancel scheduled plan change", description = "Cancels all scheduled changes for a subscription", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully cancelled scheduled changes"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Subscription not found", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> cancelScheduledSubscriptionChanges(@AuthenticationPrincipal UserContext userContext,
                                                                                @PathVariable("subscriptionId") String subscriptionId) {
        requireOwnSubscription(userContext, subscriptionId);
        subscriptionService.cancelScheduledChangesForSubscription(UUID.fromString(subscriptionId), UUID.fromString(userContext.getAccountId()));
        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder().success(true).build();
        return ResponseEntity.ok(apiResponse);
    }

}
