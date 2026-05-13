package com.tansoflow.tansocore.controller.tanso.onboarding;

import com.tansoflow.tansocore.model.auth.response.JwtResponse;
import com.tansoflow.tansocore.model.onboarding.request.SignupRequest;
import com.tansoflow.tansocore.model.plan.PlanDto;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.property.AppProperty;
import com.tansoflow.tansocore.service.internal.monetization.PlanService;
import com.tansoflow.tansocore.service.internal.onboarding.OnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/public/v1")
@Tag(name = "Onboarding", description = "Public onboarding and signup operations")
public class OnboardingController {
    private final OnboardingService onboardingService;
    private final PlanService planService;
    private final AppProperty appProperty;

    @GetMapping("/onboarding/plans")
    @Operation(summary = "List available plans", description = "Returns plans available for new signups")
    public ResponseEntity<ApiResponse<List<PlanDto>>> getOnboardingPlans() {
        List<PlanDto> plans = planService.getPlans(appProperty.getMasterAccountId());
        return ResponseEntity.ok(ApiResponse.<List<PlanDto>>builder()
                .success(true)
                .data(plans)
                .build());
    }

    @PostMapping("/signup")
    @Operation(summary = "Self-serve signup", description = "Creates a new organization, user, and subscription")
    public ResponseEntity<ApiResponse<JwtResponse>> signup(@Valid @RequestBody SignupRequest request) {
        log.info("Received signup request");
        JwtResponse jwtResponse = onboardingService.onboardNewOrganization(
                request.getCustomerDetails(),
                request.getOrganizationName(),
                request.getPassword(),
                request.getPlanId()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<JwtResponse>builder()
                        .success(true)
                        .data(jwtResponse)
                        .build());
    }
}
