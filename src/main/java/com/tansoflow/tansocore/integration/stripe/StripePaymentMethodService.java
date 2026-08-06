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
package com.tansoflow.tansocore.integration.stripe;

import com.stripe.exception.StripeException;

import java.math.BigDecimal;
import java.util.UUID;

public interface StripePaymentMethodService {

    record SetupIntentResult(String setupIntentId, String clientSecret, String stripeCustomerId) {
    }

    record PaymentResult(boolean succeeded, String paymentIntentId, String failureMessage) {
    }

    /**
     * Creates an off_session SetupIntent for the customer. The agent's
     * principal confirms it directly with Stripe using the client secret —
     * card data never touches Tanso.
     */
    SetupIntentResult createSetupIntent(UUID accountId, UUID customerId) throws StripeException;

    /** Attaches the payment method to the customer's Stripe customer and stores it as their default. */
    void setDefaultPaymentMethod(UUID accountId, UUID customerId, String paymentMethodId) throws StripeException;

    /**
     * Charges an off_session PaymentIntent with the given payment method,
     * confirmed synchronously. Enforces the account's agent spend cap before
     * any money moves. Returns a failed result (never throws) for declines
     * and SCA challenges so the caller can fall back to hosted checkout.
     */
    PaymentResult chargeOffSession(UUID accountId, UUID customerId, String paymentMethodId,
                                   BigDecimal amount, String currency, String description,
                                   java.util.Map<String, String> metadata) throws StripeException;

    record HostedCheckout(String url, String stripeSessionId) {
    }

    /** Hosted one-off payment fallback (mode=payment) for credit top-ups when no payment method is on file. */
    HostedCheckout createTopupCheckoutSession(UUID accountId, UUID customerId, BigDecimal amount,
                                              String currency, String description,
                                              java.util.Map<String, String> metadata) throws StripeException;
}
