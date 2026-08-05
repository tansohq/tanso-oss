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

import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.entity.AccountSetting;
import com.tansoflow.tansocore.integration.stripe.StripeSyncService;
import com.tansoflow.tansocore.model.api.external.StripeMode;
import com.tansoflow.tansocore.model.billing.InvoiceDto;
import com.tansoflow.tansocore.model.billing.response.StripeCheckoutSessionsResponse;
import com.tansoflow.tansocore.model.data.stripe.StripePaymentLinkDto;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.model.response.Error;
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
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/v1/monetization/billing")
@PreAuthorize("hasRole('TANSO_UI')")
@Tag(name = "Billing", description = "Billing and invoice management operations")
public class BillingController {
    private final InvoiceService invoiceService;
    private final SubscriptionService subscriptionService;
    private final AccountService accountService;
    private final StripeSyncService stripeSyncService;

    @GetMapping("/invoices")
    @Operation(summary = "List invoices", description = "Retrieves all invoices for the authenticated account", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved invoices"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content)
    })
    public ResponseEntity<ApiResponse<List<InvoiceDto>>> getInvoices(@AuthenticationPrincipal UserContext userContext,
                                                                     @RequestParam(required = false, defaultValue = "false") String onlyDue) {
        ApiResponse<List<InvoiceDto>> apiResponse = ApiResponse.<List<InvoiceDto>>builder().success(true).build();
        if (onlyDue.equalsIgnoreCase("true")) {
            apiResponse.setData(invoiceService.retrieveOnlyDueInvoicesByAccount(userContext.getAccountId()));
        } else {
            apiResponse.setData(invoiceService.retrieveInvoicesByAccount(userContext.getAccountId()));
        }

        return ResponseEntity.ok(apiResponse);
    }

    @GetMapping("/invoices/{invoiceId}")
    @Operation(summary = "Get invoice details", description = "Retrieves a single invoice with line items for the authenticated account", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved invoice"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Invoice not found", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content)
    })
    public ResponseEntity<ApiResponse<InvoiceDto>> getInvoice(@AuthenticationPrincipal UserContext userContext, @PathVariable String invoiceId) {
        InvoiceDto invoiceDto = invoiceService.retrieveInvoiceById(invoiceId, userContext.getAccountId());
        ApiResponse<InvoiceDto> apiResponse = ApiResponse.<InvoiceDto>builder().success(true).data(invoiceDto).build();
        return ResponseEntity.ok(apiResponse);
    }

    @PatchMapping("/invoices/{invoiceId}")
    @Operation(summary = "Mark an invoice as paid", description = "Records an out-of-band payment and activates the associated subscription. Not available for Stripe-integrated accounts, where invoices are paid through Stripe.", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Invoice marked as paid"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Stripe-integrated account", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Invoice not found", content = @Content)})
    public ResponseEntity<ApiResponse<Void>> patchInvoice(@AuthenticationPrincipal UserContext userContext, @PathVariable String invoiceId) {
        final String accountId = userContext.getAccountId();

        AccountSetting accountSetting = accountService.retrieveAccountSettings(accountId);
        if (accountSetting != null && accountSetting.getStripeMode().isStripeIntegration()) {
            return ResponseEntity.badRequest().body(ApiResponse.<Void>builder()
                    .success(false)
                    .error(new Error("Manual invoice payment is not available for Stripe-integrated accounts. Invoices are paid through Stripe."))
                    .build());
        }

        subscriptionService.subscriptionInvoicePaid(invoiceId, accountId);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).build());
    }

    @PostMapping("/invoices/{invoiceId}/checkout-link")
    @Operation(summary = "Create a Stripe checkout link for an invoice", description = "Returns the hosted Stripe payment URL for a DUE invoice, creating the Stripe invoice first if needed. The operator can send this link to the customer.", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Checkout link created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Stripe not enabled or invoice not DUE", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Invoice not found", content = @Content)})
    public ResponseEntity<ApiResponse<StripeCheckoutSessionsResponse>> createInvoiceCheckoutLink(@AuthenticationPrincipal UserContext userContext, @PathVariable String invoiceId) {
        final String accountId = userContext.getAccountId();
        try {
            AccountSetting accountSetting = accountService.retrieveAccountSettings(accountId);
            if (accountSetting == null || !accountSetting.isStripeEnabled()) {
                return ResponseEntity.badRequest().body(ApiResponse.<StripeCheckoutSessionsResponse>builder()
                        .success(false)
                        .error(new Error("Stripe is not enabled at the account"))
                        .build());
            }

            InvoiceDto invoice = invoiceService.retrieveInvoiceById(invoiceId, accountId);
            if (!"DUE".equals(invoice.getStatus())) {
                return ResponseEntity.badRequest().body(ApiResponse.<StripeCheckoutSessionsResponse>builder()
                        .success(false)
                        .error(new Error("Only DUE invoices have a checkout link (status is " + invoice.getStatus() + ")"))
                        .build());
            }

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
        } catch (Exception e) {
            log.error("Error creating checkout link for invoice {} on account {}", invoiceId, accountId, e);
            return ResponseEntity.badRequest().body(ApiResponse.<StripeCheckoutSessionsResponse>builder()
                    .success(false)
                    .error(new Error("Failed to create checkout link", e.getMessage()))
                    .build());
        }
    }

}
