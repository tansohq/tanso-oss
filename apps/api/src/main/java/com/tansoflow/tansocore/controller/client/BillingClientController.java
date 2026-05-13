package com.tansoflow.tansocore.controller.client;

import com.stripe.exception.StripeException;
import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.entity.AccountSetting;
import com.tansoflow.tansocore.entity.Invoice;
import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.integration.stripe.StripeSyncService;
import com.tansoflow.tansocore.model.api.external.StripeMode;
import com.tansoflow.tansocore.model.billing.InvoiceDto;
import com.tansoflow.tansocore.model.billing.response.StripeCheckoutSessionsResponse;
import com.tansoflow.tansocore.model.data.stripe.StripePaymentLinkDto;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.model.response.Error;
import com.tansoflow.tansocore.model.response.PaginatedResponse;
import com.tansoflow.tansocore.service.internal.account.AccountService;
import com.tansoflow.tansocore.service.internal.monetization.InvoiceService;
import com.tansoflow.tansocore.service.internal.monetization.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/client/billing")
@PreAuthorize("hasRole('CLIENT')")
@Tag(name = "Client Billing", description = "Billing operations for client applications")
public class BillingClientController {
    private final InvoiceService invoiceService;
    private final SubscriptionService subscriptionService;
    private final StripeSyncService stripeSyncService;
    private final AccountService accountService;

    @PostMapping("/invoices/{invoiceId}/mark-paid")
    @Operation(summary = "Mark invoice as paid", description = "Manually marks an invoice as paid and activates the associated subscription", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Invoice marked as paid successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Invoice not found", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> markInvoicePaid(@AuthenticationPrincipal UserContext userContext, @PathVariable String invoiceId) {
        final String accountId = userContext.getAccountId();

        AccountSetting accountSetting = accountService.retrieveAccountSettings(accountId);
        if (accountSetting != null && accountSetting.getStripeMode().isStripeIntegration()) {
            return ResponseEntity.badRequest().body(ApiResponse.<Void>builder()
                    .success(false)
                    .error(new Error("Manual invoice payment is not available for Stripe-integrated accounts. Invoices are paid through Stripe."))
                    .build());
        }

        subscriptionService.subscriptionInvoicePaid(invoiceId, accountId);
        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder().success(true).build();
        return ResponseEntity.ok(apiResponse);
    }

