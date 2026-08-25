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

import com.tansoflow.tansocore.model.spend.type.BudgetMode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
public class SpendBudgetDto {
    private String spendUnitId;
    private BigDecimal dailyCents;
    private BigDecimal monthlyCents;
    private int alertThreshold;
    private BudgetMode monthlyMode;
    @Schema(description = "Spend so far in the current UTC day / calendar month, for the unit including descendants.")
    private BigDecimal dailySpentCents;
    private BigDecimal monthlySpentCents;
    private Instant dailyResetsAt;
    private Instant monthlyResetsAt;
}
