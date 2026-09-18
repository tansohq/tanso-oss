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
import com.tansoflow.tansocore.model.analytics.AgentFunnelResponse;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.service.internal.analytics.AgentFunnelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.time.ZoneOffset;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/tanso/agent-funnel")
@PreAuthorize("hasRole('TANSO_UI')")
@Tag(name = "Agent funnel", description = "Agent self-signup funnel: signups, first verified job, claimed, paid")
@ConditionalOnProperty(name = "app.modules.monetization.enabled", havingValue = "true", matchIfMissing = true)
public class AgentFunnelController {

    private final AgentFunnelService agentFunnelService;

    @GetMapping
    @Operation(summary = "Get agent funnel",
            description = "Counts customers whose external id starts with agent_ and were created in [from, to), "
                    + "and how many reached first usage event, claim (first payment) and paid. Defaults to the last 30 days.",
            security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved agent funnel"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "from is not before to", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content)})
    public ResponseEntity<ApiResponse<AgentFunnelResponse>> getAgentFunnel(
            @AuthenticationPrincipal UserContext userContext,
            @Parameter(description = "Inclusive start date (UTC), ISO yyyy-MM-dd. Default: 30 days ago.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Exclusive end date (UTC), ISO yyyy-MM-dd. Default: tomorrow, so today is included.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate effectiveFrom = from != null ? from : today.minusDays(30);
        LocalDate effectiveTo = to != null ? to : today.plusDays(1);
        if (!effectiveFrom.isBefore(effectiveTo)) {
            throw new IllegalArgumentException("from must be before to");
        }
        if (effectiveFrom.plusDays(366).isBefore(effectiveTo)) {
            throw new IllegalArgumentException("range must be 366 days or less");
        }
        AgentFunnelResponse funnel = agentFunnelService.getFunnel(userContext.getAccountId(), effectiveFrom, effectiveTo);
        return ResponseEntity.ok(ApiResponse.<AgentFunnelResponse>builder()
                .success(true)
                .data(funnel)
                .build());
    }
}
