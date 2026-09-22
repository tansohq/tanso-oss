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
package com.tansoflow.tansocore.model.signup;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Getter
@Builder
public class AgentStatusResponse {
    private String customerReferenceId;
    private String status;
    @JsonProperty("expires_at")
    private Instant expiresAt;
    @JsonProperty("claimed_at")
    private Instant claimedAt;
    private String plan;
    private AgentSignupResponse.AgentLimits limits;
    private Map<String, BigDecimal> remaining;
    private Spend spend;
    @JsonProperty("owner_email")
    private String ownerEmail;
    @JsonProperty("spend_mandate")
    private SpendMandateStatus spendMandate;

    /**
     * The customer's mandate, shared by all of its keys. The numbers are null until a mandate is active;
     * setup_url is set while a page (first or raised mandate) waits for the principal.
     */
    @Getter
    @Builder
    public static class SpendMandateStatus {
        @Schema(description = "none | pending | active | expired")
        private String status;
        @JsonProperty("setup_url")
        private String setupUrl;
        @JsonProperty("max_amount")
        private BigDecimal maxAmount;
        @Schema(description = "Off-session spend across all of the customer's keys in the current window")
        private BigDecimal spent;
        private BigDecimal remaining;
        @Schema(description = "day | week | month")
        private String period;
        @JsonProperty("resets_at")
        private Instant resetsAt;
        private String currency;
    }

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Spend {
        private BigDecimal cap;
        private BigDecimal spent;
        private BigDecimal remaining;
        private String currency;
        @JsonProperty("resets_at")
        private Instant resetsAt;
    }
}
