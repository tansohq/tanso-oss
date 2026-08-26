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

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class SpendBudgetBumpRequest {
    @NotNull
    @Schema(description = "The monthly ceiling in cents while the bump lasts. Must be above the standing ceiling.")
    private BigDecimal monthlyCents;
    @NotNull
    @Schema(description = "When the bump ends and the standing ceiling applies again.")
    private Instant expiresAt;
    @jakarta.validation.constraints.Size(max = 255)
    @Schema(description = "Why — shown on the budget and in the digest.")
    private String reason;
}
