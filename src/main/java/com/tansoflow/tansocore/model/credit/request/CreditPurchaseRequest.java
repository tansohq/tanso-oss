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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreditPurchaseRequest {
    @Schema(description = "Customer reference. Optional for customer-scoped keys (their own).")
    private String customerReferenceId;

    @NotBlank
    @Schema(description = "Credit pool to top up (must belong to the customer)")
    private String creditPoolId;

    @NotNull
    @Positive
    @Schema(description = "How many credits to buy; priced at the current price book rate")
    private BigDecimal credits;

    @Schema(description = "Stripe payment method (pm_...). Falls back to the customer's saved default; "
            + "with neither, the response is a 402 with a hosted checkout URL.")
    private String paymentMethodId;
}
