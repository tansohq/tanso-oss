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
import com.tansoflow.tansocore.model.spend.SpendAttributionRuleDto;
import com.tansoflow.tansocore.model.spend.SpendUnitDto;
import com.tansoflow.tansocore.model.spend.request.SpendAttributionRuleRequest;
import com.tansoflow.tansocore.model.spend.request.SpendUnitRequest;
import com.tansoflow.tansocore.service.internal.spend.SpendUnitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/spend")
@PreAuthorize("hasRole('TANSO_UI')")
@ConditionalOnProperty(name = "app.modules.build.enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "Spend — Units & Attribution", description = "Teams, people and projects, and the rules that map vendor usage onto them")
public class SpendUnitController {
    private final SpendUnitService spendUnitService;

    @GetMapping("/units")
    @Operation(summary = "List spend units", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<List<SpendUnitDto>>> listUnits(@AuthenticationPrincipal UserContext userContext) {
        return ok(spendUnitService.listUnits(userContext.getAccountId()));
    }

    @PostMapping("/units")
    @Operation(summary = "Create a team, person or project", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<SpendUnitDto>> createUnit(@AuthenticationPrincipal UserContext userContext,
                                                                @Valid @RequestBody SpendUnitRequest request) {
        SpendUnitDto created = spendUnitService.createUnit(userContext.getAccountId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<SpendUnitDto>builder().data(created).success(true).build());
    }

    @PutMapping("/units/{unitId}")
    @Operation(summary = "Update a unit: name, parent, person fields, feature link", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<SpendUnitDto>> updateUnit(@AuthenticationPrincipal UserContext userContext,
                                                                @PathVariable String unitId,
                                                                @Valid @RequestBody SpendUnitRequest request) {
        return ok(spendUnitService.updateUnit(userContext.getAccountId(), unitId, request));
    }

    @DeleteMapping("/units/{unitId}")
    @Operation(summary = "Remove a unit (its rules and budget go with it; children move up)", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<Void>> deleteUnit(@AuthenticationPrincipal UserContext userContext, @PathVariable String unitId) {
        spendUnitService.deleteUnit(userContext.getAccountId(), unitId);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).build());
    }

    @GetMapping("/rules")
    @Operation(summary = "List attribution rules, highest priority first", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<List<SpendAttributionRuleDto>>> listRules(@AuthenticationPrincipal UserContext userContext) {
        return ok(spendUnitService.listRules(userContext.getAccountId()));
    }

    @PostMapping("/rules")
    @Operation(summary = "Map a vendor workspace, key or actor onto a unit", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<SpendAttributionRuleDto>> createRule(@AuthenticationPrincipal UserContext userContext,
                                                                           @Valid @RequestBody SpendAttributionRuleRequest request) {
        SpendAttributionRuleDto created = spendUnitService.createRule(userContext.getAccountId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<SpendAttributionRuleDto>builder().data(created).success(true).build());
    }

    @DeleteMapping("/rules/{ruleId}")
    @Operation(summary = "Remove a rule", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<Void>> deleteRule(@AuthenticationPrincipal UserContext userContext, @PathVariable String ruleId) {
        spendUnitService.deleteRule(userContext.getAccountId(), ruleId);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).build());
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder().data(data).success(true).build());
    }
}
