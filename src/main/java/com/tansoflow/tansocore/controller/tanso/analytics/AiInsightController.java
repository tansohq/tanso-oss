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
package com.tansoflow.tansocore.controller.tanso.analytics;

import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.model.analytics.AiInsightDto;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.service.internal.analytics.AiInsightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/analytics/insights")
@PreAuthorize("hasRole('TANSO_UI')")
@Tag(name = "AI Insights", description = "AI-powered analytics insights")
public class AiInsightController {

    private final AiInsightService aiInsightService;

    @GetMapping
    @Operation(summary = "List insights", description = "Returns previously generated AI insights", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<List<AiInsightDto>>> listInsights(
            @AuthenticationPrincipal UserContext userContext) {
        List<AiInsightDto> insights = aiInsightService.listInsights(userContext.getAccountId());
        return ResponseEntity.ok(ApiResponse.<List<AiInsightDto>>builder()
                .success(true)
                .data(insights)
                .build());
    }

    @PostMapping("/generate")
    @Operation(summary = "Generate insights", description = "Analyzes event data and generates AI-powered insights", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<List<AiInsightDto>>> generateInsights(
            @AuthenticationPrincipal UserContext userContext) {
        List<AiInsightDto> insights = aiInsightService.generateInsights(userContext.getAccountId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<List<AiInsightDto>>builder()
                .success(true)
                .data(insights)
                .build());
    }

    @DeleteMapping
    @Operation(summary = "Clear insights", description = "Deletes all generated insights", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<Void>> clearInsights(
            @AuthenticationPrincipal UserContext userContext) {
        aiInsightService.clearInsights(userContext.getAccountId());
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .build());
    }
}
