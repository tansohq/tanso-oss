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
import com.tansoflow.tansocore.model.spend.SpendReconcileReportDto;
import com.tansoflow.tansocore.model.spend.SpendUsageReportDto;
import com.tansoflow.tansocore.service.internal.spend.SpendReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/spend/reports")
@PreAuthorize("hasRole('TANSO_UI')")
@ConditionalOnProperty(name = "app.modules.build.enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "Spend — Reports", description = "Internal AI usage and the three-way reconcile (price book vs vendor report vs invoice)")
public class SpendReportController {
    private final SpendReportService spendReportService;

    @GetMapping("/usage")
    @Operation(summary = "Usage and cost for a window", description = "[from, to). Defaults to the last 30 days.",
            security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<SpendUsageReportDto>> usage(
            @AuthenticationPrincipal UserContext userContext,
            @Parameter(description = "First day, inclusive (UTC). Default: 30 days before `to`.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Day to stop at, EXCLUSIVE (UTC). Default: tomorrow.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        SpendUsageReportDto report = spendReportService.usage(userContext.getAccountId(), from, to);
        return ResponseEntity.ok(ApiResponse.<SpendUsageReportDto>builder().data(report).success(true).build());
    }

    @GetMapping("/reconcile")
    @Operation(summary = "Reconcile a period per vendor",
            description = "[from, to] inclusive. Defaults to the last full calendar month. Metered = price book × tokens; "
                    + "vendor = the vendor's cost report; invoiced = imported invoices that sit inside the window.",
            security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<SpendReconcileReportDto>> reconcile(
            @AuthenticationPrincipal UserContext userContext,
            @Parameter(description = "First day of the period, inclusive. Default: first day of last month.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Last day of the period, INCLUSIVE — invoices are dated, not timestamped. Default: last day of last month.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        SpendReconcileReportDto report = spendReportService.reconcile(userContext.getAccountId(), from, to);
        return ResponseEntity.ok(ApiResponse.<SpendReconcileReportDto>builder().data(report).success(true).build());
    }
}
