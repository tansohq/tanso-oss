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
import com.stripe.model.SubscriptionItem;
import com.stripe.model.SubscriptionItemCollection;
import com.stripe.net.RequestOptions;
import com.stripe.param.SubscriptionUpdateParams;
import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.entity.StripePrice;
import com.tansoflow.tansocore.entity.StripeSubscription;
import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.integration.stripe.StripeClientFactory;
import com.tansoflow.tansocore.model.data.stripe.StripeUpgradeCharge;
import com.tansoflow.tansocore.repository.StripePriceRepository;
import com.tansoflow.tansocore.repository.StripeSubscriptionRepository;
import com.tansoflow.tansocore.repository.SubscriptionRepository;
import com.tansoflow.tansocore.service.internal.monetization.PlanService;
import com.tansoflow.tansocore.util.TestTransactionManager;
import org.junit.jupiter.api.BeforeEach;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** What Tanso sends Stripe when a subscription changes plan. */
@ExtendWith(MockitoExtension.class)
class StripeSyncServiceImplPlanChangeTest {

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
    private StripePriceRepository stripePriceRepository;
    @Mock
    private PlanService planService;

    private final TestTransactionManager transactionManager = new TestTransactionManager();
    @Spy
    private TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

    private final UUID accountId = UUID.randomUUID();
    private final UUID changeId = UUID.randomUUID();
    private Account account;
    private Plan oldPlan;
    private Plan newPlan;
    private Subscription subscription;
    private com.stripe.model.Subscription current;

    @BeforeEach
    void setUp() throws Exception {
        account = new Account();
        account.setId(accountId);
        oldPlan = new Plan();
        oldPlan.setId(UUID.randomUUID());
        newPlan = new Plan();
        newPlan.setId(UUID.randomUUID());

        subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setAccount(account);
        // Not swapped yet: the Tanso subscription still sits on the old plan while the upgrade waits on payment.
        subscription.setPlan(oldPlan);

        StripeSubscription bridge = new StripeSubscription();
        bridge.setStripeSubscriptionExternalId("sub_123");

        StripePrice oldPrice = new StripePrice();
        oldPrice.setStripePriceExternalId("price_old");
        StripePrice newPrice = new StripePrice();
        newPrice.setStripePriceExternalId("price_new");

        when(stripeClientFactory.forAccount(accountId)).thenReturn(stripeClient);
        when(subscriptionRepository.findSubscriptionByUuidAndAccountId(subscription.getId(), accountId)).thenReturn(subscription);
        when(stripeSubscriptionRepository.findStripeSubscriptionBySubscription(subscription)).thenReturn(bridge);
        org.mockito.Mockito.lenient().when(planService.retrievePlan(account, newPlan.getId())).thenReturn(newPlan);
        org.mockito.Mockito.lenient().when(stripePriceRepository.findFirstByPlanAndAccountOrderByCreatedAtDesc(oldPlan, account))
                .thenReturn(Optional.of(oldPrice));
        org.mockito.Mockito.lenient().when(stripePriceRepository.findFirstByPlanAndAccountOrderByCreatedAtDesc(newPlan, account))
                .thenReturn(Optional.of(newPrice));

        SubscriptionItem item = new SubscriptionItem();
        item.setId("si_123");
        SubscriptionItemCollection items = new SubscriptionItemCollection();
        items.setData(List.of(item));
        current = new com.stripe.model.Subscription();
        current.setCollectionMethod("charge_automatically");
        current.setItems(items);
        when(stripeClient.v1().subscriptions().retrieve("sub_123")).thenReturn(current);
    }

    private com.stripe.model.Subscription stripeReply(boolean pendingUpdate, String invoiceStatus, long amountPaid) {
        com.stripe.model.Invoice invoice = new com.stripe.model.Invoice();
        invoice.setId("in_proration");
        invoice.setStatus(invoiceStatus);
        invoice.setAmountPaid(amountPaid);
        invoice.setHostedInvoiceUrl("https://invoice.stripe.com/i/acct_test/in_proration");
        com.stripe.model.Subscription updated = new com.stripe.model.Subscription();
        updated.setLatestInvoiceObject(invoice);
        if (pendingUpdate) {
            updated.setPendingUpdate(new com.stripe.model.Subscription.PendingUpdate());
        }
        return updated;
    }

    // The update that can charge the card used to run inside the caller's transaction and this method's own.
    @Test
    void theChargingUpdateRunsOutsideAnyTransactionAndIsKeyedOnTheScheduledChange() throws Exception {
        when(stripeClient.v1().subscriptions().update(eq("sub_123"), any(SubscriptionUpdateParams.class), any(RequestOptions.class)))
                .thenAnswer(i -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                            .as("Stripe charged inside a transaction").isFalse();
                    return stripeReply(false, "paid", 7450L);
                });