    @PostMapping("/invoices/subscription/{subscriptionId}/stripe/checkout")
    @Operation(summary = "Create Stripe checkout session (DEPRECATED)", description = "Generates a Stripe Checkout URL for a specific subscription. This endpoint is deprecated, use /invoices/{invoiceId}/stripe/checkout instead.", security = @SecurityRequirement(name = "Bearer"), deprecated = true)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Endpoint is deprecated and inaccessible")
    })
    public ResponseEntity<ApiResponse<StripeCheckoutSessionsResponse>> createStripeSubscriptionCheckoutSession(@AuthenticationPrincipal UserContext userContext,
                                                                                                   @PathVariable String subscriptionId) {
        ApiResponse<StripeCheckoutSessionsResponse> apiResponse = ApiResponse.<StripeCheckoutSessionsResponse>builder()
                .success(false)
                .error(new Error("This endpoint is deprecated and inaccessible. Please use /invoices/{invoiceId}/stripe/checkout instead."))
                .build();
        return ResponseEntity.status(403).body(apiResponse);
    }

    @PostMapping("/subscriptions/{subscriptionId}/stripe/checkout")
    @Operation(summary = "Create Stripe checkout session for subscription", description = "Finds the first DUE invoice for a subscription and generates a Stripe Checkout URL for payment", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Checkout session created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid subscription or billing state"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No DUE invoice found for subscription")
    })
    public ResponseEntity<ApiResponse<StripeCheckoutSessionsResponse>> createStripeCheckoutSession(@AuthenticationPrincipal UserContext userContext,
                                                                                                   @PathVariable String subscriptionId) {
        try {
            final String accountId = userContext.getAccountId();
            AccountSetting accountSetting = accountService.retrieveAccountSettings(accountId);

            if (accountSetting == null || !accountSetting.isStripeEnabled()) {
                return ResponseEntity.badRequest().body(ApiResponse.<StripeCheckoutSessionsResponse>builder()
                        .success(false)
                        .error(new Error("Stripe is not enabled at the account"))
                        .build());
            }

            Subscription subscription = subscriptionService.getSubscriptionById(subscriptionId, accountId);
            if (subscription == null) {
                return ResponseEntity.status(404).body(ApiResponse.<StripeCheckoutSessionsResponse>builder()
                        .success(false)
                        .error(new Error("Subscription not found"))
                        .build());
            }

            Invoice dueInvoice = invoiceService.retrieveCurrentlyDueBySubscription(subscription);
            if (dueInvoice == null) {
                return ResponseEntity.status(404).body(ApiResponse.<StripeCheckoutSessionsResponse>builder()
                        .success(false)
                        .error(new Error("No DUE invoice found for this subscription"))
                        .build());
            }

            String invoiceId = dueInvoice.getId().toString();

            // Only create a standalone Stripe invoice for PAYMENT_PASS_THROUGH.
            // In STRIPE_INTEGRATION, invoices are auto-generated by the Stripe subscription.
            if (accountSetting.getStripeMode() == StripeMode.PAYMENT_PASS_THROUGH) {
                stripeSyncService.syncNewInvoice(UUID.fromString(invoiceId), UUID.fromString(accountId));
            }

            StripePaymentLinkDto dto = stripeSyncService.retrieveStripeSession(invoiceId, accountId);

            StripeCheckoutSessionsResponse response = new StripeCheckoutSessionsResponse();
            response.setUrl(dto.getPaymentLink());

            return ResponseEntity.ok(ApiResponse.<StripeCheckoutSessionsResponse>builder()
                    .success(true)
                    .data(response)
                    .build());
        } catch (StripeException e) {
            log.error("Error creating stripe checkout session", e);
            return ResponseEntity.badRequest().body(ApiResponse.<StripeCheckoutSessionsResponse>builder()
                    .success(false)
                    .error(new Error("Failed to create Stripe checkout session", e.getMessage()))
                    .build());
        } catch (Exception e) {
            log.error("Unexpected error creating stripe checkout session", e);
            return ResponseEntity.badRequest().body(ApiResponse.<StripeCheckoutSessionsResponse>builder()
                    .success(false)
                    .error(new Error("Unexpected error creating checkout session"))
                    .build());
        }
    }

    @GetMapping("/invoices/{externalClientCustomerId}")
    @Operation(summary = "List customer invoices", description = "Retrieves all invoices for a customer using the external client reference ID", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Invoices retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Customer reference not found", content = @Content)
    })
    public ResponseEntity<ApiResponse<PaginatedResponse<InvoiceDto>>> getInvoicesByExternalClientCustomerId(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String externalClientCustomerId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        List<InvoiceDto> allInvoices = invoiceService.retrieveInvoicesByExternalClientCustomerId(externalClientCustomerId,
                userContext.getAccountId());

        int total = allInvoices.size();
        int fromIndex = Math.min(offset, total);
        int toIndex = Math.min(offset + limit, total);
        List<InvoiceDto> page = allInvoices.subList(fromIndex, toIndex);

        PaginatedResponse<InvoiceDto> paginatedResponse = PaginatedResponse.<InvoiceDto>builder()
                .items(page)
                .pagination(PaginatedResponse.PaginationMeta.builder()
                        .total(total)
                        .limit(limit)
                        .offset(offset)
                        .hasMore(toIndex < total)
                        .build())
                .build();

        ApiResponse<PaginatedResponse<InvoiceDto>> apiResponse = ApiResponse.<PaginatedResponse<InvoiceDto>>builder()
                .success(true).data(paginatedResponse).build();
        return ResponseEntity.ok(apiResponse);
    }

}
