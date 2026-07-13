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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Credit invoice impact on MRR")
public class CreditImpactDto {
    @Schema(description = "Sum of all credit invoice amounts (negative value)")
    private BigDecimal totalCredits;

    @Schema(description = "Total number of credit invoices")
    private int creditInvoiceCount;

    @Schema(description = "Net effective MRR after credits (totalEffectiveMrr + totalCredits)")
    private BigDecimal netEffectiveMrr;

    @Schema(description = "Ratio of |totalCredits| to totalEffectiveMrr, null if totalEffectiveMrr is zero")
    private BigDecimal creditToMrrRatio;
}
