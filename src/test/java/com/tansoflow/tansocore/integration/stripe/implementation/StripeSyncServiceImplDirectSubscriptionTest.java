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
import com.stripe.net.RequestOptions;
import com.stripe.param.SubscriptionCreateParams;
import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.CheckoutSession;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.entity.StripeCustomer;
import com.tansoflow.tansocore.entity.StripePrice;
import com.tansoflow.tansocore.integration.stripe.StripeClientFactory;
import com.tansoflow.tansocore.repository.StripeCustomerRepository;
import com.tansoflow.tansocore.repository.StripePriceRepository;
import com.tansoflow.tansocore.service.internal.account.CustomerService;
import com.tansoflow.tansocore.service.internal.monetization.PlanService;
import com.tansoflow.tansocore.util.TestTransactionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The saved-card subscribe: what Tanso sends Stripe, and when. */
@ExtendWith(MockitoExtension.class)
class StripeSyncServiceImplDirectSubscriptionTest {

    @InjectMocks
    private StripeSyncServiceImpl stripeSyncService;

    @Mock
    private StripeClientFactory stripeClientFactory;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private StripeClient stripeClient;
    @Mock
    private CustomerService customerService;
    @Mock
    private StripeCustomerRepository stripeCustomerRepository;
    @Mock
    private PlanService planService;
    @Mock
    private StripePriceRepository stripePriceRepository;

    private final TestTransactionManager transactionManager = new TestTransactionManager();
    @Spy
    private TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

    // The create charges the card. It used to run inside subscribe's transaction, with no idempotency key, so a failed
    // commit or a retry could leave a paid Stripe subscription Tanso never recorded, or charge a second one.
    @Test
    void theChargingCreateRunsOutsideAnyTransactionKeyedAndLabelledWithThePendingCharge() throws Exception {
        UUID accountId = UUID.randomUUID();
        Account account = new Account();
        account.setId(accountId);
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setAccount(account);
        Plan plan = new Plan();
        plan.setId(UUID.randomUUID());
        StripeCustomer stripeCustomer = new StripeCustomer();
        stripeCustomer.setStripeCustomerExternalId("cus_1");
        StripePrice stripePrice = new StripePrice();
        stripePrice.setStripePriceExternalId("price_1");
        UUID pendingChargeId = UUID.randomUUID();

        when(stripeClientFactory.forAccount(accountId)).thenReturn(stripeClient);
        when(customerService.validateAndRetrieveCustomer(customer.getId().toString(), accountId.toString())).thenReturn(customer);
        when(stripeCustomerRepository.findByCustomer(customer)).thenReturn(stripeCustomer);
        when(planService.retrievePlan(accountId, plan.getId())).thenReturn(plan);
        when(stripePriceRepository.findAllByPlanAndAccount(plan, account)).thenReturn(List.of(stripePrice));
        when(stripeClient.v1().prices().retrieve("price_1")).thenReturn(new Price());
        when(stripeClient.v1().subscriptions().create(any(SubscriptionCreateParams.class), any(RequestOptions.class)))
                .thenAnswer(i -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                            .as("Stripe charged inside a transaction").isFalse();
                    com.stripe.model.Subscription created = new com.stripe.model.Subscription();
                    created.setId("sub_direct_1");
                    return created;
                });

        stripeSyncService.createDirectSubscription(accountId, customer.getId(), plan.getId(), "pm_saved", pendingChargeId);

        ArgumentCaptor<SubscriptionCreateParams> params = ArgumentCaptor.forClass(SubscriptionCreateParams.class);
        ArgumentCaptor<RequestOptions> options = ArgumentCaptor.forClass(RequestOptions.class);
        verify(stripeClient.v1().subscriptions()).create(params.capture(), options.capture());
        assertThat(options.getValue().getIdempotencyKey()).isEqualTo("tanso-subscribe-" + pendingChargeId);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> metadata = (java.util.Map<String, Object>) params.getValue().getMetadata();
        assertThat(metadata).containsEntry(CheckoutSession.DIRECT_CHARGE_METADATA_KEY, pendingChargeId.toString());
        assertThat(params.getValue().getOffSession()).isTrue();
        // The lookups ran in their own transaction, which had ended by the time Stripe was called.
        assertThat(transactionManager.commits()).isEqualTo(1);
    }
}
