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
package com.tansoflow.tansocore.model.credit;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Schema(description = "One row of the credit tariff: credits burned per usage unit for a feature (optionally per model)")
public class CreditFeatureWeightDto {
    @Schema(description = "Unique identifier of the weight row")
    private String id;

    @Schema(description = "Feature this weight applies to")
    private String featureId;

    @Schema(description = "Feature key, for display")
    private String featureKey;

    @Schema(description = "Model this weight applies to. Null = feature default for all models. Must exactly match the model string sent on events.")
    private String model;

    @Schema(description = "Credits burned per usage unit")
    private BigDecimal creditsPerUnit;

    @Schema(description = "Instant this row takes effect. Rows never change once effective; publish a new row to reprice.")
    private Instant effectiveFrom;

    @Schema(description = "Admin user who published this row")
    private String createdBy;

    @Schema(description = "Timestamp when the row was created")
    private Instant createdAt;
}
