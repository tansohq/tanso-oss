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

import com.tansoflow.tansocore.model.spend.type.SpendUnitType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Cost per shipped thing: spend allocated to a unit (with descendants) over the outcomes attributed to it (with descendants). */
@Getter
@Builder
public class SpendOutcomeReportDto {
    private LocalDate from;
    /** Exclusive. */
    private LocalDate to;
    private List<OutcomeRow> rows;
    private long totalOutcomes;
    private long unattributedOutcomes;
    private BigDecimal totalSpendCents;
    @Schema(description = "Total spend over total outcomes; null when there are no outcomes.")
    private BigDecimal costPerOutcomeCents;

    @Getter
    @Builder
    public static class OutcomeRow {
        private String unitId;
        private String name;
        private SpendUnitType type;
        private String parentId;
        private long prsMerged;
        private long issuesDone;
        private long custom;
        private long outcomes;
        @Schema(description = "Metered spend allocated to the unit and its descendants — the price-book figure, same basis for every row.")
        private BigDecimal spendCents;
        @Schema(description = "PERSON units only: the vendor's own Claude Code estimate. Shown beside, never inside, spendCents.")
        private BigDecimal personEstimateCents;
        @Schema(description = "spendCents / outcomes; null when the unit shipped nothing or has no metered spend to divide.")
        private BigDecimal costPerOutcomeCents;
    }
}
