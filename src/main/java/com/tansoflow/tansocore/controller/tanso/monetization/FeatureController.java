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
package com.tansoflow.tansocore.controller.tanso.monetization;

import com.tansoflow.tansocore.auth.RequiresFullPlatformMode;
import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.model.feature.FeatureDto;
import com.tansoflow.tansocore.model.feature.request.FeatureRequest;
import com.tansoflow.tansocore.model.monetization.request.UuidListRequest;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.service.internal.monetization.FeatureService;
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

import java.util.List;
import java.util.UUID;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/monetization/features")
@PreAuthorize("hasRole('TANSO_UI')")
@Tag(name = "Feature", description = "Feature management operations")
public class FeatureController {
    private final FeatureService featureService;

    @GetMapping
    @Operation(summary = "List features", description = "Retrieves all features for the authenticated account", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved features"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content)
    })
    public ResponseEntity<ApiResponse<List<FeatureDto>>> getFeatures(@AuthenticationPrincipal UserContext userContext) {
        List<FeatureDto> featureDto = featureService.getFeatures(userContext.getAccountId());

        ApiResponse<List<FeatureDto>> apiResponse = ApiResponse.<List<FeatureDto>>builder()
                .success(true)
                .data(featureDto)
                .build();

        return ResponseEntity.status(HttpStatus.OK).body(apiResponse);
    }

    @GetMapping("/{uuid}")
    @Operation(summary = "Get feature", description = "Retrieves a specific feature by its UUID", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved feature"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Feature not found", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content)
    })
    public ResponseEntity<ApiResponse<FeatureDto>> getFeature(@AuthenticationPrincipal UserContext userContext, @PathVariable UUID uuid) {
        FeatureDto featureDto = featureService.getFeature(userContext.getAccountId(), uuid);

        ApiResponse<FeatureDto> apiResponse = ApiResponse.<FeatureDto>builder()
                .success(true)
                .data(featureDto)
                .build();

        return ResponseEntity.status(HttpStatus.OK).body(apiResponse);
    }

    @PostMapping
    @Operation(summary = "Create feature", description = "Creates a new feature for the authenticated account", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Successfully created a new feature"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content)
    })
    public ResponseEntity<ApiResponse<FeatureDto>> postFeature(@AuthenticationPrincipal UserContext userContext, @Valid @RequestBody FeatureRequest featureRequest) {
        FeatureDto featureDto = featureService.createFeature(userContext.getAccountId(), featureRequest);
        ApiResponse<FeatureDto> apiResponse = ApiResponse.<FeatureDto>builder()
                .success(true)
                .data(featureDto)
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(apiResponse);
    }

    @PatchMapping("/{uuid}")
    @Operation(summary = "Update feature", description = "Updates an existing feature record", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully updated feature"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Feature not found", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content)
    })
    public ResponseEntity<ApiResponse<FeatureDto>> patchFeature(@AuthenticationPrincipal UserContext userContext,
                                                    @PathVariable UUID uuid,
                                                    @Valid @RequestBody FeatureRequest featureRequest) {
        FeatureDto featureDto = featureService.updateFeature(userContext.getAccountId(), uuid, featureRequest);

        ApiResponse<FeatureDto> apiResponse = ApiResponse.<FeatureDto>builder()
                .success(true)
                .data(featureDto)
                .build();

        return ResponseEntity.status(HttpStatus.OK).body(apiResponse);
    }

    @DeleteMapping("/{uuid}")
    @Operation(summary = "Delete feature", description = "Deletes a specific feature by its UUID", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully deleted feature"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Feature not found", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> deleteFeature(@PathVariable UUID uuid) {
        featureService.deleteFeature(uuid);
        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder().success(true).build();
        return ResponseEntity.status(HttpStatus.OK).body(apiResponse);
    }

    @DeleteMapping
    @Operation(summary = "Bulk delete features", description = "Deletes a set of existing features by their UUIDs", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully deleted features"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> deleteFeatures(@Valid @RequestBody UuidListRequest uuids) {
        featureService.deleteFeatures(uuids);
        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder().success(true).build();
        return ResponseEntity.status(HttpStatus.OK).body(apiResponse);
    }
}