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
package com.tansoflow.tansocore.model.apikey;

import com.tansoflow.tansocore.model.apikey.type.BudgetPeriod;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@Schema(description = "What this API key may spend, and what it has spent in the current window")
public class KeyBudgetDto {

    private UUID keyId;

    @Schema(description = "Rolling window the budget is measured over. Null when no budget is set.")
    private BudgetPeriod period;

    @Schema(description = "Credits this key may consume per window. Null means unlimited.")
    private BigDecimal creditLimit;

    @Schema(description = "Credits consumed by this key in the current window")
    private BigDecimal creditsSpent;

    @Schema(description = "Credits still available to this key. Null when the credit budget is unlimited.")
    private BigDecimal creditsRemaining;

    @Schema(description = "Money this key may spend on top-ups per window. Null means unlimited.")
    private BigDecimal amountLimit;

    @Schema(description = "Money spent by this key in the current window")
    private BigDecimal amountSpent;

    @Schema(description = "Money still available to this key. Null when the spend budget is unlimited.")
    private BigDecimal amountRemaining;

    @Schema(description = "Start of the current window")
    private Instant windowStart;

    @Schema(description = "When the current window rolls over. Null for a TOTAL budget, which never resets.")
    private Instant resetsAt;
}