        // Called the way Spring wires it, so a @Transactional on the method would open a transaction here.
        org.springframework.aop.framework.ProxyFactory factory = new org.springframework.aop.framework.ProxyFactory(stripeSyncService);
        factory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(
                (org.springframework.transaction.TransactionManager) transactionManager,
                new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        ((com.tansoflow.tansocore.integration.stripe.StripeSyncService) factory.getProxy())
                .chargeUpgradeBeforeApplying(subscription.getId(), accountId, newPlan.getId(), changeId);

        ArgumentCaptor<RequestOptions> options = ArgumentCaptor.forClass(RequestOptions.class);
        verify(stripeClient.v1().subscriptions()).update(eq("sub_123"), any(SubscriptionUpdateParams.class), options.capture());
        assertThat(options.getValue().getIdempotencyKey()).isEqualTo("tanso-upgrade-" + changeId);
        // The lookups ran in their own transaction, which had ended by the time Stripe was called.
        assertThat(transactionManager.commits()).isEqualTo(1);
    }

    @Test
    void anAgentUpgradeIsChargedNowAndAppliedOnlyIfPaid() throws Exception {
        when(stripeClient.v1().subscriptions().update(eq("sub_123"), any(SubscriptionUpdateParams.class), any(RequestOptions.class)))
                .thenReturn(stripeReply(false, "paid", 7450L));

        StripeUpgradeCharge charge = stripeSyncService.chargeUpgradeBeforeApplying(subscription.getId(), accountId, newPlan.getId(), changeId);

        ArgumentCaptor<SubscriptionUpdateParams> params = ArgumentCaptor.forClass(SubscriptionUpdateParams.class);
        verify(stripeClient.v1().subscriptions()).update(eq("sub_123"), params.capture(), any(RequestOptions.class));
        assertThat(params.getValue().getProrationBehavior()).isEqualTo(SubscriptionUpdateParams.ProrationBehavior.ALWAYS_INVOICE);
        assertThat(params.getValue().getPaymentBehavior()).isEqualTo(SubscriptionUpdateParams.PaymentBehavior.PENDING_IF_INCOMPLETE);
        assertThat(params.getValue().getItems()).hasSize(1);
        assertThat(params.getValue().getItems().getFirst().getId()).isEqualTo("si_123");
        assertThat(params.getValue().getItems().getFirst().getPrice()).isEqualTo("price_new");

        assertThat(charge.applied()).isTrue();
        assertThat(charge.stripeInvoiceId()).isEqualTo("in_proration");
        assertThat(charge.amountPaid()).isEqualByComparingTo(new BigDecimal("74.50"));
    }

