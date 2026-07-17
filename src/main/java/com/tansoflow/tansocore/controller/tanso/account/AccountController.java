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

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.entity.AccountApiKey;
import com.tansoflow.tansocore.model.account.request.ChangePasswordRequest;
import com.tansoflow.tansocore.model.account.response.AccountApiKeyResponse;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.model.response.Error;
import com.tansoflow.tansocore.service.internal.account.AccountService;
import com.tansoflow.tansocore.service.internal.account.PasswordService;
import com.tansoflow.tansocore.service.internal.audit.AuditHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/account")
@PreAuthorize("hasRole('TANSO_UI')")
@Tag(name = "Account", description = "Account and authentication operations")
public class AccountController {
    private final AccountService accountService;
    private final PasswordService passwordService;
    private final AuditHelper auditHelper;
    private final Resend resend;

    @GetMapping("/api-key")
    @Operation(summary = "Get account API key", description = "Returns a masked account API key")
    public ResponseEntity<ApiResponse<AccountApiKeyResponse>> getAccountApiKey(@AuthenticationPrincipal UserContext userContext) {
        AccountApiKey apiKey = accountService.retrieveFirstApiKey(userContext.getAccountId());

        AccountApiKeyResponse response = AccountApiKeyResponse.builder()
                .apiKey(maskApiKey(apiKey.getKeyValue()))
                .keyType(apiKey.getKeyType())
                .build();

        ApiResponse<AccountApiKeyResponse> apiResponse = ApiResponse.<AccountApiKeyResponse>builder()
                .success(true)
                .data(response)
                .build();

        return ResponseEntity.ok(apiResponse);
    }

    @PostMapping("/api-key")
    @Operation(summary = "Rotate account API key", description = "Generates a new API key, invalidates previous keys, and returns the new secret once")
    public ResponseEntity<ApiResponse<AccountApiKeyResponse>> rotateAccountApiKey(@AuthenticationPrincipal UserContext userContext) {
        log.info("API key rotation requested for account {}", userContext.getAccountId());

        AccountApiKey newKey = accountService.rotateApiKey(userContext.getAccountId());

        auditHelper.audit("API_KEY_ROTATED",
                UUID.fromString(userContext.getUserId()),
                UUID.fromString(userContext.getAccountId()),
                "ACCOUNT", userContext.getAccountId(), null);

        AccountApiKeyResponse response = AccountApiKeyResponse.builder()
                .apiKey(newKey.getKeyValue())
                .keyType("secret")
                .build();

        return ResponseEntity.ok(ApiResponse.<AccountApiKeyResponse>builder()
                .success(true)
                .data(response)
                .build());
    }

    private static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return apiKey;
        }

        int prefixLength = apiKey.startsWith("sk_live_") || apiKey.startsWith("sk_test_") ? 8 : 4;
        int suffixLength = 4;
        if (apiKey.length() <= prefixLength + suffixLength) {
            return "*".repeat(apiKey.length());
        }

        return apiKey.substring(0, prefixLength)
                + "************"
                + apiKey.substring(apiKey.length() - suffixLength);
    }

    @PostMapping("/feature-request")
    @Operation(summary = "Request a feature", description = "Sends an internal email notifying the team of a feature interest")
    public ResponseEntity<ApiResponse<Void>> requestFeature(
            @AuthenticationPrincipal UserContext userContext,
            @RequestBody java.util.Map<String, String> request) {
        String feature = request.getOrDefault("feature", "unknown");
        String userEmail = userContext.getEmail() != null ? userContext.getEmail() : "unknown";
        String accountId = userContext.getAccountId();

        log.info("Feature request: {} (account {})", feature, accountId);

        try {
            // CHANGE ME: replace with your own sender/recipient addresses (or wire to config)
            CreateEmailOptions email = CreateEmailOptions.builder()
                    .from("Notifications <notifications@your-domain.com>")
                    .to("admin@your-domain.com")
                    .subject("Feature Request: " + org.springframework.web.util.HtmlUtils.htmlEscape(feature))
                    .html("<p><strong>" + org.springframework.web.util.HtmlUtils.htmlEscape(feature) + "</strong> requested by:</p>"
                            + "<ul>"
                            + "<li>Email: " + org.springframework.web.util.HtmlUtils.htmlEscape(userEmail) + "</li>"
                            + "<li>Account ID: " + org.springframework.web.util.HtmlUtils.htmlEscape(accountId) + "</li>"
                            + "</ul>")
                    .build();
            resend.emails().send(email);
        } catch (ResendException e) {
            log.error("Failed to send feature request email: {}", e.getMessage(), e);
        }

        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).build());
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change password", description = "Changes the authenticated user's password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal UserContext userContext,
            @Valid @RequestBody ChangePasswordRequest request) {
        log.info("Password change requested for user: {}", userContext.getUserId());

        try {
            passwordService.changePassword(
                    userContext.getUserId(),
                    userContext.getEmail(),
                    request.getCurrentPassword(),
                    request.getNewPassword()
            );

            auditHelper.audit("PASSWORD_CHANGED",
                    UUID.fromString(userContext.getUserId()),
                    UUID.fromString(userContext.getAccountId()));

            return ResponseEntity.ok(ApiResponse.<Void>builder()
                    .success(true)
                    .build());
        } catch (IllegalArgumentException e) {
            log.warn("Password change failed for user {}: {}", userContext.getUserId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<Void>builder()
                            .success(false)
                            .error(new Error(e.getMessage()))
                            .build());
        }
    }
}
