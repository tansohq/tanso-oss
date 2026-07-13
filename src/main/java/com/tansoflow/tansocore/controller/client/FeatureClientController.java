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
package com.tansoflow.tansocore.controller.client;

import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.model.feature.FeatureDto;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.model.response.PaginatedResponse;
import com.tansoflow.tansocore.service.internal.monetization.FeatureService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/client/features")
@PreAuthorize("hasRole('CLIENT')")
@Tag(name = "Client Feature", description = "Feature catalog operations for client applications")
public class FeatureClientController {
    private final FeatureService featureService;

    @GetMapping()
    @Operation(summary = "List all features", description = "Retrieves all features defined for the authenticated account", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved features"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content)
    })
    public ResponseEntity<ApiResponse<PaginatedResponse<FeatureDto>>> getFeatures(
            @Parameter(description = "Authenticated user context") @AuthenticationPrincipal UserContext userContext,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        List<FeatureDto> allFeatures = featureService.getFeatures(userContext.getAccountId());

        int total = allFeatures.size();
        int fromIndex = Math.min(offset, total);
        int toIndex = Math.min(offset + limit, total);
        List<FeatureDto> page = allFeatures.subList(fromIndex, toIndex);

        PaginatedResponse<FeatureDto> paginatedResponse = PaginatedResponse.<FeatureDto>builder()
                .items(page)
                .pagination(PaginatedResponse.PaginationMeta.builder()
                        .total(total)
                        .limit(limit)
                        .offset(offset)
                        .hasMore(toIndex < total)
                        .build())
                .build();

        ApiResponse<PaginatedResponse<FeatureDto>> apiResponse = ApiResponse.<PaginatedResponse<FeatureDto>>builder()
                .success(true)
                .data(paginatedResponse)
                .build();

        return ResponseEntity.ok(apiResponse);
    }

    @GetMapping("/{featureKey}")
    @Operation(summary = "Get feature by key", description = "Retrieves a single feature by its unique key", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved feature"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Feature not found", content = @Content)
    })
    public ResponseEntity<ApiResponse<FeatureDto>> getFeatureByKey(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable("featureKey") String featureKey) {
        FeatureDto feature = featureService.getFeatureByKey(userContext.getAccountId(), featureKey);

        ApiResponse<FeatureDto> apiResponse = ApiResponse.<FeatureDto>builder()
                .data(feature)
                .success(true)
                .build();

        return ResponseEntity.ok(apiResponse);
    }
}
