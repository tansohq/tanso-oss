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
package com.tansoflow.tansocore.model.credit.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Schema(description = "Batch tariff publish: all entries share one effectiveFrom and commit in one transaction. Features omitted from the batch keep their current weights; resetting to default requires an explicit 1.0 entry.")
public class PublishCreditWeightsRequest {

    @NotNull
    @Schema(description = "Instant the tariff takes effect. Must not be in the past. All entries share it.")
    private Instant effectiveFrom;

    @NotEmpty
    @Valid
    @Schema(description = "Weight entries to publish")
    private List<Entry> entries;

    @Data
    @Schema(description = "One weight entry in a tariff publish")
    public static class Entry {
        @NotNull
        @Schema(description = "Feature the weight applies to")
        private String featureId;

        @Schema(description = "Model the weight applies to. Null or blank = feature default for all models. Must exactly match the model string sent on events.")
        private String model;

        @NotNull
        @Schema(description = "Credits burned per usage unit. Positive, max 1,000,000, up to 6 decimals.")
        private BigDecimal creditsPerUnit;
    }
}
