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
package com.tansoflow.tansocore.model.spend;

import com.tansoflow.tansocore.model.spend.type.VendorProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Internal AI usage for a window, in the vendor's dimensions. Token figures come from usage reports only; Claude Code rows are a per-person view of the same traffic, so they are listed under actors and never added on top. */
@Getter
@Builder
public class SpendUsageReportDto {
    private LocalDate from;
    /** Exclusive. */
    private LocalDate to;
    private Totals totals;
    private List<ModelRow> byModel;
    private List<DayRow> byDay;
    private List<ActorRow> byActor;
    @Schema(description = "Models seen in usage that the price book does not know; their metered cost is zero.")
    private List<String> unpricedModels;

    @Getter
    @Builder
    public static class Totals {
        private long uncachedInputTokens;
        private long cacheReadTokens;
        private long cacheCreationTokens;
        private long outputTokens;
        private long requests;
        @Schema(description = "What the vendors' cost reports say, in cents.")
        private BigDecimal vendorCostCents;
        @Schema(description = "What the price book says the tokens should cost, in cents.")
        private BigDecimal meteredCostCents;
    }

    @Getter
    @Builder
    public static class ModelRow {
        private VendorProvider provider;
        private String model;
        private long uncachedInputTokens;
        private long cacheReadTokens;
        private long cacheCreationTokens;
        private long outputTokens;
        private long requests;
        private BigDecimal meteredCostCents;
        @Schema(description = "Cost-report rows the vendor attributed to this model; null when the vendor does not break cost down by model (OpenAI).")
        private BigDecimal vendorCostCents;
        private boolean priced;
    }

    @Getter
    @Builder
    public static class DayRow {
        private LocalDate date;
        private long totalTokens;
        private BigDecimal meteredCostCents;
        private BigDecimal vendorCostCents;
    }

    @Getter
    @Builder
    public static class ActorRow {
        private VendorProvider provider;
        @Schema(description = "Claude Code actor email or key name, or OpenAI user id.")
        private String actor;
        private long totalTokens;
        private long sessions;
        @Schema(description = "The vendor's own estimate for this actor (Claude Code); null for OpenAI users.")
        private BigDecimal vendorCostCents;
        private BigDecimal meteredCostCents;
    }
}
