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
package com.tansoflow.tansocore.controller.publicapi;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.model.signup.AgentSignupResponse;
import com.tansoflow.tansocore.model.signup.request.AgentSignupRequest;
import com.tansoflow.tansocore.service.client.AgentSignupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/public/v1/catalog")
@Tag(name = "Public Catalog", description = "Machine-readable pricing for buying agents — no authentication")
@ConditionalOnProperty(name = "app.modules.monetization.enabled", havingValue = "true", matchIfMissing = true)
public class PublicAgentSignupController {

    private final AgentSignupService agentSignupService;

    @PostMapping("/{slug}/signup")
    @Operation(summary = "Programmatic agent signup",
            description = "One call: creates a provisional customer, subscribes it to the account's free default "
                    + "plan, and returns a customer-scoped API key (once). Email optional. No CAPTCHA, no email "
                    + "verification; the first payment claims the account, unclaimed accounts expire. Only served "
                    + "when the operator enabled agent signup; rate-limited per account and per IP per hour "
                    + "(429 + Retry-After).")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description =
                    "Customer created as provisional, subscribed to the free default plan; data carries the "
                            + "customer-scoped apiKey (shown once), limits, expires_at, status_url and nextSteps."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description =
                    "No account has this slug, or the operator has not enabled the public catalog or agent signup. "
                            + "error.code=not_found.", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description =
                    "Invalid body: malformed email, spend_mandate without max_amount, or a currency other than the "
                            + "account's. error.code=validation_failed.", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description =
                    "Hourly signup cap reached for this account or IP. error.code=rate_limited; "
                            + "Retry-After header carries the seconds to wait.", content = @Content)
    })
    public ResponseEntity<ApiResponse<AgentSignupResponse>> signup(
            @PathVariable String slug,
            @Valid @RequestBody AgentSignupRequest request,
            HttpServletRequest httpRequest) {
        String baseUrl = httpRequest.getRequestURL().toString().replace(httpRequest.getRequestURI(), "");
        AgentSignupResponse response = agentSignupService.signup(slug, request, baseUrl, clientIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<AgentSignupResponse>builder().data(response).success(true).build());
    }

    // Behind a proxy the real address arrives via server.forward-headers-strategy (set in the prod,
    // staging and sandbox profiles). Reading X-Forwarded-For here directly would let any caller pick its own IP.
    static String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
