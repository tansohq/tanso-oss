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
import com.tansoflow.tansocore.model.apikey.CustomerApiKeyDto;
import com.tansoflow.tansocore.model.apikey.KeyBudgetDto;
import com.tansoflow.tansocore.model.apikey.request.CreateCustomerApiKeyRequest;
import com.tansoflow.tansocore.model.apikey.request.UpdateKeyBudgetRequest;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.service.internal.account.CustomerApiKeyService;
import com.tansoflow.tansocore.service.internal.account.KeyBudgetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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
 * Tenant-facing management of customer-scoped (ck_) API keys. Deliberately
 * NOT opened to ROLE_CUSTOMER: a customer key must not mint or revoke keys.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/client/customers/{customerReferenceId}/keys")
@PreAuthorize("hasRole('CLIENT')")
@Tag(name = "Customer API Keys", description = "Issue and manage customer-scoped API keys for end-customer agents")
@ConditionalOnProperty(name = "app.modules.monetization.enabled", havingValue = "true", matchIfMissing = true)
public class CustomerApiKeyClientController {

    private final CustomerApiKeyService customerApiKeyService;
    private final KeyBudgetService keyBudgetService;
    private final CustomerAccessGuard customerAccessGuard;

    @PostMapping
    @Operation(summary = "Create a customer-scoped API key",
            description = "Issues a ck_ key pinned to this customer. The plaintext key is returned exactly once. "
                    + "Scopes: 'read' (balances, entitlements, usage) and 'purchase' (actions that spend money). "
                    + "Defaults to read-only when no scopes are given.",
            security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<CustomerApiKeyDto>> createKey(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String customerReferenceId,
            @Valid @RequestBody(required = false) CreateCustomerApiKeyRequest request) {
        List<String> scopes = request != null ? request.getScopes() : null;
        CustomerApiKeyDto created = customerApiKeyService.createKey(
                userContext.getAccountId(), customerReferenceId, scopes);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<CustomerApiKeyDto>builder().data(created).success(true).build());
    }

    @GetMapping
    @Operation(summary = "List a customer's API keys (hints only)", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<List<CustomerApiKeyDto>>> listKeys(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String customerReferenceId) {
        List<CustomerApiKeyDto> keys = customerApiKeyService.listKeys(
                userContext.getAccountId(), customerReferenceId);
        return ResponseEntity.ok(ApiResponse.<List<CustomerApiKeyDto>>builder().data(keys).success(true).build());
    }

    @PostMapping("/{keyId}/rotate")
    @Operation(summary = "Rotate one key",
            description = "Deactivates this key only and issues a replacement with the same scopes. "
                    + "Sibling keys are untouched.", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<CustomerApiKeyDto>> rotateKey(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String customerReferenceId,
            @PathVariable String keyId) {
        CustomerApiKeyDto rotated = customerApiKeyService.rotateKey(
                userContext.getAccountId(), customerReferenceId, keyId);
        return ResponseEntity.ok(ApiResponse.<CustomerApiKeyDto>builder().data(rotated).success(true).build());
    }

    @GetMapping("/{keyId}/budget")
    @PreAuthorize("hasAnyRole('CLIENT','CUSTOMER')")
    @Operation(summary = "Read one key's spend budget",
            description = "Limits, spend so far in the current window, and when the window resets. "
                    + "A customer key may read its own budget so an agent can decide whether to "
                    + "spend before it gets rejected.",
            security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<KeyBudgetDto>> getBudget(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String customerReferenceId,
            @PathVariable String keyId) {
        String ref = customerAccessGuard.resolveCustomerRef(userContext, customerReferenceId);
        if (userContext.isCustomerScoped() && !keyId.equals(String.valueOf(userContext.getApiKeyId()))) {
            throw new AccessDeniedException("A customer key may only read its own budget");
        }
        KeyBudgetDto budget = keyBudgetService.getBudget(userContext.getAccountId(), ref, keyId);
        return ResponseEntity.ok(ApiResponse.<KeyBudgetDto>builder().data(budget).success(true).build());
    }

    @PutMapping("/{keyId}/budget")
    @Operation(summary = "Set one key's spend budget",
            description = "Bounds what a single agent or team member may consume. Credits and money "
                    + "are capped independently over a rolling window; omit an axis to leave it "
                    + "unlimited. Deliberately NOT opened to ROLE_CUSTOMER — a key must not raise "
                    + "its own ceiling. Changing the period restarts the window.",
            security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<KeyBudgetDto>> setBudget(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String customerReferenceId,
            @PathVariable String keyId,
            @Valid @RequestBody UpdateKeyBudgetRequest request) {
        KeyBudgetDto budget = keyBudgetService.setBudget(
                userContext.getAccountId(), customerReferenceId, keyId, request);
        return ResponseEntity.ok(ApiResponse.<KeyBudgetDto>builder().data(budget).success(true).build());
    }

    @DeleteMapping("/{keyId}/budget")
    @Operation(summary = "Clear one key's spend budget", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<Void>> clearBudget(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String customerReferenceId,
            @PathVariable String keyId) {
        keyBudgetService.clearBudget(userContext.getAccountId(), customerReferenceId, keyId);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).build());
    }

    @DeleteMapping("/{keyId}")
    @Operation(summary = "Revoke a key", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<Void>> revokeKey(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String customerReferenceId,
            @PathVariable String keyId) {
        customerApiKeyService.revokeKey(userContext.getAccountId(), customerReferenceId, keyId);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).build());
    }
}
