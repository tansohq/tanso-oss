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

/** Metered spend for a window allocated to units by the attribution rules. */
@Getter
@Builder
public class SpendAllocationReportDto {
    private LocalDate from;
    /** Exclusive. */
    private LocalDate to;
    private List<Row> rows;
    @Schema(description = "Metered cents no rule claimed. Always sums with the rows' own cents to totalMeteredCents.")
    private BigDecimal unattributedCents;
    private BigDecimal totalMeteredCents;
    @Schema(description = "False when person-level attribution is off for the account; PERSON units then receive nothing.")
    private boolean personLevelEnabled;

    @Getter
    @Builder
    public static class Row {
        private String unitId;
        private String name;
        private SpendUnitType type;
        private String parentId;
        @Schema(description = "Metered cents matched directly to this unit's rules.")
        private BigDecimal ownCents;
        @Schema(description = "Own cents plus every descendant's own cents.")
        private BigDecimal totalCents;
        @Schema(description = "PERSON units only: the vendor's own per-person estimate (Claude Code). Not rolled up into parents — the same traffic already reaches the team through its key rules.")
        private BigDecimal personEstimateCents;
        @Schema(description = "What budgets are measured against: totalCents plus personEstimateCents.")
        private BigDecimal spendCents;
    }
}
