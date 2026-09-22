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
package com.tansoflow.tansocore.model.subscription.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Returned to tenant-key (sk_) callers when an upgrade is accepted but waits on payment. Customer-key (ck_) callers
 * get the 402 gate envelope instead.
 */
@Schema(description = "An upgrade that waits on payment. The plan changes when the invoice at paymentUrl is paid.")
public record PlanChangeResponse(
        @Schema(description = "Always payment_pending", example = "payment_pending") String status,
        @Schema(description = "Stripe's hosted invoice for the prorated charge") String paymentUrl) {

    public static PlanChangeResponse paymentPending(String paymentUrl) {
        return new PlanChangeResponse("payment_pending", paymentUrl);
    }
}
