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

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.tansoflow.tansocore.auth.CustomerAccessGuard;
import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.model.usage.CustomerUsageResponse;
import com.tansoflow.tansocore.service.client.UsageForecastService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/client/customers/{customerReferenceId}/usage")
@PreAuthorize("hasRole('CLIENT')")
@Tag(name = "Usage", description = "Current-period usage and burndown forecast")
@ConditionalOnProperty(name = "app.modules.monetization.enabled", havingValue = "true", matchIfMissing = true)
public class UsageClientController {

    private final CustomerAccessGuard customerAccessGuard;
    private final UsageForecastService usageForecastService;

    @GetMapping
    @PreAuthorize("hasAnyRole('CLIENT','CUSTOMER')")
    @Operation(summary = "Usage and burndown forecast",
            description = "Per-feature current-period usage with a linear end-of-period projection, and per-pool "
                    + "credit balances with average burn, projected depletion date, and the current credit price.",
            security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<CustomerUsageResponse>> getUsage(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String customerReferenceId) {
        customerReferenceId = customerAccessGuard.resolveCustomerRef(userContext, customerReferenceId);
        CustomerUsageResponse usage = usageForecastService.getUsage(customerReferenceId, userContext.getAccountId());
        return ResponseEntity.ok(ApiResponse.<CustomerUsageResponse>builder().data(usage).success(true).build());
    }
}
