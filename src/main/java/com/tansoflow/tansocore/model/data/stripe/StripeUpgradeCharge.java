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
package com.tansoflow.tansocore.model.data.stripe;

import java.math.BigDecimal;

/**
 * What Stripe did with a charge-first price change.
 *
 * @param stripeInvoiceId  the proration invoice Stripe raised for the change
 * @param hostedInvoiceUrl where a human pays that invoice when the saved card could not be charged
 * @param amountPaid       what the invoice collected, in major units; zero while unpaid
 * @param applied          true when Stripe took the money and moved the subscription to the new price;
 *                         false when it left a pending_update waiting on the invoice
 */
public record StripeUpgradeCharge(String stripeInvoiceId, String hostedInvoiceUrl, BigDecimal amountPaid, boolean applied) {
}
