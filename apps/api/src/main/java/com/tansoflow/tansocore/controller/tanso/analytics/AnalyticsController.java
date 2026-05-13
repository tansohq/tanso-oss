package com.tansoflow.tansocore.controller.tanso.analytics;

import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.model.analytics.AnalyticsResponseDto;
import com.tansoflow.tansocore.model.analytics.ModelsAnalyticsResponseDto;
import com.tansoflow.tansocore.model.analytics.RevenueBridgeResponseDto;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.service.internal.analytics.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/analytics")
@PreAuthorize("hasRole('TANSO_UI')")
@Tag(name = "Analytics", description = "Portfolio analytics and margin reporting")
public class AnalyticsController {
    private final AnalyticsService analyticsService;

    @GetMapping("/portfolio")
    @Operation(summary = "Get portfolio analytics", description = "Retrieves margin analytics for all customers with active subscriptions", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved analytics"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content)})
    public ResponseEntity<ApiResponse<AnalyticsResponseDto>> getPortfolioAnalytics(
            @AuthenticationPrincipal UserContext userContext) {
        AnalyticsResponseDto analytics = analyticsService.getPortfolioAnalytics(userContext.getAccountId());
        ApiResponse<AnalyticsResponseDto> apiResponse = ApiResponse.<AnalyticsResponseDto>builder()
                .success(true)
                .data(analytics)
                .build();

        return ResponseEntity.status(HttpStatus.OK).body(apiResponse);
    }

    @GetMapping("/revenue-bridge")
    @Operation(summary = "Get revenue bridge", description = "Retrieves period-over-period revenue waterfall from paid invoices", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved revenue bridge"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content)})
    public ResponseEntity<ApiResponse<RevenueBridgeResponseDto>> getRevenueBridge(
            @AuthenticationPrincipal UserContext userContext,
            @RequestParam(defaultValue = "6") int periods) {
        RevenueBridgeResponseDto bridge = analyticsService.getRevenueBridge(userContext.getAccountId(), periods);
        ApiResponse<RevenueBridgeResponseDto> apiResponse = ApiResponse.<RevenueBridgeResponseDto>builder()
                .success(true)
                .data(bridge)
                .build();

        return ResponseEntity.status(HttpStatus.OK).body(apiResponse);
    }

    @GetMapping("/models")
    @Operation(summary = "Get model analytics", description = "Retrieves cost and usage analytics grouped by LLM model", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved model analytics"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content)})
    public ResponseEntity<ApiResponse<ModelsAnalyticsResponseDto>> getModelAnalytics(
            @AuthenticationPrincipal UserContext userContext) {
        ModelsAnalyticsResponseDto analytics = analyticsService.getModelAnalytics(userContext.getAccountId());
        ApiResponse<ModelsAnalyticsResponseDto> apiResponse = ApiResponse.<ModelsAnalyticsResponseDto>builder()
                .success(true)
                .data(analytics)
                .build();

        return ResponseEntity.status(HttpStatus.OK).body(apiResponse);
    }
}
