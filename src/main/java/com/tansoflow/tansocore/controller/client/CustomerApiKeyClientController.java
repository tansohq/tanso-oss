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
import com.tansoflow.tansocore.model.apikey.CustomerApiKeyDto;
import com.tansoflow.tansocore.model.apikey.request.CreateCustomerApiKeyRequest;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.service.internal.account.CustomerApiKeyService;
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
public class CustomerApiKeyClientController {

    private final CustomerApiKeyService customerApiKeyService;

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
