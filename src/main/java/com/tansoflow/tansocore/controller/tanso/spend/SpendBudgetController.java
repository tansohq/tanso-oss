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
import com.tansoflow.tansocore.model.spend.SpendAlertDto;
import com.tansoflow.tansocore.model.spend.SpendBudgetDto;
import com.tansoflow.tansocore.model.spend.SpendDigestDto;
import com.tansoflow.tansocore.model.spend.request.SpendBudgetBumpRequest;
import com.tansoflow.tansocore.model.spend.request.SpendBudgetRequest;
import com.tansoflow.tansocore.service.internal.spend.SpendDigestService;
import com.tansoflow.tansocore.service.internal.spend.SpendBudgetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/spend")
@PreAuthorize("hasRole('TANSO_UI')")
@ConditionalOnProperty(name = "app.modules.build.enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "Spend — Budgets & Alerts", description = "Daily and monthly ceilings per unit, and what they said")
public class SpendBudgetController {
    private final SpendBudgetService spendBudgetService;
    private final SpendDigestService spendDigestService;

    @GetMapping("/units/{unitId}/budget")
    @Operation(summary = "Read a unit's budget and where it stands", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<SpendBudgetDto>> get(@AuthenticationPrincipal UserContext userContext, @PathVariable String unitId) {
        return ok(spendBudgetService.getBudget(userContext.getAccountId(), unitId));
    }

    @PutMapping("/units/{unitId}/budget")
    @Operation(summary = "Set a unit's daily and/or monthly ceiling", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<SpendBudgetDto>> put(@AuthenticationPrincipal UserContext userContext, @PathVariable String unitId,
                                                          @Valid @RequestBody SpendBudgetRequest request) {
        return ok(spendBudgetService.putBudget(userContext.getAccountId(), unitId, request));
    }

    @DeleteMapping("/units/{unitId}/budget")
    @Operation(summary = "Remove a unit's budget", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal UserContext userContext, @PathVariable String unitId) {
        spendBudgetService.deleteBudget(userContext.getAccountId(), unitId);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).build());
    }

    @PostMapping("/units/{unitId}/budget/bump")
    @Operation(summary = "Lift the monthly ceiling until a date", description = "The standing ceiling is untouched and applies again when the bump expires; a Block budget is re-pushed to the gateway both times.",
            security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<SpendBudgetDto>> bump(@AuthenticationPrincipal UserContext userContext, @PathVariable String unitId,
                                                           @Valid @RequestBody SpendBudgetBumpRequest request) {
        return ok(spendBudgetService.bump(userContext.getAccountId(), unitId, request));
    }

    @DeleteMapping("/units/{unitId}/budget/bump")
    @Operation(summary = "End a bump early", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<SpendBudgetDto>> clearBump(@AuthenticationPrincipal UserContext userContext, @PathVariable String unitId) {
        return ok(spendBudgetService.clearBump(userContext.getAccountId(), unitId));
    }

    @GetMapping("/digest")
    @Operation(summary = "Preview the weekly digest", description = "Last seven full UTC days against the seven before, per unit, with budget standing.",
            security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<SpendDigestDto>> digest(@AuthenticationPrincipal UserContext userContext) {
        return ok(spendDigestService.build(userContext.getAccountId()));
    }

    @PostMapping("/digest/send")
    @Operation(summary = "Send the weekly digest now", description = "To Slack, the webhook and the alert emails, whichever are configured. Monday 08:00 UTC otherwise, when enabled in settings.",
            security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<SpendDigestDto>> sendDigest(@AuthenticationPrincipal UserContext userContext) {
        return ok(spendDigestService.send(userContext.getAccountId()));
    }

    @PostMapping("/budgets/evaluate")
    @Operation(summary = "Check every budget now", description = "Also runs after each sync and hourly. Returns what fired this time.",
            security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<List<SpendAlertDto>>> evaluate(@AuthenticationPrincipal UserContext userContext) {
        return ok(spendBudgetService.evaluate(userContext.getAccountId()));
    }

    @GetMapping("/alerts")
    @Operation(summary = "List alerts, newest first", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<List<SpendAlertDto>>> alerts(@AuthenticationPrincipal UserContext userContext,
                                                                   @RequestParam(defaultValue = "false") boolean unackedOnly) {
        return ok(spendBudgetService.listAlerts(userContext.getAccountId(), unackedOnly));
    }

    @PostMapping("/alerts/{alertId}/ack")
    @Operation(summary = "Acknowledge an alert", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<SpendAlertDto>> ack(@AuthenticationPrincipal UserContext userContext, @PathVariable String alertId) {
        String actor = userContext.getEmail() != null ? userContext.getEmail() : userContext.getUserId();
        return ok(spendBudgetService.ack(userContext.getAccountId(), alertId, actor));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder().data(data).success(true).build());
    }
}
