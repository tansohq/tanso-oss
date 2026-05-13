package com.tansoflow.tansocore.controller.tanso.account;

import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.model.account.IntakeDataDto;
import com.tansoflow.tansocore.model.account.OnboardingProgressDto;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.service.internal.account.AccountOnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tanso/account/onboarding")
@Tag(name = "Account Onboarding", description = "Onboarding intake and progress tracking")
public class AccountOnboardingController {

    private final AccountOnboardingService accountOnboardingService;

    @PostMapping("/intake")
    @Operation(summary = "Save onboarding intake data", description = "Saves user background and goals collected during onboarding")
    public ResponseEntity<ApiResponse<Void>> saveIntakeData(
            @AuthenticationPrincipal UserContext userContext,
            @Valid @RequestBody IntakeDataDto intakeData) {

        UUID accountId = UUID.fromString(userContext.getAccountId());
        UUID actorUserId = userContext.getUserId() != null ? UUID.fromString(userContext.getUserId()) : null;

        accountOnboardingService.saveIntakeData(accountId, actorUserId, intakeData);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .build());
    }

    @GetMapping("/intake")
    @Operation(summary = "Get onboarding intake data", description = "Returns the intake data saved during onboarding")
    public ResponseEntity<ApiResponse<IntakeDataDto>> getIntakeData(
            @AuthenticationPrincipal UserContext userContext) {

        UUID userId = userContext.getUserId() != null ? UUID.fromString(userContext.getUserId()) : null;
        IntakeDataDto intakeData = userId != null
                ? accountOnboardingService.getIntakeDataByUserId(userId)
                : null;

        return ResponseEntity.ok(ApiResponse.<IntakeDataDto>builder()
                .success(true)
                .data(intakeData)
                .build());
    }

    @GetMapping("/progress")
    @Operation(summary = "Get onboarding progress", description = "Returns which onboarding steps have been completed")
    public ResponseEntity<ApiResponse<OnboardingProgressDto>> getOnboardingProgress(
            @AuthenticationPrincipal UserContext userContext) {

        UUID accountId = UUID.fromString(userContext.getAccountId());
        OnboardingProgressDto progress = accountOnboardingService.getOnboardingProgress(accountId);

        return ResponseEntity.ok(ApiResponse.<OnboardingProgressDto>builder()
                .success(true)
                .data(progress)
                .build());
    }

    @PostMapping("/progress/complete")
    @Operation(summary = "Complete an onboarding step", description = "Marks an onboarding step as completed")
    public ResponseEntity<ApiResponse<OnboardingProgressDto>> completeOnboardingStep(
            @AuthenticationPrincipal UserContext userContext,
            @Valid @RequestBody CompleteStepRequest request) {

        UUID accountId = UUID.fromString(userContext.getAccountId());
        UUID actorUserId = userContext.getUserId() != null ? UUID.fromString(userContext.getUserId()) : null;

        OnboardingProgressDto progress = accountOnboardingService.completeOnboardingStep(
                accountId, actorUserId, request.getStepKey());

        return ResponseEntity.ok(ApiResponse.<OnboardingProgressDto>builder()
                .success(true)
                .data(progress)
                .build());
    }

    @Data
    public static class CompleteStepRequest {
        @NotBlank(message = "Step key is required")
        private String stepKey;
    }
}
