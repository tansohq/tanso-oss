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
import com.tansoflow.tansocore.entity.CheckoutSession;
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.repository.CheckoutSessionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Polling endpoint for hosted checkout outcomes, so an agent that received a
 * checkout URL learns what happened without a browser.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/client/checkout-sessions")
@PreAuthorize("hasRole('CLIENT')")
@Tag(name = "Checkout Sessions", description = "Poll the outcome of hosted checkout flows")
public class CheckoutSessionClientController {

    private final CheckoutSessionRepository checkoutSessionRepository;

    @GetMapping("/{checkoutSessionId}")
    @PreAuthorize("hasAnyRole('CLIENT','CUSTOMER')")
    @Operation(summary = "Checkout session status",
            description = "PENDING until the hosted checkout completes; COMPLETED carries the created "
                    + "subscription id (subscriptions) or granted credits (top-ups).",
            security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<CheckoutSessionDto>> getCheckoutSession(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String checkoutSessionId) {
        CheckoutSession session = checkoutSessionRepository
                .findByIdAndAccountId(UUID.fromString(checkoutSessionId), UUID.fromString(userContext.getAccountId()))
                .orElseThrow(() -> new ResourceNotFoundException("Checkout session not found: " + checkoutSessionId));

        if (userContext.isCustomerScoped()
                && !session.getCustomerId().toString().equals(userContext.getCustomerId())) {
            throw new AccessDeniedException("This API key is scoped to another customer");
        }

        CheckoutSessionDto dto = CheckoutSessionDto.builder()
                .id(session.getId().toString())
                .purpose(session.getPurpose())
                .status(session.getStatus())
                .checkoutUrl(session.getCheckoutUrl())
                .subscriptionId(session.getSubscriptionId() != null ? session.getSubscriptionId().toString() : null)
                .credits(session.getCredits())
                .createdAt(session.getCreatedAt())
                .completedAt(session.getCompletedAt())
                .build();
        return ResponseEntity.ok(ApiResponse.<CheckoutSessionDto>builder().data(dto).success(true).build());
    }

    @Getter
    @Builder
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public static class CheckoutSessionDto {
        private String id;
        private String purpose;
        private String status;
        private String checkoutUrl;
        private String subscriptionId;
        private BigDecimal credits;
        private Instant createdAt;
        private Instant completedAt;
    }
}
