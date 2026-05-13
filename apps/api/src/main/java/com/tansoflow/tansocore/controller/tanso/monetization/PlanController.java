package com.tansoflow.tansocore.controller.tanso.monetization;

import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.model.monetization.PlanFeatureLinkedDto;
import com.tansoflow.tansocore.model.monetization.request.UuidListRequest;
import com.tansoflow.tansocore.model.plan.PlanDto;
import com.tansoflow.tansocore.model.plan.request.PlanRequest;
import com.tansoflow.tansocore.model.plan.response.PlanRevenueResponse;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.service.internal.monetization.PlanRevenueService;
import com.tansoflow.tansocore.service.internal.monetization.PlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/monetization/plans")
@PreAuthorize("hasRole('TANSO_UI')")
@Tag(name = "Plan", description = "Plan management operations")
public class PlanController {
    private final PlanService planService;
    private final PlanRevenueService planRevenueService;

    @GetMapping
    @Operation(summary = "List plans", description = "Retrieves all plans for the authenticated account", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved plans"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content)})
    public ResponseEntity<ApiResponse<List<PlanDto>>> getPlans(@AuthenticationPrincipal UserContext userContext) {
        List<PlanDto> planDtos = planService.getPlans(userContext.getAccountId());
        ApiResponse<List<PlanDto>> apiResponse = ApiResponse.<List<PlanDto>>builder()
                .success(true)
                .data(planDtos)
                .build();

        return ResponseEntity.status(HttpStatus.OK).body(apiResponse);
    }

    @PatchMapping("/{uuid}")
    @Operation(summary = "Update plan", description = "Updates an existing plan record", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully updated the plan"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Plan not found", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content)})
    public ResponseEntity<ApiResponse<PlanDto>> updatePlan(@AuthenticationPrincipal UserContext userContext,
                                                  @PathVariable String uuid,
                                                  @Valid @RequestBody PlanRequest planRequest) {
        PlanDto planDto = planService.updatePlan(userContext.getAccountId(), uuid, planRequest);
        ApiResponse<PlanDto> apiResponse = ApiResponse.<PlanDto>builder()
                .success(true)
                .data(planDto)
                .build();

        return ResponseEntity.status(HttpStatus.OK).body(apiResponse);
    }

    @PostMapping
    @Operation(summary = "Create plan", description = "Creates a new plan for the authenticated account", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Successfully created a new plan"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content)})
    public ResponseEntity<ApiResponse<PlanDto>> postPlan(@AuthenticationPrincipal UserContext userContext,
                                                @Valid @RequestBody PlanRequest planRequest) {
        PlanDto planDto = planService.createPlans(userContext.getAccountId(), planRequest);
        ApiResponse<PlanDto> apiResponse = ApiResponse.<PlanDto>builder()
                .success(true)
                .data(planDto)
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(apiResponse);
    }

    @DeleteMapping("/{planId}")
    @Operation(summary = "Delete plan", description = "Deletes a specific plan by ID", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully deleted plan"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Plan not found", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> deletePlans(@PathVariable(name = "planId") String planId) {
        planService.deletePlan(planId);
        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder().success(true).build();
        return ResponseEntity.status(HttpStatus.OK).body(apiResponse);
    }

    @DeleteMapping
    @Operation(summary = "Bulk delete plans", description = "Deletes a set of existing plans by their IDs", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully deleted plans"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> deletePlans(@Valid @RequestBody UuidListRequest uuids) {
        planService.deletePlans(uuids);
        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder().success(true).build();
        return ResponseEntity.status(HttpStatus.OK).body(apiResponse);
    }

    @GetMapping("/{planUuid}/features")
    @Operation(summary = "Get linked features", description = "Retrieves all features linked to a specific plan", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved linked features"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Plan not found", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content)})
    public ResponseEntity<ApiResponse<PlanFeatureLinkedDto>> getLinkedFeatures(@AuthenticationPrincipal UserContext userContext,
                                                                               @PathVariable String planUuid) {
        PlanFeatureLinkedDto planFeatureLinkedDto = planService.retrievePlanFeatureLinkByPlanUuid(planUuid, userContext.getAccountId());
        ApiResponse<PlanFeatureLinkedDto> apiResponse = ApiResponse.<PlanFeatureLinkedDto>builder()
                .success(true)
                .data(planFeatureLinkedDto)
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(apiResponse);
    }

    @GetMapping("/{planId}/revenue")
    @Operation(summary = "Get plan revenue breakdown by feature and subscription",
            description = "Returns revenue and usage for each feature tied to the plan, with a per-subscription breakdown",
            security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved plan revenue"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Plan not found", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content)})
    public ResponseEntity<ApiResponse<PlanRevenueResponse>> getPlanRevenue(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable UUID planId,
            @RequestParam Instant periodStart,
            @RequestParam Instant periodEnd,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID subscriptionId) {
        PlanRevenueResponse response = planRevenueService.getPlanRevenue(
                userContext.getAccountId(), planId, periodStart, periodEnd, page, size, subscriptionId);
        ApiResponse<PlanRevenueResponse> apiResponse = ApiResponse.<PlanRevenueResponse>builder()
                .success(true)
                .data(response)
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(apiResponse);
    }

}
