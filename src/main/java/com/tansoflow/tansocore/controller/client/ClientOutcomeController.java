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
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.model.spend.OutcomeDto;
import com.tansoflow.tansocore.model.spend.request.OutcomeRequest;
import com.tansoflow.tansocore.service.internal.spend.OutcomeService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The one build-side route a script can hit with the tenant API key: a CI job
 * saying something shipped. Customer keys are refused — this is the operator's
 * own spend, not a customer's.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/client/outcomes")
@PreAuthorize("hasRole('CLIENT')")
@ConditionalOnProperty(name = "app.modules.build.enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "Outcomes (Client API)", description = "Record shipped work from CI with the tenant API key")
public class ClientOutcomeController {
    private final OutcomeService outcomeService;

    @PostMapping
    @Operation(summary = "Record that something shipped",
            description = "Same contract as the console route: same externalId again updates the outcome. Tenant sk_ key only.",
            security = @SecurityRequirement(name = "ApiKey"))
    public ResponseEntity<ApiResponse<OutcomeDto>> record(@AuthenticationPrincipal UserContext userContext,
                                                          @Valid @RequestBody OutcomeRequest request) {
        OutcomeDto created = outcomeService.record(userContext.getAccountId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<OutcomeDto>builder().data(created).success(true).build());
    }
}
