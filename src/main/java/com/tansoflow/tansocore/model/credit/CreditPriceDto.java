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
@Schema(description = "One row of the credit price book: what one credit of a denomination costs the buyer")
public class CreditPriceDto {
    @Schema(description = "Unique identifier of the price row")
    private String id;

    @Schema(description = "Credit denomination this price applies to (matches a credit model's denomination)")
    private String denomination;

    @Schema(description = "ISO 4217 currency code the price is stated in")
    private String currency;

    @Schema(description = "Price of one credit")
    private BigDecimal pricePerCredit;

    @Schema(description = "Instant this row takes effect. Rows never change once effective; publish a new row to reprice.")
    private Instant effectiveFrom;

    @Schema(description = "Admin user who published this row")
    private String createdBy;

    @Schema(description = "Timestamp when the row was created")
    private Instant createdAt;
}
