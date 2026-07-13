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
package com.tansoflow.tansocore.model.analytics;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Revenue breakdown for a single billing period")
public class RevenueBridgePeriodDto {
    @Schema(description = "Start of the billing period")
    private Instant periodStart;

    @Schema(description = "End of the billing period")
    private Instant periodEnd;

    @Schema(description = "Total revenue from all paid invoices in this period")
    private BigDecimal totalRevenue;

    @Schema(description = "Revenue from plan base prices")
    private BigDecimal baseRevenue;

    @Schema(description = "Revenue from usage-based charges")
    private BigDecimal usageRevenue;

    @Schema(description = "Revenue from adjustment invoices (upgrades/downgrades)")
    private BigDecimal adjustmentRevenue;

    @Schema(description = "Credit amounts applied in this period (negative)")
    private BigDecimal creditAmount;

    @Schema(description = "Net revenue (totalRevenue, which already includes credits)")
    private BigDecimal netRevenue;

    @Schema(description = "Ratio of |creditAmount| to gross revenue (totalRevenue - creditAmount), null if gross is zero")
    private BigDecimal creditToRevenueRatio;

    @Schema(description = "Number of distinct customers with paid invoices in this period")
    private int customerCount;

    @Schema(description = "Number of customers new in this period vs the previous period")
    private int newCustomers;

    @Schema(description = "Number of customers in the previous period but not this one")
    private int churnedCustomers;
}
