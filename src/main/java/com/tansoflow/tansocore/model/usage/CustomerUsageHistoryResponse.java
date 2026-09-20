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

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * What a customer recorded over a window, for audit. Usage outlives the subscription that carried it: a plan
 * change ends one subscription and starts another, and both periods are readable here afterwards.
 */
@Getter
@Builder
@Schema(description = "Recorded usage over an explicit window, grouped by subscription, feature and event name")
public class CustomerUsageHistoryResponse {
    private String customerReferenceId;
    private Instant from;
    private Instant to;
    private List<RecordedUsage> usage;

    @Getter
    @Builder
    @Schema(description = "One group of recorded events")
    public static class RecordedUsage {
        @Schema(description = "The subscription the usage was recorded against. Null for usage recorded with no subscription.")
        private String subscriptionId;
        @Schema(description = "The plan that subscription was on. Null when the subscription is unknown.")
        private String planKey;
        private String featureKey;
        @Schema(description = "The name the client sent with the event, which may differ from the feature key")
        private String eventName;
        private BigDecimal usageUnits;
        private Long events;
        private Instant firstOccurredAt;
        private Instant lastOccurredAt;
    }
}
