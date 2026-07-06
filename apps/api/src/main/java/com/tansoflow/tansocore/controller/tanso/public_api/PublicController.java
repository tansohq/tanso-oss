package com.tansoflow.tansocore.controller.tanso.public_api;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tansoflow.tansocore.model.account.request.UsernameAndPasswordRequest;
import com.tansoflow.tansocore.model.auth.response.JwtResponse;
import com.tansoflow.tansocore.model.exception.AuthenticationException;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.model.response.Error;
import com.tansoflow.tansocore.service.internal.account.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/public/v1")
@Tag(name = "Public", description = "Public operations")
public class PublicController {
    private final UserService userService;

    private static final int MAX_FAILED_ATTEMPTS = 5;

    // In-memory failed-login counter keyed by username, expiring 15 minutes after the last failure.
    private final Cache<String, Integer> failedLoginAttempts = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(15))
            .maximumSize(10_000)
            .build();

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticates a user and returns a JWT token")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Too many failed login attempts", content = @Content)
    })
    public ResponseEntity<ApiResponse<JwtResponse>> login(@Valid @RequestBody UsernameAndPasswordRequest request) {
        String attemptKey = request.getUsername() == null ? "" : request.getUsername().toLowerCase();

        Integer attempts = failedLoginAttempts.getIfPresent(attemptKey);
        if (attempts != null && attempts >= MAX_FAILED_ATTEMPTS) {
            log.warn("Login blocked: too many failed attempts for username");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.<JwtResponse>builder()
                            .success(false)
                            .error(new Error("Too many failed login attempts. Please try again later."))
                            .build());
        }

        try {
            JwtResponse response = userService.generateJwtTokenForUser(request);
            failedLoginAttempts.invalidate(attemptKey);

            ApiResponse<JwtResponse> apiResponse = ApiResponse.<JwtResponse>builder()
                    .success(true)
                    .data(response)
                    .build();

            return ResponseEntity.status(200).body(apiResponse);
        } catch (AuthenticationException e) {
            failedLoginAttempts.asMap().merge(attemptKey, 1, Integer::sum);
            throw e;
        }
    }
}
