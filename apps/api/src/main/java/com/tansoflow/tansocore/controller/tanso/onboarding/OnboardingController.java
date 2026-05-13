package com.tansoflow.tansocore.controller.tanso.onboarding;

import com.tansoflow.tansocore.model.auth.response.JwtResponse;
import com.tansoflow.tansocore.model.customer.request.CustomerRequest;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.service.internal.onboarding.OnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/public/v1")
@Tag(name = "Signup", description = "Public signup")
public class OnboardingController {
    private final OnboardingService onboardingService;

    @PostMapping("/signup")
    @Operation(summary = "Self-serve signup", description = "Creates a new organization, user, and API key")
    public ResponseEntity<ApiResponse<JwtResponse>> signup(@RequestBody SignupRequest request) {
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

    @Data
    public static class SignupRequest {
        private CustomerRequest customerDetails;
        private String organizationName;
        private String password;
        private String planId;
    }
}
