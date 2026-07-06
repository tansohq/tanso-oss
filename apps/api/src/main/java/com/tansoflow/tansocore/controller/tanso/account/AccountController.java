package com.tansoflow.tansocore.controller.tanso.account;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.entity.AccountApiKey;
import com.tansoflow.tansocore.model.account.request.ChangePasswordRequest;
import com.tansoflow.tansocore.model.account.request.SubscribeToPlanRequest;
import com.tansoflow.tansocore.model.account.response.AccountApiKeyResponse;
import com.tansoflow.tansocore.model.account.response.SubscriptionStatusResponse;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.model.response.Error;
import org.springframework.beans.factory.annotation.Value;
import com.tansoflow.tansocore.service.internal.account.AccountService;
import com.tansoflow.tansocore.service.internal.account.PasswordService;
import com.tansoflow.tansocore.service.internal.account.UserService;
import com.tansoflow.tansocore.service.internal.audit.AuditHelper;
import com.tansoflow.tansocore.service.internal.onboarding.OnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/account")
@Tag(name = "Account", description = "Account and authentication operations")
public class AccountController {
    private final AccountService accountService;
    private final OnboardingService onboardingService;
    private final PasswordService passwordService;
    private final UserService userService;
    private final AuditHelper auditHelper;
    private final Resend resend;
    @Value("${app.admin-email:admin@localhost}")
    private String adminEmail;

    @GetMapping("/api-key")
    @Operation(summary = "Get account API key", description = "Returns a masked view of the account's API key. The full key is only shown once, at create/rotate time.")
    public ResponseEntity<ApiResponse<AccountApiKeyResponse>> getAccountApiKey(@AuthenticationPrincipal UserContext userContext) {
        AccountApiKey apiKey = accountService.retrieveFirstApiKey(userContext.getAccountId());

        // The full key is stored only as a hash and cannot be recovered; return the display prefix + a mask.
        String prefix = apiKey.getKeyPrefix() != null ? apiKey.getKeyPrefix() : "";
        AccountApiKeyResponse response = AccountApiKeyResponse.builder()
                .apiKey(prefix + "****************")
                .keyType("secret") // Defaulting to secret as per current usage
                .build();

        ApiResponse<AccountApiKeyResponse> apiResponse = ApiResponse.<AccountApiKeyResponse>builder()
                .success(true)
                .data(response)
                .build();

        return ResponseEntity.ok(apiResponse);
    }

    @PostMapping("/api-key")
    @Operation(summary = "Rotate account API key", description = "Generates a new API key, invalidating the previous one")
    public ResponseEntity<ApiResponse<AccountApiKeyResponse>> rotateAccountApiKey(@AuthenticationPrincipal UserContext userContext) {
        log.info("API key rotation requested for account {}", userContext.getAccountId());

        AccountApiKey newKey = accountService.rotateApiKey(userContext.getAccountId());

        auditHelper.audit("API_KEY_ROTATED",
                UUID.fromString(userContext.getUserId()),
                UUID.fromString(userContext.getAccountId()),
                "ACCOUNT", userContext.getAccountId(), null);

        // Raw key is returned exactly once here; only its hash is persisted.
        AccountApiKeyResponse response = AccountApiKeyResponse.builder()
                .apiKey(newKey.getRawKey())
                .keyType("secret")
                .build();

        return ResponseEntity.ok(ApiResponse.<AccountApiKeyResponse>builder()
                .success(true)
                .data(response)
                .build());
    }

    @GetMapping("/subscription-status")
    @Operation(summary = "Get subscription status", description = "Checks if the account has an active subscription and returns plan details")
    public ResponseEntity<ApiResponse<SubscriptionStatusResponse>> getSubscriptionStatus(
            @AuthenticationPrincipal UserContext userContext) {
        SubscriptionStatusResponse response = onboardingService.getSubscriptionStatus(userContext.getAccountId());

        return ResponseEntity.ok(ApiResponse.<SubscriptionStatusResponse>builder()
                .success(true)
                .data(response)
                .build());
    }

    @PostMapping("/subscribe")
    @Operation(summary = "Subscribe to plan", description = "Subscribes the account to a specified plan")
    public ResponseEntity<ApiResponse<Void>> subscribeToPlan(
            @AuthenticationPrincipal UserContext userContext,
            @Valid @RequestBody SubscribeToPlanRequest request) {
        log.info("Account {} subscribing to plan {}", userContext.getAccountId(), request.getPlanId());

        onboardingService.subscribeAccountToPlan(userContext.getAccountId(), request.getPlanId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<Void>builder()
                        .success(true)
                        .build());
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
            CreateEmailOptions email = CreateEmailOptions.builder()
                    .from(adminEmail)
                    .to(adminEmail)
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
