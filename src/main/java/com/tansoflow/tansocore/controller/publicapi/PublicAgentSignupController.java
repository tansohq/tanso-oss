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

import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.model.signup.AgentSignupResponse;
import com.tansoflow.tansocore.model.signup.request.AgentSignupRequest;
import com.tansoflow.tansocore.service.client.AgentSignupService;
import io.swagger.v3.oas.annotations.Operation;
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
public class PublicAgentSignupController {

    private final AgentSignupService agentSignupService;

    @PostMapping("/{slug}/signup")
    @Operation(summary = "Programmatic agent signup",
            description = "One call: creates a customer, subscribes it to the account's free default plan, and "
                    + "returns a customer-scoped API key (once). No CAPTCHA, no email verification. Only served "
                    + "when the operator enabled agent signup; rate-limited per account per hour (429 + Retry-After).")
    public ResponseEntity<ApiResponse<AgentSignupResponse>> signup(
            @PathVariable String slug,
            @Valid @RequestBody AgentSignupRequest request,
            HttpServletRequest httpRequest) {
        String baseUrl = httpRequest.getRequestURL().toString().replace(httpRequest.getRequestURI(), "");
        AgentSignupResponse response = agentSignupService.signup(slug, request, baseUrl);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<AgentSignupResponse>builder().data(response).success(true).build());
    }
}
