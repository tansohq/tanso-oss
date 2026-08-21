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
package com.tansoflow.tansocore.model.apikey.request;

import com.tansoflow.tansocore.model.apikey.type.BudgetPeriod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Set this key's spend budget. Omit an axis to leave it unlimited.")
public class UpdateKeyBudgetRequest {

    @NotNull
    @Schema(description = "Window the budget is measured over", example = "MONTH")
    private BudgetPeriod period;

    @Schema(description = "Credits this key may consume per window. Omit or null for unlimited.", example = "50000")
    private BigDecimal creditLimit;

    @Schema(description = "Money this key may spend on top-ups per window. Omit or null for unlimited.", example = "500.00")
    private BigDecimal amountLimit;
}
