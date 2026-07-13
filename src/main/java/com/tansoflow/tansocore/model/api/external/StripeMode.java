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
package com.tansoflow.tansocore.model.api.external;

public enum StripeMode {
    NONE,
    PAYMENT_PASS_THROUGH,
    /** @deprecated Use {@link #STRIPE_INTEGRATION} instead. Kept for backward compatibility. */
    @Deprecated FULL_SYNC,
    STRIPE_INTEGRATION,
    STRIPE_DRIVEN;

    /**
     * Returns true when this mode manages Stripe subscriptions, products, prices, and meters
     * (i.e. FULL_SYNC or STRIPE_INTEGRATION).
     */
    public boolean isStripeIntegration() {
        return this == FULL_SYNC || this == STRIPE_INTEGRATION;
    }
}
