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
package com.tansoflow.tansocore.controller.tanso.spend;

import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.model.spend.SpendSettingsDto;
import com.tansoflow.tansocore.model.spend.request.SpendSettingsRequest;
import com.tansoflow.tansocore.service.internal.spend.SpendSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/spend/settings")
@PreAuthorize("hasRole('TANSO_UI')")
@ConditionalOnProperty(name = "app.modules.build.enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "Spend — Settings", description = "Person-level attribution, the worker notice, and the Slack webhook for alerts")
public class SpendSettingsController {
    private final SpendSettingsService spendSettingsService;

    @GetMapping
    @Operation(summary = "Read spend settings", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<SpendSettingsDto>> get(@AuthenticationPrincipal UserContext userContext) {
        return ResponseEntity.ok(ApiResponse.<SpendSettingsDto>builder().data(spendSettingsService.get(userContext.getAccountId())).success(true).build());
    }

    @PutMapping
    @Operation(summary = "Update spend settings",
            description = "Person-level attribution needs a worker notice first. slackWebhookUrl is stored encrypted and never returned; send an empty string to remove it.",
            security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<SpendSettingsDto>> update(@AuthenticationPrincipal UserContext userContext,
                                                                @Valid @RequestBody SpendSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.<SpendSettingsDto>builder().data(spendSettingsService.update(userContext.getAccountId(), request)).success(true).build());
    }
}