    @Test
    void aChargeStripeCouldNotTakeComesBackPendingWithTheHostedInvoice() throws Exception {
        when(stripeClient.v1().subscriptions().update(eq("sub_123"), any(SubscriptionUpdateParams.class), any(RequestOptions.class)))
                .thenReturn(stripeReply(true, "open", 0L));

        StripeUpgradeCharge charge = stripeSyncService.chargeUpgradeBeforeApplying(subscription.getId(), accountId, newPlan.getId(), changeId);

        assertThat(charge.applied()).isFalse();
        assertThat(charge.hostedInvoiceUrl()).isEqualTo("https://invoice.stripe.com/i/acct_test/in_proration");
        assertThat(charge.amountPaid()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // Stripe applied the price but the invoice is not paid: the plan must not move yet.
    @Test
    void aChargeAutomaticallyUpgradeWithAnUnpaidInvoiceIsNotApplied() throws Exception {
        when(stripeClient.v1().subscriptions().update(eq("sub_123"), any(SubscriptionUpdateParams.class), any(RequestOptions.class)))
                .thenReturn(stripeReply(false, "open", 0L));

        StripeUpgradeCharge charge = stripeSyncService.chargeUpgradeBeforeApplying(subscription.getId(), accountId, newPlan.getId(), changeId);

        assertThat(charge.applied()).isFalse();
    }

    // Stripe supports pending updates only on charge_automatically subscriptions; accumulate-mode plans are
    // send_invoice, so the update goes without pending_if_incomplete and waits on the emailed invoice.
    @Test
    void aSendInvoiceUpgradeIsInvoicedWithoutPendingUpdateAndWaitsForPayment() throws Exception {
        current.setCollectionMethod("send_invoice");
        when(stripeClient.v1().subscriptions().update(eq("sub_123"), any(SubscriptionUpdateParams.class), any(RequestOptions.class)))
                .thenReturn(stripeReply(false, "open", 0L));

        StripeUpgradeCharge charge = stripeSyncService.chargeUpgradeBeforeApplying(subscription.getId(), accountId, newPlan.getId(), changeId);

        ArgumentCaptor<SubscriptionUpdateParams> params = ArgumentCaptor.forClass(SubscriptionUpdateParams.class);
        verify(stripeClient.v1().subscriptions()).update(eq("sub_123"), params.capture(), any(RequestOptions.class));
        assertThat(params.getValue().getProrationBehavior()).isEqualTo(SubscriptionUpdateParams.ProrationBehavior.ALWAYS_INVOICE);
        assertThat(params.getValue().getPaymentBehavior()).isNull();
        assertThat(params.getValue().getItems().getFirst().getPrice()).isEqualTo("price_new");

        assertThat(charge.applied()).isFalse();
        assertThat(charge.stripeInvoiceId()).isEqualTo("in_proration");
        assertThat(charge.hostedInvoiceUrl()).isEqualTo("https://invoice.stripe.com/i/acct_test/in_proration");
    }

    @Test
    void aSendInvoiceUpgradeFinalizesADraftSoThereIsAPageToPay() throws Exception {
        current.setCollectionMethod("send_invoice");
        com.stripe.model.Subscription draftReply = stripeReply(false, "draft", 0L);
        draftReply.getLatestInvoiceObject().setHostedInvoiceUrl(null);
        when(stripeClient.v1().subscriptions().update(eq("sub_123"), any(SubscriptionUpdateParams.class), any(RequestOptions.class)))
                .thenReturn(draftReply);
        com.stripe.model.Invoice finalized = new com.stripe.model.Invoice();
        finalized.setId("in_proration");
        finalized.setStatus("open");
        finalized.setAmountPaid(0L);
        finalized.setHostedInvoiceUrl("https://invoice.stripe.com/i/acct_test/in_proration");
        when(stripeClient.v1().invoices().finalizeInvoice(eq("in_proration"), any(RequestOptions.class))).thenReturn(finalized);

        StripeUpgradeCharge charge = stripeSyncService.chargeUpgradeBeforeApplying(subscription.getId(), accountId, newPlan.getId(), changeId);

        assertThat(charge.applied()).isFalse();
        assertThat(charge.hostedInvoiceUrl()).isEqualTo("https://invoice.stripe.com/i/acct_test/in_proration");
    }

    @Test
    void aSendInvoiceUpgradeAlreadyPaidIsApplied() throws Exception {
        current.setCollectionMethod("send_invoice");
        when(stripeClient.v1().subscriptions().update(eq("sub_123"), any(SubscriptionUpdateParams.class), any(RequestOptions.class)))
                .thenReturn(stripeReply(false, "paid", 7450L));

        StripeUpgradeCharge charge = stripeSyncService.chargeUpgradeBeforeApplying(subscription.getId(), accountId, newPlan.getId(), changeId);

        assertThat(charge.applied()).isTrue();
        assertThat(charge.amountPaid()).isEqualByComparingTo(new BigDecimal("74.50"));
    }

    // Stripe moved a send_invoice subscription to the new price when it raised the invoice; dropping the upgrade
    // must put it back, or the next renewal bills a plan Tanso never granted.
    @Test
    void droppingAnUnpaidSendInvoiceUpgradeVoidsTheInvoiceAndRestoresTheOldPrice() throws Exception {
        current.setCollectionMethod("send_invoice");

        stripeSyncService.cancelUnpaidUpgrade("in_proration", subscription.getId(), accountId);

        verify(stripeClient.v1().invoices()).voidInvoice("in_proration");
        ArgumentCaptor<SubscriptionUpdateParams> params = ArgumentCaptor.forClass(SubscriptionUpdateParams.class);
        verify(stripeClient.v1().subscriptions()).update(eq("sub_123"), params.capture());
        assertThat(params.getValue().getItems().getFirst().getPrice()).isEqualTo("price_old");
        assertThat(params.getValue().getProrationBehavior()).isEqualTo(SubscriptionUpdateParams.ProrationBehavior.NONE);
    }

    @Test
    void droppingAnUnpaidChargeAutomaticallyUpgradeOnlyVoidsTheInvoice() throws Exception {
        stripeSyncService.cancelUnpaidUpgrade("in_proration", subscription.getId(), accountId);

        verify(stripeClient.v1().invoices()).voidInvoice("in_proration");
        verify(stripeClient.v1().subscriptions(), org.mockito.Mockito.never())
                .update(any(String.class), any(SubscriptionUpdateParams.class));
    }

    // The listener used to read subscription.getPlan(), which on STRIPE_INTEGRATION is still the old plan while the
    // upgrade waits on payment, so Stripe was sent the price it already had.
    @Test
    void aPlanChangePricesStripeOnTheNamedPlanNotTheSubscriptionsCurrentOne() throws Exception {
        stripeSyncService.updateStripeSubscriptionPrice(subscription.getId(), accountId, newPlan.getId(), true);

        ArgumentCaptor<SubscriptionUpdateParams> params = ArgumentCaptor.forClass(SubscriptionUpdateParams.class);
        verify(stripeClient.v1().subscriptions()).update(eq("sub_123"), params.capture());
        assertThat(params.getValue().getItems().getFirst().getPrice()).isEqualTo("price_new");
        assertThat(params.getValue().getProrationBehavior()).isEqualTo(SubscriptionUpdateParams.ProrationBehavior.CREATE_PRORATIONS);
        assertThat(params.getValue().getPaymentBehavior()).isNull();
    }
}
