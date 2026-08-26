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

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** A project's AI build cost next to the revenue and serving cost of the feature it shipped, same window. All cents. */
@Getter
@Builder
public class SpendPnlReportDto {
    private LocalDate from;
    @Schema(description = "Exclusive.")
    private LocalDate to;
    private List<PnlRow> rows;
    @Schema(description = "PROJECT units with no feature linked — they have a build cost and no revenue to put beside it.")
    private List<String> unlinkedProjects;
    private BigDecimal totalBuildCents;
    private BigDecimal totalRevenueCents;
    private BigDecimal totalServeCostCents;
    private BigDecimal totalNetCents;

    @Getter
    @Builder
    public static class PnlRow {
        private String unitId;
        private String name;
        private String featureId;
        private String featureKey;
        private String featureName;
        @Schema(description = "Metered AI spend attributed to the project and its descendants (build side).")
        private BigDecimal buildCents;
        private long outcomes;
        @Schema(description = "Sum of revenueAmount on the feature's events in the window (serve side).")
        private BigDecimal revenueCents;
        @Schema(description = "Sum of costAmount on the feature's events in the window (serve side).")
        private BigDecimal serveCostCents;
        @Schema(description = "revenue − serve cost.")
        private BigDecimal serveMarginCents;
        @Schema(description = "serve margin − build cost.")
        private BigDecimal netCents;
        @Schema(description = "Build cost per outcome; null with no outcomes.")
        private BigDecimal buildPerOutcomeCents;
    }
}
