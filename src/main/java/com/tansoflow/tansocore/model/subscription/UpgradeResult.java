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
package com.tansoflow.tansocore.model.subscription;

import java.util.UUID;

/**
 * The outcome of an upgrade call. When the upgrade waits on payment, exactly one of the fields is set:
 * pendingInvoiceId for a Tanso adjustment invoice (Tanso collects), stripePaymentUrl for a Stripe invoice
 * not paid yet (STRIPE_DRIVEN, STRIPE_INTEGRATION): the saved card was not charged, or the subscription is billed
 * by send_invoice. Both null means nothing is waiting on payment.
 */
public record UpgradeResult(UUID pendingInvoiceId, String stripePaymentUrl) {

    public static UpgradeResult notWaiting() {
        return new UpgradeResult(null, null);
    }

    public static UpgradeResult waitingOnInvoice(UUID pendingInvoiceId) {
        return new UpgradeResult(pendingInvoiceId, null);
    }

    public static UpgradeResult waitingOnStripe(String stripePaymentUrl) {
        return new UpgradeResult(null, stripePaymentUrl);
    }

    public boolean waitingOnPayment() {
        return pendingInvoiceId != null || stripePaymentUrl != null;
    }
}
