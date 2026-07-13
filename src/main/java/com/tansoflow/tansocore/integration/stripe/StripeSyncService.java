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

    void syncNewPaymentAsDefault(String setupIntentId, String accountId, String stripeCustomerId) throws StripeException;

    boolean stripeInvoiceLinked(String stripeInvoiceId);

    StripeInvoice retrieveStripeInvoiceLinkedData(String stripeInvoiceId);

    StripePaymentLinkDto createSubscriptionCheckoutSession(UUID accountId, UUID customerId, UUID planId) throws StripeException;

    // STRIPE_INTEGRATION methods
    void createStripeProductWithPrices(UUID planId, UUID accountId) throws StripeException;

    void createStripeSubscription(UUID subscriptionId, UUID accountId) throws StripeException;

    void updateStripeSubscriptionPrice(UUID subscriptionId, UUID accountId, boolean prorate) throws StripeException;

    void cancelStripeSubscription(UUID subscriptionId, UUID accountId, String cancelMode) throws StripeException;

    void createStripeMeter(UUID featureId, Plan plan, UUID accountId) throws StripeException;

    void forwardUsageToStripeMeter(UUID eventFeatureId, UUID customerId, UUID accountId, BigDecimal usageUnits, Instant timestamp) throws StripeException;

    void disableAutoAdvanceOnStripeInvoice(String stripeInvoiceId, UUID accountId) throws StripeException;

    void addLineItemToDraftInvoice(String stripeInvoiceId, UUID accountId, BigDecimal amount, String currency, String description) throws StripeException;

    void finalizeAndPayStripeInvoice(String stripeInvoiceId, UUID accountId) throws StripeException;
}
