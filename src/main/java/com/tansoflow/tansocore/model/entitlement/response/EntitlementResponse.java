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
package com.tansoflow.tansocore.model.entitlement.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Response containing feature entitlement information for a customer")
public class EntitlementResponse {
    @Schema(description = "The external client reference ID for the customer", example = "cust_12345")
    private String referenceCustomerId;

    @Schema(description = "The key of the feature being checked", example = "premium_reports")
    private String featureKey;

    @Schema(description = "Whether the customer is allowed to access the feature")
    private boolean isAllowed;

    @Schema(description = "Additional metadata about the entitlement decision")
    private meta meta;

    @Schema(description = "The flow ID of the event")
    private String flowId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Usage information when a usage limit applies to the feature")
    private Usage usage;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Simulation results when usage context is provided in the request")
    private Simulation simulation;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Credit balance for the feature's credit model")
    private Credit credit;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Usage details for a metered feature")
    public static class Usage {
        @Schema(description = "Total usage consumed in the current period")
        private BigDecimal used;

        @Schema(description = "Maximum usage allowed by the plan")
        private BigDecimal limit;

        @Schema(description = "Remaining usage before the limit is hit")
        private BigDecimal remaining;
    }

    @Data
    @Schema(description = "Simulation results showing projected usage impact")
    public static class Simulation {
        @Schema(description = "The usage amount requested in the simulation")
        private BigDecimal requestedUsage;

        @Schema(description = "Cumulative usage after adding the requested amount")
        private BigDecimal projectedUsage;

        @Schema(description = "Remaining usage after projected usage (limit - projected, minimum 0)")
        private BigDecimal projectedRemaining;

        @Schema(description = "Whether the proposed usage would exceed the plan limit")
        private boolean wouldExceedLimit;
    }

    @Data
    @Schema(description = "Credit balance for the feature's credit model")
    public static class Credit {
        @Schema(description = "Credit denomination (e.g. CREDITS, TOKENS)")
        private String denomination;
        @Schema(description = "Current available balance")
        private BigDecimal balance;
        @Schema(description = "Total credits granted")
        private BigDecimal totalGranted;
        @Schema(description = "Total credits consumed")
        private BigDecimal totalConsumed;
        @Schema(description = "Whether zero balance blocks access")
        private Boolean hardLimit;
    }

    @Data
    @Schema(description = "Metadata about the entitlement decision")
    public static class meta {
        @Schema(description = "The reason why the entitlement was granted or denied")
        private reason reason;

        @Data
        @Schema(description = "Reason details")
        public static class reason {
            @Schema(description = "A human-readable description of the reason")
            private String description;
        }
    }
}