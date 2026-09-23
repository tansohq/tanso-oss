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
package com.tansoflow.tansocore.integration.stripe.implementation;

import com.stripe.StripeClient;
import com.stripe.model.Price;
import com.stripe.param.SubscriptionCreateParams;
import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.entity.StripeCustomer;
import com.tansoflow.tansocore.entity.StripePrice;
import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.integration.stripe.StripeClientFactory;
import com.tansoflow.tansocore.repository.StripeCustomerRepository;
import com.tansoflow.tansocore.repository.StripePriceRepository;
import com.tansoflow.tansocore.repository.StripeSubscriptionRepository;
import com.tansoflow.tansocore.repository.SubscriptionRepository;
import com.tansoflow.tansocore.service.internal.monetization.InvoiceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The Stripe subscription Tanso creates when a subscription activates on a Stripe-billed account. */
@ExtendWith(MockitoExtension.class)
class StripeSyncServiceImplCreateSubscriptionTest {

    @InjectMocks
    private StripeSyncServiceImpl stripeSyncService;

    @Mock
    private StripeClientFactory stripeClientFactory;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private StripeClient stripeClient;
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private StripeSubscriptionRepository stripeSubscriptionRepository;
    @Mock
    private StripeCustomerRepository stripeCustomerRepository;
    @Mock
    private StripePriceRepository stripePriceRepository;
    @Mock
    private InvoiceService invoiceService;

    private Price price(String id, String usageType) {
        Price.Recurring recurring = new Price.Recurring();
        recurring.setUsageType(usageType);
        Price price = new Price();
        price.setId(id);
        price.setRecurring(recurring);
        return price;
    }

    private StripePrice stripePrice(String id) {
        StripePrice price = new StripePrice();
        price.setStripePriceExternalId(id);
        return price;
    }

    // A paid plan with a usage-priced feature has a metered price and a licensed base price, saved last. Only the
    // newest price went on the subscription, so Stripe billed the base fee and never the usage.
    @Test
    void aPlanWithAUsagePricedFeatureGetsItsMeteredAndItsBasePrice() throws Exception {
        UUID accountId = UUID.randomUUID();
        Account account = new Account();
        account.setId(accountId);
        Plan plan = new Plan();
        plan.setId(UUID.randomUUID());
        plan.setPriceAmount(new BigDecimal("20"));
        plan.setBillingTiming("IN_ARREARS");
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setAccount(account);
        subscription.setCustomer(customer);
        subscription.setPlan(plan);
        subscription.setIsActive(true);
        StripeCustomer stripeCustomer = new StripeCustomer();
        stripeCustomer.setStripeCustomerExternalId("cus_123");

        when(stripeClientFactory.forAccount(accountId)).thenReturn(stripeClient);
        when(subscriptionRepository.findSubscriptionByUuidAndAccountId(subscription.getId(), accountId)).thenReturn(subscription);
        when(stripeCustomerRepository.findByCustomer(customer)).thenReturn(stripeCustomer);
        when(stripePriceRepository.findAllByPlanAndAccountOrderByCreatedAtDesc(plan, account))
                .thenReturn(List.of(stripePrice("price_base"), stripePrice("price_metered")));
        when(stripeClient.v1().prices().retrieve("price_base")).thenReturn(price("price_base", "licensed"));
        when(stripeClient.v1().prices().retrieve("price_metered")).thenReturn(price("price_metered", "metered"));
        com.stripe.model.Subscription created = new com.stripe.model.Subscription();
        created.setId("sub_123");
        when(stripeClient.v1().subscriptions().create(org.mockito.ArgumentMatchers.any(SubscriptionCreateParams.class)))
                .thenReturn(created);

        stripeSyncService.createStripeSubscription(subscription.getId(), accountId);

        ArgumentCaptor<SubscriptionCreateParams> params = ArgumentCaptor.forClass(SubscriptionCreateParams.class);
        verify(stripeClient.v1().subscriptions()).create(params.capture());
        List<SubscriptionCreateParams.Item> items = params.getValue().getItems();
        assertThat(items).extracting(SubscriptionCreateParams.Item::getPrice)
                .containsExactlyInAnyOrder("price_base", "price_metered");
        assertThat(items).filteredOn(i -> "price_metered".equals(i.getPrice()))
                .extracting(SubscriptionCreateParams.Item::getQuantity).containsOnlyNulls();
    }
}
