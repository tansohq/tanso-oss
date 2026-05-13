package com.tansoflow.tansocore.controller.tanso.monetization;

import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.model.monetization.PlanFeatureRuleDto;
import com.tansoflow.tansocore.model.monetization.request.PlanFeatureLinkedDiffRequest;
import com.tansoflow.tansocore.model.monetization.request.PlanFeatureRuleRequest;
import com.tansoflow.tansocore.model.monetization.response.PlanFeatureLinkedDiffResponse;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.service.internal.monetization.PlanFeatureRuleService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/monetization/rules")
@PreAuthorize("hasRole('TANSO_UI')")
@Tag(name = "Monetization Rules", description = "Operations for managing monetization rules (linking plans and features)")
public class MonetizationRulesController {
    private final PlanFeatureRuleService planFeatureRuleService;

    @PostMapping("/plan-features")
    @Operation(summary = "Create plan-feature rule", description = "Links a feature to a plan with specific rules", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Successfully created a new plan feature rule"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad request", content = @Content)})
    public ResponseEntity<ApiResponse<PlanFeatureRuleDto>> postPlanFeatureRule(@AuthenticationPrincipal UserContext userContext,
                                                                               @Valid @RequestBody PlanFeatureRuleRequest request) {
        PlanFeatureRuleDto planFeatureRuleDto = planFeatureRuleService.createPlanFeatureRule(userContext.getAccountId(), request);
        ApiResponse<PlanFeatureRuleDto> apiResponse = ApiResponse.<PlanFeatureRuleDto>builder()
                .success(true)
                .data(planFeatureRuleDto)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(apiResponse);
    }

    @DeleteMapping("/plan-features/{planUuid}/{featureUuid}")
    @Operation(summary = "Delete plan-feature rule", description = "Unlinks a feature from a plan", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully deleted a plan feature rule"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad request", content = @Content)})
    public ResponseEntity<ApiResponse<Void>> deletePlanFeatureRule(@AuthenticationPrincipal UserContext userContext,
                                                                   @PathVariable String planUuid,
                                                                   @PathVariable String featureUuid) {
        planFeatureRuleService.deletePlanFeatureRule(userContext.getAccountId(), featureUuid, planUuid);
        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder().success(true).build();
        return ResponseEntity.status(HttpStatus.OK).body(apiResponse);
    }

    @GetMapping("/plan-features/{planUuid}/{featureUuid}")
    @Operation(summary = "Get plan-feature rule", description = "Retrieves rules for a specific plan-feature link", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved plan feature rule"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad request", content = @Content)})
    public ResponseEntity<ApiResponse<PlanFeatureRuleDto>> getPlanFeatureRule(@AuthenticationPrincipal UserContext userContext,
                                                                              @PathVariable String planUuid,
                                                                              @PathVariable String featureUuid) {

        PlanFeatureRuleDto planFeatureRuleDto = planFeatureRuleService.getPlanFeatureRule(userContext.getAccountId(), featureUuid, planUuid);
        ApiResponse<PlanFeatureRuleDto> apiResponse = ApiResponse.<PlanFeatureRuleDto>builder()
                .success(true)
                .data(planFeatureRuleDto)
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(apiResponse);
    }

    @PatchMapping("/plan-features")
    @Operation(summary = "Update plan-feature rule", description = "Updates rules for a plan-feature link", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully updated a plan feature rule"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad request", content = @Content)})
    public ResponseEntity<ApiResponse<PlanFeatureRuleDto>> updatePlanFeatureRule(@AuthenticationPrincipal UserContext userContext,
                                                             @Valid @RequestBody PlanFeatureRuleRequest request) {

        PlanFeatureRuleDto planFeatureRuleDto = planFeatureRuleService.updatePlanFeatureRule(userContext.getAccountId(), request);
        ApiResponse<PlanFeatureRuleDto> apiResponse = ApiResponse.<PlanFeatureRuleDto>builder()
                .success(true)
                .data(planFeatureRuleDto)
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(apiResponse);
    }

    @PatchMapping("plan-features/diff/{planUuid}")
    @Operation(summary = "Sync plan-feature links", description = "Links/Unlinks features for a plan based on a provided list (diff)", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully updated plan feature links"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad request", content = @Content)})
    public ResponseEntity<ApiResponse<PlanFeatureLinkedDiffResponse>> updatePlanFeatureRuleDiff(@AuthenticationPrincipal UserContext userContext,
                                                                                                @PathVariable String planUuid,
                                                                                                @Valid @RequestBody PlanFeatureLinkedDiffRequest request) {
        PlanFeatureLinkedDiffResponse response = planFeatureRuleService.addRemovePlanFeatureRulesByDiff(userContext.getAccountId(), request, planUuid);
        ApiResponse<PlanFeatureLinkedDiffResponse> apiResponse = ApiResponse.<PlanFeatureLinkedDiffResponse>builder()
                .success(true)
                .data(response)
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(apiResponse);
    }

}