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

import com.stripe.exception.StripeException;
import com.tansoflow.tansocore.auth.CustomerAccessGuard;
import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.integration.stripe.StripePaymentMethodService;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.service.internal.account.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * SetupIntent pre-authorization: the agent's principal saves a card once,
 * then the agent buys off-session with it. Card data never touches Tanso.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/client/customers/{customerReferenceId}/payment-methods")
@PreAuthorize("hasRole('CLIENT')")
@Tag(name = "Payment Methods", description = "Save a payment method for programmatic purchases")
public class PaymentMethodClientController {

    private final CustomerAccessGuard customerAccessGuard;
    private final CustomerService customerService;
    private final StripePaymentMethodService stripePaymentMethodService;

    @PostMapping("/setup-intent")
    @PreAuthorize("hasAnyRole('CLIENT','CUSTOMER')")
    @Operation(summary = "Create a SetupIntent",
            description = "Returns a client_secret the principal confirms directly with Stripe. Requires the "
                    + "'purchase' scope on customer keys.", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<Map<String, String>>> createSetupIntent(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String customerReferenceId) throws StripeException {
        customerReferenceId = customerAccessGuard.resolveCustomerRef(userContext, customerReferenceId);
        customerAccessGuard.requirePurchaseScope(userContext);
        Customer customer = customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(
                customerReferenceId, userContext.getAccountId());

        StripePaymentMethodService.SetupIntentResult result = stripePaymentMethodService.createSetupIntent(
                UUID.fromString(userContext.getAccountId()), customer.getId());
        return ResponseEntity.ok(ApiResponse.<Map<String, String>>builder()
                .data(Map.of(
                        "setup_intent_id", result.setupIntentId(),
                        "client_secret", result.clientSecret(),
                        "stripe_customer_id", result.stripeCustomerId()))
                .success(true).build());
    }

    @PostMapping("/default")
    @PreAuthorize("hasAnyRole('CLIENT','CUSTOMER')")
    @Operation(summary = "Set the default payment method",
            description = "Attaches a confirmed payment method (pm_...) and stores it as the customer's default "
                    + "for off-session charges.", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<Void>> setDefaultPaymentMethod(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String customerReferenceId,
            @RequestBody DefaultPaymentMethodRequest request) throws StripeException {
        customerReferenceId = customerAccessGuard.resolveCustomerRef(userContext, customerReferenceId);
        customerAccessGuard.requirePurchaseScope(userContext);
        Customer customer = customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(
                customerReferenceId, userContext.getAccountId());

        stripePaymentMethodService.setDefaultPaymentMethod(
                UUID.fromString(userContext.getAccountId()), customer.getId(), request.getPaymentMethodId());
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).build());
    }

    @Data
    public static class DefaultPaymentMethodRequest {
        @NotBlank
        private String paymentMethodId;
    }
}
