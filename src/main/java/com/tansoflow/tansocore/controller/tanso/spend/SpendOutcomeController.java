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
import com.tansoflow.tansocore.model.spend.OutcomeDto;
import com.tansoflow.tansocore.model.spend.OutcomeSourceDto;
import com.tansoflow.tansocore.model.spend.SpendOutcomeReportDto;
import com.tansoflow.tansocore.model.spend.VendorProbeResultDto;
import com.tansoflow.tansocore.model.spend.VendorSyncResultDto;
import com.tansoflow.tansocore.model.spend.request.OutcomeRequest;
import com.tansoflow.tansocore.model.spend.request.OutcomeSourceRequest;
import com.tansoflow.tansocore.service.internal.spend.OutcomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/spend")
@PreAuthorize("hasRole('TANSO_UI')")
@ConditionalOnProperty(name = "app.modules.build.enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "Spend — Outcomes", description = "Shipped work (merged PRs, done issues, anything a CI job posts) and cost per outcome")
public class SpendOutcomeController {
    private final OutcomeService outcomeService;

    @GetMapping("/outcome-sources")
    @Operation(summary = "List outcome sources", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<List<OutcomeSourceDto>>> sources(@AuthenticationPrincipal UserContext userContext) {
        return ok(outcomeService.listSources(userContext.getAccountId()));
    }

    @PostMapping("/outcome-sources")
    @Operation(summary = "Connect GitHub repos or Linear teams", description = "The token is stored encrypted and never returned.",
            security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<OutcomeSourceDto>> createSource(@AuthenticationPrincipal UserContext userContext,
                                                                      @Valid @RequestBody OutcomeSourceRequest request) {
        OutcomeSourceDto created = outcomeService.createSource(userContext.getAccountId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<OutcomeSourceDto>builder().data(created).success(true).build());
    }

    @DeleteMapping("/outcome-sources/{sourceId}")
    @Operation(summary = "Disconnect a source and drop the outcomes it pulled", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<Void>> deleteSource(@AuthenticationPrincipal UserContext userContext, @PathVariable String sourceId) {
        outcomeService.deleteSource(userContext.getAccountId(), sourceId);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).build());
    }

    @PostMapping("/outcome-sources/{sourceId}/probe")
    @Operation(summary = "Check the stored token against the system", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<VendorProbeResultDto>> probe(@AuthenticationPrincipal UserContext userContext, @PathVariable String sourceId) {
        return ok(outcomeService.probe(userContext.getAccountId(), sourceId));
    }

    @PostMapping("/outcome-sources/{sourceId}/sync")
    @Operation(summary = "Pull outcomes for a window", description = "[from, to). Defaults to the last 30 days. Re-pulls upsert.",
            security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<VendorSyncResultDto>> sync(
            @AuthenticationPrincipal UserContext userContext, @PathVariable String sourceId,
            @Parameter(description = "First day, inclusive (UTC).") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Day to stop at, EXCLUSIVE (UTC).") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ok(outcomeService.sync(userContext.getAccountId(), sourceId, from, to));
    }

    @PostMapping("/outcomes")
    @Operation(summary = "Record that something shipped",
            description = "For CI jobs and scripts. Same externalId again updates the outcome. Attributed to the person whose email/login matches (person level on), else the given unit.",
            security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<OutcomeDto>> record(@AuthenticationPrincipal UserContext userContext,
                                                          @Valid @RequestBody OutcomeRequest request) {
        OutcomeDto created = outcomeService.record(userContext.getAccountId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<OutcomeDto>builder().data(created).success(true).build());
    }

    @GetMapping("/outcomes")
    @Operation(summary = "Recent outcomes, newest first (200)", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<List<OutcomeDto>>> recent(@AuthenticationPrincipal UserContext userContext) {
        return ok(outcomeService.recent(userContext.getAccountId()));
    }

    @GetMapping("/reports/outcomes")
    @Operation(summary = "Cost per outcome per unit", description = "[from, to). Spend (unit + descendants) over outcomes (unit + descendants).",
            security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<SpendOutcomeReportDto>> report(
            @AuthenticationPrincipal UserContext userContext,
            @Parameter(description = "First day, inclusive (UTC). Default: 30 days before `to`.") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Day to stop at, EXCLUSIVE (UTC). Default: tomorrow.") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ok(outcomeService.report(userContext.getAccountId(), from, to));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder().data(data).success(true).build());
    }
}
