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

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentSignupResponse {
    private String customerReferenceId;
    @Schema(description = "Customer-scoped API key. Returned exactly once; store it now.")
    private String apiKey;
    private List<String> apiKeyScopes;
    private String plan;

    @Schema(description = "provisional until the first payment claims the account; expired once past expires_at unpaid")
    private String status;

    @JsonProperty("expires_at")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(description = "When an unclaimed provisional account is closed. Null once claimed.")
    private Instant expiresAt;

    private AgentLimits limits;

    @JsonProperty("spend_mandate")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(description = "Null unless the request asked for one")
    private AgentSpendMandate spendMandate;

    @JsonProperty("status_url")
    private String statusUrl;

    @JsonProperty("owner_url")
    private String ownerUrl;

    @Schema(description = "Where to go next: base URL, entitlement check, event ingestion, usage")
    private Map<String, String> nextSteps;

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AgentLimits {
        private Map<String, FeatureLimit> features;
        @JsonProperty("spend_cap")
        private java.math.BigDecimal spendCap;
        private String currency;
    }

    @Getter
    @Builder
    public static class FeatureLimit {
        private java.math.BigDecimal included;
        private String period;
        private boolean unlimited;
    }

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AgentSpendMandate {
        @Schema(description = "pending | unavailable")
        private String status;
        @JsonProperty("setup_url")
        @Schema(description = "Hand this to the principal. Saving a card here activates the mandate and claims the account.")
        private String setupUrl;
        @JsonProperty("max_amount")
        private java.math.BigDecimal maxAmount;
        private String currency;
        private String period;
    }
}
