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
package com.tansoflow.tansocore.model.spend.request;

import com.tansoflow.tansocore.model.spend.type.BudgetMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SpendBudgetRequest {
    @Schema(description = "Cents per UTC day. Null = no daily ceiling.")
    private BigDecimal dailyCents;
    @Schema(description = "Cents per calendar month. Null = no monthly ceiling.")
    private BigDecimal monthlyCents;
    @Min(1)
    @Max(100)
    @Schema(description = "Percent of either ceiling that raises a THRESHOLD alert. Default 80.")
    private Integer alertThreshold;
    private BudgetMode monthlyMode;
}
