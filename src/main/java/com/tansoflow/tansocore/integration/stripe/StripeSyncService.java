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
import com.stripe.model.Price;
import com.stripe.model.Product;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.entity.StripeCustomer;
import com.tansoflow.tansocore.entity.StripeInvoice;
import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.model.data.stripe.StripePaymentLinkDto;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface StripeSyncService {
    void syncStripeSubscriptionTansoSubscription(String stripeSubscriptionId, String tansoSubscription, String accountId);

    void saveStripeInvoice(String stripeInvoiceId, String tansoInvoiceId, String accountId);

    StripeCustomer syncStripeCustomerTansoCustomer(String stripeCustomerId, String tansoCustomer, String accountId);

    StripeCustomer createStripeCustomer(UUID accountId, UUID tansoCustomerId) throws StripeException;

    StripePaymentLinkDto syncNewInvoice(UUID invoiceId, UUID accountId) throws StripeException;

    StripePaymentLinkDto retrieveStripeInvoiceHostedUrl(String invoiceId, String accountId) throws StripeException;

    @Transactional
    StripePaymentLinkDto retrieveStripeSession(String invoiceId, String accountId) throws StripeException;

    Product createStripeProduct(String planId, String accountId) throws StripeException;

    Price createStripePrice(Plan plan, String productId) throws StripeException;

    boolean isSubscriptionLinked(Subscription subscription);

    StripePaymentLinkDto updateCustomerPayment(String accountId, String customerId) throws StripeException;

    /** Returns the payment method id that became the default. */
    String syncNewPaymentAsDefault(String setupIntentId, String accountId, String stripeCustomerId) throws StripeException;

    boolean stripeInvoiceLinked(String stripeInvoiceId);

    /** Pushes a changed email to the mirrored Stripe customer, if one exists. No-op otherwise. */
    void syncCustomerEmail(UUID accountId, UUID customerId, String email) throws StripeException;

    StripeInvoice retrieveStripeInvoiceLinkedData(String stripeInvoiceId);

    StripePaymentLinkDto createSubscriptionCheckoutSession(UUID accountId, UUID customerId, UUID planId) throws StripeException;

    com.stripe.model.Subscription createDirectSubscription(UUID accountId, UUID customerId, UUID planId, String paymentMethodId) throws StripeException;

    // STRIPE_INTEGRATION methods
    void createStripeProductWithPrices(UUID planId, UUID accountId) throws StripeException;

    void createStripeSubscription(UUID subscriptionId, UUID accountId) throws StripeException;

    /** Moves the Stripe subscription to planId's price. planId is explicit: an upgrade may not have swapped the Tanso plan yet. */
    void updateStripeSubscriptionPrice(UUID subscriptionId, UUID accountId, UUID planId, boolean prorate) throws StripeException;

    /**
     * Charge-first upgrade (STRIPE_INTEGRATION, and agent keys on STRIPE_DRIVEN): invoices the proration now
     * (always_invoice). On a charge_automatically subscription Stripe moves to planId's price only if that charge
     * succeeds (pending_if_incomplete), otherwise it keeps the old price with a pending_update waiting on the
     * returned invoice. A send_invoice subscription cannot hold a pending_update, so Stripe moves the price and
     * sends the invoice; the result is applied only once that invoice is paid.
     */
    com.tansoflow.tansocore.model.data.stripe.StripeUpgradeCharge chargeUpgradeBeforeApplying(UUID subscriptionId, UUID accountId, UUID planId) throws StripeException;

    /** Voids a Stripe invoice by its Stripe id. Voiding the invoice behind a pending_update discards that update. */
    void voidStripeInvoice(String stripeInvoiceId, UUID accountId) throws StripeException;

    /**
     * Drops a charge-first upgrade nobody paid for: voids its Stripe invoice, and on a send_invoice subscription,
     * where Stripe had already moved to the new price, puts the subscription back on the Tanso plan's price
     * without proration.
     */
    void cancelUnpaidUpgrade(String stripeInvoiceId, UUID subscriptionId, UUID accountId) throws StripeException;

    void cancelStripeSubscription(UUID subscriptionId, UUID accountId, String cancelMode) throws StripeException;

    void createStripeMeter(UUID featureId, Plan plan, UUID accountId) throws StripeException;

    void forwardUsageToStripeMeter(UUID eventFeatureId, UUID customerId, UUID accountId, BigDecimal usageUnits, Instant timestamp) throws StripeException;

    void disableAutoAdvanceOnStripeInvoice(String stripeInvoiceId, UUID accountId) throws StripeException;

    /** Voids the mirrored Stripe invoice for a Tanso invoice, so a human cannot pay a charge nobody can fulfil. */
    void voidStripeInvoiceFor(UUID tansoInvoiceId, UUID accountId) throws StripeException;

    void addLineItemToDraftInvoice(String stripeInvoiceId, UUID accountId, BigDecimal amount, String currency, String description) throws StripeException;

    void finalizeAndPayStripeInvoice(String stripeInvoiceId, UUID accountId) throws StripeException;
}
