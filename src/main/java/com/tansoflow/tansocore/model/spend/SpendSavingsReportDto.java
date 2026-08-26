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

/** What prompt caching is worth: per model, the input-side cost as billed against the same tokens with no cache. */
@Getter
@Builder
public class SpendSavingsReportDto {
    private LocalDate from;
    @Schema(description = "Exclusive.")
    private LocalDate to;
    private SavingsRow totals;
    private List<SavingsRow> byModel;
    private List<String> unpricedModels;

    @Getter
    @Builder
    public static class SavingsRow {
        private VendorProvider provider;
        private String model;
        private long uncachedInputTokens;
        private long cacheReadTokens;
        private long cacheCreationTokens;
        private long outputTokens;
        @Schema(description = "cacheRead / (uncached + cacheRead + cacheWrite), 0–1.")
        private BigDecimal cacheShare;
        @Schema(description = "Input-side cost as billed: uncached at the input rate, cache reads and writes at their rates.")
        private BigDecimal inputCostCents;
        @Schema(description = "The same input tokens with no cache: everything at the input rate.")
        private BigDecimal noCacheCostCents;
        @Schema(description = "noCacheCostCents − inputCostCents. Negative when cache writes cost more than the reads saved.")
        private BigDecimal savedCents;
        private boolean priced;
        @Schema(description = "False when the price book has no cache rates for the model — the row then assumes cache tokens cost the input rate, i.e. zero saving.")
        private boolean cacheRatesKnown;
    }
}
