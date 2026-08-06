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
package com.tansoflow.tansocore.model.usage;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomerUsageResponse {
    private String customerReferenceId;
    private Instant asOf;
    private List<SubscriptionUsage> subscriptions;
    private List<CreditPoolUsage> creditPools;

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SubscriptionUsage {
        private String subscriptionId;
        private String planKey;
        private Instant currentPeriodStart;
        private Instant currentPeriodEnd;
        private List<FeatureUsage> features;
    }

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FeatureUsage {
        private String featureKey;
        private BigDecimal used;
        private BigDecimal limit;
        private BigDecimal remaining;
        @Schema(description = "Linear extrapolation of period usage: used ÷ elapsed period fraction. Null early in the period.")
        private BigDecimal projectedEndOfPeriod;
        private Boolean wouldExceedLimit;
    }

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CreditPoolUsage {
        private String poolId;
        private String denomination;
        private BigDecimal balance;
        private BigDecimal totalConsumed;
        @Schema(description = "Average credits burned per day since the pool was created")
        private BigDecimal averageDailyBurn;
        @Schema(description = "Projected date the balance reaches zero at the average burn rate. Null when burn is zero.")
        private Instant projectedDepletionDate;
        @Schema(description = "Current price of one credit from the price book. Null when unpriced.")
        private BigDecimal pricePerCredit;
        private String currency;
    }
}
