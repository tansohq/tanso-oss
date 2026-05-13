package com.tansoflow.tansocore.controller.client;

import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.model.client.ClientPlanFeatureLinkedDto;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.model.response.PaginatedResponse;
import com.tansoflow.tansocore.service.client.ClientPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/client/plans")
@PreAuthorize("hasRole('CLIENT')")
@Tag(name = "Client Plan", description = "Plan operations for client applications")
public class PlanClientController {
    private final ClientPlanService clientPlanService;

    @GetMapping()
    @Operation(summary = "Get client plans with pricing", description = "Retrieves active plans with feature pricing details for the authenticated account", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved plan features with pricing"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content)
    })
    public ResponseEntity<ApiResponse<PaginatedResponse<ClientPlanFeatureLinkedDto>>> getClientPlansFeatures(
            @Parameter(description = "Authenticated user context") @AuthenticationPrincipal UserContext userContext,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        List<ClientPlanFeatureLinkedDto> allPlans = clientPlanService.retrieveActivePlansWithPricing(userContext.getAccountId());

        int total = allPlans.size();
        int fromIndex = Math.min(offset, total);
        int toIndex = Math.min(offset + limit, total);
        List<ClientPlanFeatureLinkedDto> page = allPlans.subList(fromIndex, toIndex);

        PaginatedResponse<ClientPlanFeatureLinkedDto> paginatedResponse = PaginatedResponse.<ClientPlanFeatureLinkedDto>builder()
                .items(page)
                .pagination(PaginatedResponse.PaginationMeta.builder()
                        .total(total)
                        .limit(limit)
                        .offset(offset)
                        .hasMore(toIndex < total)
                        .build())
                .build();

        ApiResponse<PaginatedResponse<ClientPlanFeatureLinkedDto>> apiResponse = ApiResponse.<PaginatedResponse<ClientPlanFeatureLinkedDto>>builder()
                .success(true)
                .data(paginatedResponse)
                .build();

        return ResponseEntity.ok(apiResponse);
    }

}
