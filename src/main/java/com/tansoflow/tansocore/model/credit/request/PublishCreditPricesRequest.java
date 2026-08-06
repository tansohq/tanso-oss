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
@Schema(description = "Batch price publish: all entries share one effectiveFrom and commit in one transaction. Denominations omitted from the batch keep their current price; a denomination with no rows at all is unpriced.")
public class PublishCreditPricesRequest {

    @NotNull
    @Schema(description = "Instant the prices take effect. Must not be in the past. All entries share it.")
    private Instant effectiveFrom;

    @NotEmpty
    @Valid
    @Schema(description = "Price entries to publish")
    private List<Entry> entries;

    @Data
    @Schema(description = "One price entry in a price publish")
    public static class Entry {
        @NotNull
        @Schema(description = "Credit denomination the price applies to. Must match a credit model's denomination on this account.")
        private String denomination;

        @Schema(description = "ISO 4217 currency code. Defaults to USD.")
        private String currency;

        @NotNull
        @Schema(description = "Price of one credit. Positive, max 1,000,000, up to 6 decimals.")
        private BigDecimal pricePerCredit;
    }
}
