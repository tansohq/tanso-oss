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
package com.tansoflow.tansocore.controller.tanso.account;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.model.apikey.CustomerApiKeyDto;
import com.tansoflow.tansocore.model.apikey.KeyBudgetDto;
import com.tansoflow.tansocore.model.apikey.request.CreateCustomerApiKeyRequest;
import com.tansoflow.tansocore.model.apikey.request.UpdateKeyBudgetRequest;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.service.internal.account.CustomerApiKeyService;
import com.tansoflow.tansocore.service.internal.account.CustomerService;
import com.tansoflow.tansocore.service.internal.account.KeyBudgetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Console-facing management of a customer's API keys and their spend budgets.
 *
 * The equivalent client endpoints sit on the /api/v1/client/** chain, which
 * only authenticates API keys — a console JWT cannot reach them at all. So the
 * operator had no way to issue a customer key or cap what it spends without
 * dropping to curl with a tenant key. This is the admin mirror; it takes the
 * internal customer id the console already holds and delegates to the same
 * services the client endpoints use.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tanso/customers/{customerId}/keys")
@PreAuthorize("hasRole('TANSO_UI')")
@Tag(name = "Customer API Keys (Admin)",
        description = "Issue, rotate, revoke, and budget a customer's API keys from the console")
@ConditionalOnProperty(name = "app.modules.monetization.enabled", havingValue = "true", matchIfMissing = true)
public class CustomerApiKeyAdminController {

    private final CustomerApiKeyService customerApiKeyService;
    private final KeyBudgetService keyBudgetService;
    private final CustomerService customerService;

    @GetMapping
    @Operation(summary = "List a customer's API keys (hints only)", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<List<CustomerApiKeyDto>>> listKeys(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String customerId) {
        List<CustomerApiKeyDto> keys = customerApiKeyService.listKeys(
                userContext.getAccountId(), referenceOf(userContext, customerId));
        return ResponseEntity.ok(ApiResponse.<List<CustomerApiKeyDto>>builder().data(keys).success(true).build());
    }

    @PostMapping
    @Operation(summary = "Issue a customer-scoped API key",
            description = "The plaintext key is returned exactly once. Scopes: 'read' and 'purchase'.",
            security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<CustomerApiKeyDto>> createKey(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String customerId,
            @Valid @RequestBody(required = false) CreateCustomerApiKeyRequest request) {
        CustomerApiKeyDto created = customerApiKeyService.createKey(
                userContext.getAccountId(), referenceOf(userContext, customerId),
                request != null ? request.getScopes() : null);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<CustomerApiKeyDto>builder().data(created).success(true).build());
    }

    @PostMapping("/{keyId}/rotate")
    @Operation(summary = "Rotate one key", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<CustomerApiKeyDto>> rotateKey(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String customerId,
            @PathVariable String keyId) {
        CustomerApiKeyDto rotated = customerApiKeyService.rotateKey(
                userContext.getAccountId(), referenceOf(userContext, customerId), keyId);
        return ResponseEntity.ok(ApiResponse.<CustomerApiKeyDto>builder().data(rotated).success(true).build());
    }

    @DeleteMapping("/{keyId}")
    @Operation(summary = "Revoke a key", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<Void>> revokeKey(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String customerId,
            @PathVariable String keyId) {
        customerApiKeyService.revokeKey(
                userContext.getAccountId(), referenceOf(userContext, customerId), keyId);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).build());
    }

    @GetMapping("/{keyId}/budget")
    @Operation(summary = "Read one key's spend budget", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<KeyBudgetDto>> getBudget(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String customerId,
            @PathVariable String keyId) {
        KeyBudgetDto budget = keyBudgetService.getBudget(
                userContext.getAccountId(), referenceOf(userContext, customerId), keyId);
        return ResponseEntity.ok(ApiResponse.<KeyBudgetDto>builder().data(budget).success(true).build());
    }

    @PutMapping("/{keyId}/budget")
    @Operation(summary = "Set one key's spend budget",
            description = "Credits and money are capped independently over a rolling window; "
                    + "omit an axis to leave it unlimited.",
            security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<KeyBudgetDto>> setBudget(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String customerId,
            @PathVariable String keyId,
            @Valid @RequestBody UpdateKeyBudgetRequest request) {
        KeyBudgetDto budget = keyBudgetService.setBudget(
                userContext.getAccountId(), referenceOf(userContext, customerId), keyId, request);
        return ResponseEntity.ok(ApiResponse.<KeyBudgetDto>builder().data(budget).success(true).build());
    }

    @DeleteMapping("/{keyId}/budget")
    @Operation(summary = "Clear one key's spend budget", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<Void>> clearBudget(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String customerId,
            @PathVariable String keyId) {
        keyBudgetService.clearBudget(
                userContext.getAccountId(), referenceOf(userContext, customerId), keyId);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).build());
    }

    /**
     * The console works in internal customer ids; the services key off the
     * customer's reference. Resolving through validateAndRetrieveCustomer also
     * enforces that the customer belongs to the caller's account.
     */
    private String referenceOf(UserContext userContext, String customerId) {
        Customer customer = customerService.validateAndRetrieveCustomer(customerId, userContext.getAccountId());
        String reference = customer.getExternalClientCustomerId();
        if (reference == null || reference.isBlank()) {
            // A customer-scoped key is pinned to its customer by reference, so
            // there is nothing to pin it to yet. Say that, rather than failing
            // later in a lookup with a message about a customer that "isn't found".
            throw new IllegalArgumentException(
                    "This customer has no Reference ID. Add one before issuing customer-scoped keys.");
        }
        return reference;
    }
}
