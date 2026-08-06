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

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreditPurchaseResult {
    @Schema(description = "True when the charge succeeded and credits are granted")
    private boolean completed;
    private BigDecimal credits;
    private BigDecimal pricePerCredit;
    private BigDecimal amountCharged;
    private String currency;
    private String grantId;
    private String paymentIntentId;
    @Schema(description = "Set on the 402 fallback: hand this URL to the principal")
    private String checkoutUrl;
    @Schema(description = "Poll GET /api/v1/client/checkout-sessions/{id} for the outcome")
    private String checkoutSessionId;
    private String declineReason;
}
