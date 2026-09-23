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

import com.stripe.model.Invoice;
import com.stripe.model.InvoiceLineItem;
import com.stripe.model.InvoiceLineItemCollection;
import com.stripe.model.Price;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.SubscriptionItemCollection;
import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.entity.StripeCustomer;
import com.tansoflow.tansocore.entity.StripeInvoice;
import com.tansoflow.tansocore.entity.StripeProduct;
import com.tansoflow.tansocore.entity.StripeSubscription;
import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.model.billing.InvoiceDto;
import com.tansoflow.tansocore.model.billing.type.InvoiceStatus;
import com.tansoflow.tansocore.model.customer.CustomerDto;
import com.tansoflow.tansocore.repository.AccountRepository;
import com.tansoflow.tansocore.repository.AccountSettingRepository;
import com.tansoflow.tansocore.repository.ExternalApiKeyRepository;
import com.tansoflow.tansocore.repository.StripeCustomerRepository;
import com.tansoflow.tansocore.repository.StripeProductPlansRepository;
import com.tansoflow.tansocore.repository.StripeSubscriptionRepository;
import com.tansoflow.tansocore.repository.StripeWebhookEventRepository;
import com.tansoflow.tansocore.repository.SubscriptionRepository;
import com.tansoflow.tansocore.service.internal.account.CustomerService;
import com.tansoflow.tansocore.service.internal.monetization.EntitlementService;
import com.tansoflow.tansocore.service.internal.monetization.InvoiceService;
import com.tansoflow.tansocore.service.internal.monetization.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeWebhookImplTest {

    @InjectMocks
    private StripeWebhookImpl stripeWebhook;

    @Mock
    private StripeSyncServiceImpl stripeSyncService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ExternalApiKeyRepository externalApiKeyRepository;

    @Mock
    private InvoiceService invoiceService;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private AccountSettingRepository accountSettingRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private EntitlementService entitlementService;

    @Mock
    private StripeWebhookEventRepository stripeWebhookEventRepository;

    @Mock
    private StripeSubscriptionRepository stripeSubscriptionRepository;

    @Mock
    private com.tansoflow.tansocore.service.internal.monetization.CreditService creditService;

    @Mock
    private com.tansoflow.tansocore.repository.CreditPoolSubscriptionRepository creditPoolSubscriptionRepository;

    @Mock
    private StripeCustomerRepository stripeCustomerRepository;

    @Mock
    private StripeProductPlansRepository stripeProductPlansRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CustomerService customerService;

    @Mock
    private com.tansoflow.tansocore.repository.CheckoutSessionRepository checkoutSessionRepository;

    @Mock
    private com.tansoflow.tansocore.service.internal.account.KeyBudgetService keyBudgetService;

    @Mock
    private com.tansoflow.tansocore.service.client.AgentLifecycleService agentLifecycleService;

    @Mock
    private com.tansoflow.tansocore.repository.SubscriptionScheduledChangeRepository subscriptionScheduledChangeRepository;

    @Mock
    private com.tansoflow.tansocore.service.internal.monetization.PlanService planService;

    // Regression: a hosted checkout completes in a browser, so the webhook is the
    // only place the money can be charged back to the key that opened it. The
    // security context is gone by then, which is why the key and amount ride on
    // the checkout_sessions row.
    // Found by /qa on 2026-08-21
    // Report: .gstack/qa-reports/qa-report-tanso-oss-2026-08-21.md
    @Test
    void subscriptionCheckoutCompletionChargesTheKeyThatOpenedIt() throws Exception {
        UUID acct = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        com.tansoflow.tansocore.entity.CheckoutSession record = new com.tansoflow.tansocore.entity.CheckoutSession();
        record.setAccountId(acct);
        record.setApiKeyId(keyId);
        record.setAmount(new BigDecimal("9.00"));
        record.setPurpose(com.tansoflow.tansocore.entity.CheckoutSession.PURPOSE_SUBSCRIPTION);
        when(checkoutSessionRepository.findByStripeSessionId("cs_sub_1")).thenReturn(Optional.of(record));

        com.stripe.model.checkout.Session session = new com.stripe.model.checkout.Session();
        session.setId("cs_sub_1");
        session.setMode("subscription");

        stripeWebhook.handleSessionsComplete(session);

        // A human paid the page in person: it counts against the key's budget but not the mandate.
        verify(keyBudgetService).recordSpend(eq(acct), eq(keyId),
                eq(com.tansoflow.tansocore.model.apikey.type.SpendKind.MONEY),
                eq(com.tansoflow.tansocore.model.apikey.type.SpendChannel.HOSTED),
                eq(new BigDecimal("9.00")), eq("cs_sub_1"), eq("checkout_cs_sub_1"));
        assertEquals(com.tansoflow.tansocore.entity.CheckoutSession.STATUS_COMPLETED, record.getStatus());
    }

    @Test
    void creditTopupCheckoutCompletionChargesTheKeyThatOpenedIt() throws Exception {
        UUID acct = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        com.tansoflow.tansocore.entity.CheckoutSession record = new com.tansoflow.tansocore.entity.CheckoutSession();
        record.setAccountId(acct);
        record.setApiKeyId(keyId);
        record.setAmount(new BigDecimal("1.00"));
        record.setCredits(new BigDecimal("100"));
        record.setCreditPoolId(UUID.randomUUID());
        record.setPurpose(com.tansoflow.tansocore.entity.CheckoutSession.PURPOSE_CREDIT_TOPUP);
        when(checkoutSessionRepository.findByStripeSessionId("cs_top_1")).thenReturn(Optional.of(record));

        com.stripe.model.checkout.Session session = new com.stripe.model.checkout.Session();
        session.setId("cs_top_1");
        session.setMode("payment");

        stripeWebhook.handleSessionsComplete(session);

        verify(keyBudgetService).recordSpend(eq(acct), eq(keyId),
                eq(com.tansoflow.tansocore.model.apikey.type.SpendKind.MONEY),
                eq(com.tansoflow.tansocore.model.apikey.type.SpendChannel.HOSTED),
                eq(new BigDecimal("1.00")), eq("cs_top_1"), eq("checkout_cs_top_1"));
    }

    // A checkout opened from the console or a tenant key has no ck_ actor. The
    // ledger call still fires; KeyBudgetService is what no-ops on a null key, so
    // the webhook must not start guarding it itself and silently diverge.
    @Test
    void aCheckoutWithNoOwningKeyStillCompletesCleanly() throws Exception {
        UUID acct = UUID.randomUUID();
        com.tansoflow.tansocore.entity.CheckoutSession record = new com.tansoflow.tansocore.entity.CheckoutSession();
        record.setAccountId(acct);
        record.setApiKeyId(null);
        record.setAmount(null);
        record.setPurpose(com.tansoflow.tansocore.entity.CheckoutSession.PURPOSE_SUBSCRIPTION);
        when(checkoutSessionRepository.findByStripeSessionId("cs_sub_2")).thenReturn(Optional.of(record));

        com.stripe.model.checkout.Session session = new com.stripe.model.checkout.Session();
        session.setId("cs_sub_2");
        session.setMode("subscription");

        stripeWebhook.handleSessionsComplete(session);

        verify(keyBudgetService).recordSpend(eq(acct), eq(null),
                eq(com.tansoflow.tansocore.model.apikey.type.SpendKind.MONEY),
                eq(com.tansoflow.tansocore.model.apikey.type.SpendChannel.HOSTED),
                eq(null), eq("cs_sub_2"), eq("checkout_cs_sub_2"));
        assertEquals(com.tansoflow.tansocore.entity.CheckoutSession.STATUS_COMPLETED, record.getStatus());
    }

    private Account account;
    private Customer customer;
    private Plan plan;
    private Subscription subscription;
    private String accountId;

    @BeforeEach
    void setUp() {
        account = new Account();
        account.setId(UUID.randomUUID());
        accountId = account.getId().toString();

        customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setAccount(account);

        plan = new Plan();
        plan.setId(UUID.randomUUID());

        subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setCustomer(customer);
        subscription.setPlan(plan);
        subscription.setIsActive(true);
        subscription.setAccount(account);
        subscription.setCurrentPeriodStart(Instant.parse("2025-01-01T00:00:00Z"));
        subscription.setCurrentPeriodEnd(Instant.parse("2025-02-01T00:00:00Z"));
    }

    // ── Period advancement tests ──────────────────────────────────────────────

    @Test
    void handleFullSyncInvoiceCreated_UpdatesSubscriptionPeriodFromStripeInvoice() {
        Invoice stripeInvoice = createStripeInvoiceWithPeriod(
                "inv_001", 5000L,
                Instant.parse("2025-02-01T00:00:00Z").getEpochSecond(),
                Instant.parse("2025-03-01T00:00:00Z").getEpochSecond());
        stripeInvoice.setMetadata(Map.of("tanso_subscription_id", subscription.getId().toString()));

        when(stripeSyncService.stripeInvoiceLinked("inv_001")).thenReturn(false);
        when(subscriptionService.getSubscriptionById(subscription.getId().toString(), accountId))
                .thenReturn(subscription);
        when(invoiceService.planHasAccumulateModeFeatures(plan)).thenReturn(false);
        when(invoiceService.createInvoiceFromStripe(any(Subscription.class), any(), any(BigDecimal.class), any(InvoiceStatus.class), any(Instant.class), any(Instant.class), anyList()))
                .thenReturn(createInvoiceDto(UUID.randomUUID().toString()));

        stripeWebhook.handleFullSyncInvoiceCreated(stripeInvoice, accountId);

        // Verify period was updated on subscription
        assertEquals(Instant.parse("2025-02-01T00:00:00Z"), subscription.getCurrentPeriodStart());
        assertEquals(Instant.parse("2025-03-01T00:00:00Z"), subscription.getCurrentPeriodEnd());
        verify(subscriptionRepository).save(subscription);
    }

    @Test
    void handleFullSyncInvoiceCreated_AccumulateMode_UsesCorrectPeriodForChargeCalculation() {
        Instant newPeriodStart = Instant.parse("2025-02-01T00:00:00Z");
        Instant newPeriodEnd = Instant.parse("2025-03-01T00:00:00Z");

        Invoice stripeInvoice = createStripeInvoiceWithPeriod(
                "inv_002", 0L,
                newPeriodStart.getEpochSecond(), newPeriodEnd.getEpochSecond());
        stripeInvoice.setMetadata(Map.of("tanso_subscription_id", subscription.getId().toString()));

        when(stripeSyncService.stripeInvoiceLinked("inv_002")).thenReturn(false);
        when(subscriptionService.getSubscriptionById(subscription.getId().toString(), accountId))
                .thenReturn(subscription);
        when(invoiceService.planHasAccumulateModeFeatures(plan)).thenReturn(true);
        when(invoiceService.calculateUsageChargeForPeriod(eq(subscription), eq(newPeriodStart), eq(newPeriodEnd)))
                .thenReturn(new BigDecimal("35.00"));
        when(invoiceService.createNewInvoice(any(Subscription.class), any(), any(BigDecimal.class), any(InvoiceStatus.class), any(Instant.class), any(Instant.class)))
                .thenReturn(createInvoiceDto(UUID.randomUUID().toString()));

        stripeWebhook.handleFullSyncInvoiceCreated(stripeInvoice, accountId);

        // Verify charge was calculated with the NEW period dates, not the stale ones
        verify(invoiceService).calculateUsageChargeForPeriod(subscription, newPeriodStart, newPeriodEnd);
    }

    @Test
    void handleFullSyncSubscriptionUpdated_SyncsPeriodDatesFromItems() {
        // In Stripe SDK v31+, period is per-SubscriptionItem, not on the Subscription itself
        SubscriptionItem subItem = new SubscriptionItem();
        subItem.setCurrentPeriodStart(Instant.parse("2025-03-01T00:00:00Z").getEpochSecond());
        subItem.setCurrentPeriodEnd(Instant.parse("2025-04-01T00:00:00Z").getEpochSecond());

        SubscriptionItemCollection items = new SubscriptionItemCollection();
        items.setData(List.of(subItem));

        com.stripe.model.Subscription stripeSub = new com.stripe.model.Subscription();
        stripeSub.setMetadata(Map.of("tanso_subscription_id", subscription.getId().toString()));
        stripeSub.setItems(items);
        stripeSub.setStatus("active");

        when(subscriptionService.getSubscriptionById(subscription.getId().toString(), accountId))
                .thenReturn(subscription);

        stripeWebhook.handleFullSyncSubscriptionUpdated(stripeSub, accountId);

        assertEquals(Instant.parse("2025-03-01T00:00:00Z"), subscription.getCurrentPeriodStart());
        assertEquals(Instant.parse("2025-04-01T00:00:00Z"), subscription.getCurrentPeriodEnd());
        verify(subscriptionRepository).save(subscription);
    }

    // ── invoice.paid race condition tests ─────────────────────────────────────

    @Test
    void handleFullSyncInvoicePaid_AccumulateMode_AlreadyPaid_CreatesMirrorDirectly() {
        Invoice stripeInvoice = createStripeInvoiceWithPeriod(
                "inv_003", 3500L,
                Instant.parse("2025-02-01T00:00:00Z").getEpochSecond(),
                Instant.parse("2025-03-01T00:00:00Z").getEpochSecond());
        stripeInvoice.setMetadata(Map.of("tanso_subscription_id", subscription.getId().toString()));
        stripeInvoice.setStatus("paid");
        stripeInvoice.setAmountPaid(3500L);

        UUID tansoInvoiceId = UUID.randomUUID();
        com.tansoflow.tansocore.entity.Invoice tansoInvoice = new com.tansoflow.tansocore.entity.Invoice();
        tansoInvoice.setId(tansoInvoiceId);

        StripeInvoice stripeInvoiceEntity = new StripeInvoice();
        stripeInvoiceEntity.setInvoice(tansoInvoice);

        // Invoice not yet linked (race condition scenario)
        when(stripeSyncService.stripeInvoiceLinked("inv_003"))
                .thenReturn(false)   // First call in handleFullSyncInvoicePaid: not linked
                .thenReturn(true);   // After createMirrorInvoiceFromPaidStripeInvoice: now linked

        when(subscriptionService.getSubscriptionById(subscription.getId().toString(), accountId))
                .thenReturn(subscription);
        when(invoiceService.planHasAccumulateModeFeatures(plan)).thenReturn(true);
        when(invoiceService.createNewInvoice(any(Subscription.class), any(), any(BigDecimal.class), any(InvoiceStatus.class), any(Instant.class), any(Instant.class)))
                .thenReturn(createInvoiceDto(tansoInvoiceId.toString()));
        when(stripeSyncService.retrieveStripeInvoiceLinkedData("inv_003")).thenReturn(stripeInvoiceEntity);

        stripeWebhook.handleFullSyncInvoicePaid(stripeInvoice, accountId);

        // Verify: should create mirror directly using amountPaid ($35.00), NOT try to manipulate draft
        verify(invoiceService).createNewInvoice(
                eq(subscription), any(), eq(new BigDecimal("35.00")), eq(InvoiceStatus.DUE),
                any(Instant.class), any(Instant.class));
        verify(invoiceService).markInvoiceAsPaid(tansoInvoice);
    }

    @Test
    void handleFullSyncInvoicePaid_NonAccumulateMode_FallsBackToInvoiceCreated() {
        Invoice stripeInvoice = createStripeInvoiceWithPeriod(
                "inv_004", 5000L,
                Instant.parse("2025-02-01T00:00:00Z").getEpochSecond(),
                Instant.parse("2025-03-01T00:00:00Z").getEpochSecond());
        stripeInvoice.setMetadata(Map.of("tanso_subscription_id", subscription.getId().toString()));
        stripeInvoice.setStatus("paid");
        stripeInvoice.setAmountPaid(5000L);
        stripeInvoice.setAmountDue(5000L);

        UUID tansoInvoiceId = UUID.randomUUID();
        com.tansoflow.tansocore.entity.Invoice tansoInvoice = new com.tansoflow.tansocore.entity.Invoice();
        tansoInvoice.setId(tansoInvoiceId);

        StripeInvoice stripeInvoiceEntity = new StripeInvoice();
        stripeInvoiceEntity.setInvoice(tansoInvoice);

        // 3 calls to stripeInvoiceLinked:
        // 1. handleFullSyncInvoicePaid: not linked → enters backfill branch
        // 2. handleFullSyncInvoiceCreated: not linked → proceeds to create mirror
        // 3. (implicit) retrieveStripeInvoiceLinkedData used after creation
        when(stripeSyncService.stripeInvoiceLinked("inv_004"))
                .thenReturn(false)   // handleFullSyncInvoicePaid check
                .thenReturn(false);  // handleFullSyncInvoiceCreated idempotency check

        when(subscriptionService.getSubscriptionById(subscription.getId().toString(), accountId))
                .thenReturn(subscription);
        when(invoiceService.planHasAccumulateModeFeatures(plan)).thenReturn(false);
        when(invoiceService.createInvoiceFromStripe(any(Subscription.class), any(), any(BigDecimal.class), any(InvoiceStatus.class), any(Instant.class), any(Instant.class), anyList()))
                .thenReturn(createInvoiceDto(tansoInvoiceId.toString()));
        when(stripeSyncService.retrieveStripeInvoiceLinkedData("inv_004")).thenReturn(stripeInvoiceEntity);

        stripeWebhook.handleFullSyncInvoicePaid(stripeInvoice, accountId);

        // For non-accumulate mode, should fall through to handleFullSyncInvoiceCreated as before. The copy it made
        // is not committed yet, so it is marked paid in this transaction: markInvoiceAsPaid(String) opens a new one,
        // could not see the copy, threw "Invoice not found" and failed the webhook with 400.
        verify(invoiceService).markInvoiceAsPaid(tansoInvoice);
        verify(invoiceService, never()).markInvoiceAsPaid(any(String.class));
    }

    // ── Accumulate mode base price tests ────────────────────────────────────────

    @Test
    void handleFullSyncInvoiceCreated_AccumulateMode_WithBasePrice_AddsBasePriceLineItem() throws Exception {
        Instant newPeriodStart = Instant.parse("2025-02-01T00:00:00Z");
        Instant newPeriodEnd = Instant.parse("2025-03-01T00:00:00Z");

        plan.setPriceAmount(new BigDecimal("50.00"));
        plan.setName("Pro Plan");

        Invoice stripeInvoice = createStripeInvoiceWithPeriod(
                "inv_base_01", 0L,
                newPeriodStart.getEpochSecond(), newPeriodEnd.getEpochSecond());
        stripeInvoice.setMetadata(Map.of("tanso_subscription_id", subscription.getId().toString()));

        when(stripeSyncService.stripeInvoiceLinked("inv_base_01")).thenReturn(false);
        when(subscriptionService.getSubscriptionById(subscription.getId().toString(), accountId))
                .thenReturn(subscription);
        when(invoiceService.planHasAccumulateModeFeatures(plan)).thenReturn(true);
        when(invoiceService.calculateUsageChargeForPeriod(eq(subscription), eq(newPeriodStart), eq(newPeriodEnd)))
                .thenReturn(new BigDecimal("35.00"));
        when(invoiceService.createNewInvoice(any(Subscription.class), any(), any(BigDecimal.class), any(InvoiceStatus.class), any(Instant.class), any(Instant.class)))
                .thenReturn(createInvoiceDto(UUID.randomUUID().toString()));

        stripeWebhook.handleFullSyncInvoiceCreated(stripeInvoice, accountId);

        // Verify base price line item added to Stripe draft
        verify(stripeSyncService).addLineItemToDraftInvoice(
                eq("inv_base_01"), any(UUID.class), eq(new BigDecimal("50.00")), any(String.class),
                contains("Plan base price"));

        // Verify usage charge line item also added
        verify(stripeSyncService).addLineItemToDraftInvoice(
                eq("inv_base_01"), any(UUID.class), eq(new BigDecimal("35.00")), any(String.class),
                contains("Accumulated usage charge"));

        // Verify tanso mirror invoice total = base (50) + usage (35) = 85
        verify(invoiceService).createNewInvoice(
                eq(subscription), any(), eq(new BigDecimal("85.00")), eq(InvoiceStatus.DUE),
                eq(newPeriodStart), eq(newPeriodEnd));
    }

    @Test
    void handleFullSyncInvoiceCreated_AccumulateMode_WithBasePriceAndCreditOffset_AllThreeLineItems() throws Exception {
        Instant newPeriodStart = Instant.parse("2025-02-01T00:00:00Z");
        Instant newPeriodEnd = Instant.parse("2025-03-01T00:00:00Z");

        plan.setPriceAmount(new BigDecimal("50.00"));
        plan.setName("Pro Plan");

        Invoice stripeInvoice = createStripeInvoiceWithPeriod(
                "inv_base_02", 0L,
                newPeriodStart.getEpochSecond(), newPeriodEnd.getEpochSecond());
        stripeInvoice.setMetadata(Map.of("tanso_subscription_id", subscription.getId().toString()));

        when(stripeSyncService.stripeInvoiceLinked("inv_base_02")).thenReturn(false);
        when(subscriptionService.getSubscriptionById(subscription.getId().toString(), accountId))
                .thenReturn(subscription);
        when(invoiceService.planHasAccumulateModeFeatures(plan)).thenReturn(true);
        when(invoiceService.calculateUsageChargeForPeriod(eq(subscription), eq(newPeriodStart), eq(newPeriodEnd)))
                .thenReturn(new BigDecimal("35.00"));

        // Mock credit pool returning a credit offset of 10.00
        var creditPoolSub = new com.tansoflow.tansocore.entity.CreditPoolSubscription();
        var creditPool = new com.tansoflow.tansocore.entity.CreditPool();
        creditPool.setId(UUID.randomUUID());
        creditPoolSub.setCreditPool(creditPool);
        when(creditPoolSubscriptionRepository.findBySubscriptionIdOrderByDrawPriority(subscription.getId()))
                .thenReturn(List.of(creditPoolSub));
        when(creditService.applyCreditOffset(eq(creditPool.getId()), any(BigDecimal.class), eq(subscription.getId()), any(UUID.class), any(String.class)))
                .thenReturn(new BigDecimal("10.00"));

        when(invoiceService.createNewInvoice(any(Subscription.class), any(), any(BigDecimal.class), any(InvoiceStatus.class), any(Instant.class), any(Instant.class)))
                .thenReturn(createInvoiceDto(UUID.randomUUID().toString()));

        stripeWebhook.handleFullSyncInvoiceCreated(stripeInvoice, accountId);

        // Verify all three line items: base price, usage charge (net of credit), credit offset
        verify(stripeSyncService).addLineItemToDraftInvoice(
                eq("inv_base_02"), any(UUID.class), eq(new BigDecimal("50.00")), any(String.class),
                contains("Plan base price"));
        verify(stripeSyncService).addLineItemToDraftInvoice(
                eq("inv_base_02"), any(UUID.class), eq(new BigDecimal("25.00")), any(String.class),
                contains("Accumulated usage charge"));
        verify(stripeSyncService).addLineItemToDraftInvoice(
                eq("inv_base_02"), any(UUID.class), eq(new BigDecimal("-10.00")), any(String.class),
                contains("Credit applied"));

        // Verify tanso mirror invoice total = base (50) + net usage (25) = 75
        verify(invoiceService).createNewInvoice(
                eq(subscription), any(), eq(new BigDecimal("75.00")), eq(InvoiceStatus.DUE),
                eq(newPeriodStart), eq(newPeriodEnd));
    }

    @Test
    void handleFullSyncInvoiceCreated_AccumulateMode_ZeroBasePrice_NoBasePriceLineItem() throws Exception {
        Instant newPeriodStart = Instant.parse("2025-02-01T00:00:00Z");
        Instant newPeriodEnd = Instant.parse("2025-03-01T00:00:00Z");

        plan.setPriceAmount(BigDecimal.ZERO);

        Invoice stripeInvoice = createStripeInvoiceWithPeriod(
                "inv_base_03", 0L,
                newPeriodStart.getEpochSecond(), newPeriodEnd.getEpochSecond());
        stripeInvoice.setMetadata(Map.of("tanso_subscription_id", subscription.getId().toString()));

        when(stripeSyncService.stripeInvoiceLinked("inv_base_03")).thenReturn(false);
        when(subscriptionService.getSubscriptionById(subscription.getId().toString(), accountId))
                .thenReturn(subscription);
        when(invoiceService.planHasAccumulateModeFeatures(plan)).thenReturn(true);
        when(invoiceService.calculateUsageChargeForPeriod(eq(subscription), eq(newPeriodStart), eq(newPeriodEnd)))
                .thenReturn(new BigDecimal("35.00"));
        when(invoiceService.createNewInvoice(any(Subscription.class), any(), any(BigDecimal.class), any(InvoiceStatus.class), any(Instant.class), any(Instant.class)))
                .thenReturn(createInvoiceDto(UUID.randomUUID().toString()));

        stripeWebhook.handleFullSyncInvoiceCreated(stripeInvoice, accountId);

        // Verify NO base price line item was added
        verify(stripeSyncService, never()).addLineItemToDraftInvoice(
                any(), any(UUID.class), any(), any(), contains("Plan base price"));

        // Verify usage charge still added and mirror uses just net charge
        verify(stripeSyncService).addLineItemToDraftInvoice(
                eq("inv_base_03"), any(UUID.class), eq(new BigDecimal("35.00")), any(String.class),
                contains("Accumulated usage charge"));
        verify(invoiceService).createNewInvoice(
                eq(subscription), any(), eq(new BigDecimal("35.00")), eq(InvoiceStatus.DUE),
                eq(newPeriodStart), eq(newPeriodEnd));
    }

    // ── STRIPE_DRIVEN webhook handler tests ─────────────────────────────────

    @Test
    void handleStripeDrivenSubscriptionCreated_CreatesSubscriptionAndEntitlements() {
        com.stripe.model.Subscription stripeSub = createStripeSubscription("sub_sd_001", "cus_sd_001", "prod_sd_001", "active");

        StripeCustomer stripeCustomer = new StripeCustomer();
        stripeCustomer.setCustomer(customer);

        StripeProduct stripeProduct = new StripeProduct();
        stripeProduct.setPlan(plan);

        when(stripeSubscriptionRepository.existsStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_001"))
                .thenReturn(false);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(stripeCustomerRepository.findByStripeCustomerExternalIdAndAccount("cus_sd_001", account))
                .thenReturn(stripeCustomer);
        when(stripeProductPlansRepository.findByStripeProductExternalIdAndAccount("prod_sd_001", account))
                .thenReturn(stripeProduct);

        stripeWebhook.handleStripeDrivenSubscriptionCreated(stripeSub, accountId);

        verify(subscriptionRepository).save(any(Subscription.class));
        verify(stripeSubscriptionRepository).save(any(StripeSubscription.class));
        verify(entitlementService).processEntitlementsForSubscription(any(Subscription.class));
    }

    @Test
    void handleStripeDrivenSubscriptionCreated_AlreadyMapped_Skips() {
        com.stripe.model.Subscription stripeSub = createStripeSubscription("sub_sd_002", "cus_sd_001", "prod_sd_001", "active");

        when(stripeSubscriptionRepository.existsStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_002"))
                .thenReturn(true);

        stripeWebhook.handleStripeDrivenSubscriptionCreated(stripeSub, accountId);

        verify(subscriptionRepository, never()).save(any());
        verify(entitlementService, never()).processEntitlementsForSubscription(any());
    }

    @Test
    void handleStripeDrivenSubscriptionCreated_NoMappedCustomer_Skips() {
        com.stripe.model.Subscription stripeSub = createStripeSubscription("sub_sd_003", "cus_unknown", "prod_sd_001", "active");

        when(stripeSubscriptionRepository.existsStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_003"))
                .thenReturn(false);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(stripeCustomerRepository.findByStripeCustomerExternalIdAndAccount("cus_unknown", account))
                .thenReturn(null);

        stripeWebhook.handleStripeDrivenSubscriptionCreated(stripeSub, accountId);

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void handleStripeDrivenSubscriptionCreated_NoMappedPlan_Skips() {
        com.stripe.model.Subscription stripeSub = createStripeSubscription("sub_sd_004", "cus_sd_001", "prod_unmapped", "active");

        StripeCustomer stripeCustomer = new StripeCustomer();
        stripeCustomer.setCustomer(customer);

        when(stripeSubscriptionRepository.existsStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_004"))
                .thenReturn(false);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(stripeCustomerRepository.findByStripeCustomerExternalIdAndAccount("cus_sd_001", account))
                .thenReturn(stripeCustomer);
        when(stripeProductPlansRepository.findByStripeProductExternalIdAndAccount("prod_unmapped", account))
                .thenReturn(null);

        stripeWebhook.handleStripeDrivenSubscriptionCreated(stripeSub, accountId);

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void handleStripeDrivenSubscriptionUpdated_SyncsPeriodAndStatus() {
        com.stripe.model.Subscription stripeSub = createStripeSubscription("sub_sd_010", "cus_sd_001", "prod_sd_001", "canceled");

        StripeSubscription bridge = new StripeSubscription();
        bridge.setSubscription(subscription);

        when(stripeSubscriptionRepository.findStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_010"))
                .thenReturn(bridge);

        stripeWebhook.handleStripeDrivenSubscriptionUpdated(stripeSub);

        assertFalse(subscription.getIsActive());
        assertNotNull(subscription.getCancelledAt());
        verify(subscriptionRepository).save(subscription);
        verify(entitlementService).processEntitlementRevokeForSubscription(subscription);
    }

    @Test
    void handleStripeDrivenSubscriptionUpdated_ReactivatesWhenActive() {
        subscription.setIsActive(false);
        com.stripe.model.Subscription stripeSub = createStripeSubscription("sub_sd_011", "cus_sd_001", "prod_sd_001", "active");

        StripeSubscription bridge = new StripeSubscription();
        bridge.setSubscription(subscription);

        when(stripeSubscriptionRepository.findStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_011"))
                .thenReturn(bridge);

        stripeWebhook.handleStripeDrivenSubscriptionUpdated(stripeSub);

        assertEquals(true, subscription.getIsActive());
        verify(entitlementService).processEntitlementsForSubscription(subscription);
        verify(subscriptionRepository).save(subscription);
    }

    @Test
    void handleStripeDrivenSubscriptionUpdated_NoMapping_Skips() {
        com.stripe.model.Subscription stripeSub = createStripeSubscription("sub_sd_012", "cus_sd_001", "prod_sd_001", "active");

        when(stripeSubscriptionRepository.findStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_012"))
                .thenReturn(null);

        stripeWebhook.handleStripeDrivenSubscriptionUpdated(stripeSub);

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void handleStripeDrivenSubscriptionDeleted_DeactivatesAndRevokes() {
        com.stripe.model.Subscription stripeSub = createStripeSubscription("sub_sd_020", "cus_sd_001", "prod_sd_001", "canceled");

        StripeSubscription bridge = new StripeSubscription();
        bridge.setSubscription(subscription);

        when(stripeSubscriptionRepository.findStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_020"))
                .thenReturn(bridge);

        stripeWebhook.handleStripeDrivenSubscriptionDeleted(stripeSub);

        assertFalse(subscription.getIsActive());
        assertNotNull(subscription.getCancelledAt());
        assertNotNull(subscription.getCancelEffectiveAt());
        verify(subscriptionRepository).save(subscription);
        verify(entitlementService).processEntitlementRevokeForSubscription(subscription);
    }

    @Test
    void handleStripeDrivenSubscriptionDeleted_NoMapping_Skips() {
        com.stripe.model.Subscription stripeSub = createStripeSubscription("sub_sd_021", "cus_sd_001", "prod_sd_001", "canceled");

        when(stripeSubscriptionRepository.findStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_021"))
                .thenReturn(null);

        stripeWebhook.handleStripeDrivenSubscriptionDeleted(stripeSub);

        verify(subscriptionRepository, never()).save(any());
        verify(entitlementService, never()).processEntitlementRevokeForSubscription(any());
    }

    // ── STRIPE_DRIVEN invoice mirroring tests ────────────────────────────────

    @Test
    void handleStripeDrivenInvoiceCreated_MirrorsInvoice() {
        Invoice stripeInvoice = createStripeInvoiceWithSubscription("inv_sd_mirror_001", "sub_sd_030");
        stripeInvoice.setAmountDue(5000L);
        stripeInvoice.setStatus("open");

        StripeSubscription bridge = new StripeSubscription();
        bridge.setSubscription(subscription);

        when(stripeSubscriptionRepository.findStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_030"))
                .thenReturn(bridge);
        when(stripeSyncService.stripeInvoiceLinked("inv_sd_mirror_001")).thenReturn(false);
        when(invoiceService.createInvoiceFromStripe(any(Subscription.class), any(), any(BigDecimal.class), any(InvoiceStatus.class), any(Instant.class), any(Instant.class), anyList()))
                .thenReturn(createInvoiceDto(UUID.randomUUID().toString()));

        stripeWebhook.handleStripeDrivenInvoiceCreated(stripeInvoice, accountId);

        verify(invoiceService).createInvoiceFromStripe(eq(subscription), any(), eq(new BigDecimal("50.00")), eq(InvoiceStatus.DUE),
                any(Instant.class), any(Instant.class), anyList());
        verify(stripeSyncService).saveStripeInvoice(eq("inv_sd_mirror_001"), any(String.class), eq(accountId));
    }

    // Stripe charges a saved card while it creates the invoice, so invoice.created already reports it paid. Written
    // straight to PAID, it never went through markInvoiceAsPaid: the agent customer stayed provisional after paying,
    // and invoice.paid then found the invoice PAID and did nothing.
    @Test
    void handleStripeDrivenInvoiceCreated_AnInvoiceStripeAlreadyCollectedIsMarkedPaid() {
        Invoice stripeInvoice = createStripeInvoiceWithSubscription("inv_sd_mirror_paid", "sub_sd_030");
        stripeInvoice.setAmountDue(3000L);
        stripeInvoice.setStatus("paid");

        StripeSubscription bridge = new StripeSubscription();
        bridge.setSubscription(subscription);
        UUID tansoInvoiceId = UUID.randomUUID();
        com.tansoflow.tansocore.entity.Invoice tansoInvoice = new com.tansoflow.tansocore.entity.Invoice();
        tansoInvoice.setId(tansoInvoiceId);

        when(stripeSubscriptionRepository.findStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_030"))
                .thenReturn(bridge);
        when(stripeSyncService.stripeInvoiceLinked("inv_sd_mirror_paid")).thenReturn(false);
        when(invoiceService.createInvoiceFromStripe(any(Subscription.class), any(), any(BigDecimal.class), any(InvoiceStatus.class), any(Instant.class), any(Instant.class), anyList()))
                .thenReturn(createInvoiceDto(tansoInvoiceId.toString()));
        when(invoiceService.retrieveInvoiceByInvoiceIdAndAccount(tansoInvoiceId.toString(), accountId)).thenReturn(tansoInvoice);

        stripeWebhook.handleStripeDrivenInvoiceCreated(stripeInvoice, accountId);

        verify(invoiceService).createInvoiceFromStripe(eq(subscription), any(), eq(new BigDecimal("30.00")), eq(InvoiceStatus.DUE),
                any(Instant.class), any(Instant.class), anyList());
        verify(invoiceService).markInvoiceAsPaid(tansoInvoice);
    }

    // Stripe computed the invoice, so Tanso records its amount and lines. The mirror used to go through
    // createNewInvoice, which reset the amount to the subscription's current plan price plus Tanso's usage: a $30.00
    // upgrade proration was stored as 0.00 before the plan moved, or 60.00 (the new plan's full price) after.
    @Test
    void handleStripeDrivenInvoiceCreated_RecordsStripesAmountAndLinesWithoutRecalculating() {
        Invoice stripeInvoice = createStripeInvoiceWithSubscription("inv_sd_proration", "sub_sd_030");
        stripeInvoice.setAmountDue(3000L);
        stripeInvoice.setStatus("open");
        InvoiceLineItem unused = new InvoiceLineItem();
        unused.setAmount(-3000L);
        unused.setDescription("Unused time on Paid");
        InvoiceLineItem remaining = new InvoiceLineItem();
        remaining.setAmount(6000L);
        remaining.setDescription("Remaining time on Pro");
        InvoiceLineItemCollection lines = new InvoiceLineItemCollection();
        lines.setData(List.of(unused, remaining));
        stripeInvoice.setLines(lines);

        StripeSubscription bridge = new StripeSubscription();
        bridge.setSubscription(subscription);
        when(stripeSubscriptionRepository.findStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_030"))
                .thenReturn(bridge);
        when(stripeSyncService.stripeInvoiceLinked("inv_sd_proration")).thenReturn(false);
        when(invoiceService.createInvoiceFromStripe(any(Subscription.class), any(), any(BigDecimal.class), any(InvoiceStatus.class), any(Instant.class), any(Instant.class), anyList()))
                .thenReturn(createInvoiceDto(UUID.randomUUID().toString()));

        stripeWebhook.handleStripeDrivenInvoiceCreated(stripeInvoice, accountId);

        verify(invoiceService).createInvoiceFromStripe(eq(subscription), any(), eq(new BigDecimal("30.00")), eq(InvoiceStatus.DUE),
                any(Instant.class), any(Instant.class), eq(List.of(
                        new InvoiceService.SyncLineItem(new BigDecimal("-30.00"), "Unused time on Paid"),
                        new InvoiceService.SyncLineItem(new BigDecimal("60.00"), "Remaining time on Pro"))));
        verify(invoiceService, never()).createNewInvoice(any(Subscription.class), any(), any(BigDecimal.class), any(InvoiceStatus.class), any(Instant.class), any(Instant.class));
    }

    // invoice.created, invoice.paid and invoice.payment_succeeded arrive together. Each checked for a Tanso copy,
    // found none and inserted its own, so one Stripe invoice got several copies. The lock is taken before the check.
    @Test
    void handleStripeDrivenInvoiceCreated_LocksTheStripeInvoiceBeforeLookingForACopy() {
        Invoice stripeInvoice = createStripeInvoiceWithSubscription("inv_sd_lock", "sub_sd_030");
        StripeSubscription bridge = new StripeSubscription();
        bridge.setSubscription(subscription);
        when(stripeSubscriptionRepository.findStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_030"))
                .thenReturn(bridge);
        when(stripeSyncService.stripeInvoiceLinked("inv_sd_lock")).thenReturn(true);

        stripeWebhook.handleStripeDrivenInvoiceCreated(stripeInvoice, accountId);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(stripeSyncService);
        order.verify(stripeSyncService).lockStripeInvoice("inv_sd_lock");
        order.verify(stripeSyncService).stripeInvoiceLinked("inv_sd_lock");
        verify(invoiceService, never()).createNewInvoice(any(Subscription.class), any(), any(BigDecimal.class), any(InvoiceStatus.class), any(Instant.class), any(Instant.class));
    }

    @Test
    void handleFullSyncInvoiceCreated_LocksTheStripeInvoiceBeforeLookingForACopy() {
        Invoice stripeInvoice = createStripeInvoiceWithPeriod("inv_fs_lock", 5000L,
                Instant.parse("2025-02-01T00:00:00Z").getEpochSecond(),
                Instant.parse("2025-03-01T00:00:00Z").getEpochSecond());
        stripeInvoice.setMetadata(Map.of("tanso_subscription_id", subscription.getId().toString()));
        when(subscriptionService.getSubscriptionById(subscription.getId().toString(), accountId)).thenReturn(subscription);
        when(stripeSyncService.stripeInvoiceLinked("inv_fs_lock")).thenReturn(false);
        when(invoiceService.planHasAccumulateModeFeatures(plan)).thenReturn(false);
        when(invoiceService.createInvoiceFromStripe(any(Subscription.class), any(), any(BigDecimal.class), any(InvoiceStatus.class), any(Instant.class), any(Instant.class), anyList()))
                .thenReturn(createInvoiceDto(UUID.randomUUID().toString()));

        stripeWebhook.handleFullSyncInvoiceCreated(stripeInvoice, accountId);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(stripeSyncService);
        order.verify(stripeSyncService).lockStripeInvoice("inv_fs_lock");
        order.verify(stripeSyncService).stripeInvoiceLinked("inv_fs_lock");
        order.verify(stripeSyncService).saveStripeInvoice(eq("inv_fs_lock"), any(String.class), eq(accountId));
    }

    @Test
    void handleStripeDrivenInvoiceCreated_AlreadyLinked_Skips() {
        Invoice stripeInvoice = createStripeInvoiceWithSubscription("inv_sd_mirror_002", "sub_sd_030");

        StripeSubscription bridge = new StripeSubscription();
        bridge.setSubscription(subscription);

        when(stripeSubscriptionRepository.findStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_030"))
                .thenReturn(bridge);
        when(stripeSyncService.stripeInvoiceLinked("inv_sd_mirror_002")).thenReturn(true);

        stripeWebhook.handleStripeDrivenInvoiceCreated(stripeInvoice, accountId);

        verify(invoiceService, never()).createNewInvoice(any(Subscription.class), any(), any(BigDecimal.class), any(InvoiceStatus.class), any(Instant.class), any(Instant.class));
    }

    @Test
    void handleStripeDrivenInvoicePaid_MarksAsPaidAndGrantsCredits() {
        UUID tansoInvoiceId = UUID.randomUUID();
        com.tansoflow.tansocore.entity.Invoice tansoInvoice = new com.tansoflow.tansocore.entity.Invoice();
        tansoInvoice.setId(tansoInvoiceId);

        StripeInvoice stripeInvoiceEntity = new StripeInvoice();
        stripeInvoiceEntity.setInvoice(tansoInvoice);

        Invoice stripeInvoice = createStripeInvoiceWithSubscription("inv_sd_paid_001", "sub_sd_030");
        stripeInvoice.setAmountDue(5000L);
        stripeInvoice.setStatus("paid");

        StripeSubscription bridge = new StripeSubscription();
        bridge.setSubscription(subscription);

        when(stripeSubscriptionRepository.findStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_030"))
                .thenReturn(bridge);
        when(stripeSyncService.stripeInvoiceLinked("inv_sd_paid_001")).thenReturn(true);
        when(stripeSyncService.retrieveStripeInvoiceLinkedData("inv_sd_paid_001")).thenReturn(stripeInvoiceEntity);

        stripeWebhook.handleStripeDrivenInvoicePaid(stripeInvoice, accountId);

        verify(invoiceService).markInvoiceAsPaid(tansoInvoiceId.toString());
        verify(creditService).processCreditGrantsForSubscription(subscription);
    }

    // An agent upgrade Stripe could not charge at the time completes when a human pays its invoice. The paired
    // invoice.paid / invoice.payment_succeeded (or a redelivery) must not fulfil it twice.
    @Test
    void handleStripeDrivenInvoicePaid_FulfilsTheUpgradeWaitingOnThatInvoiceOnce() {
        UUID tansoInvoiceId = UUID.randomUUID();
        com.tansoflow.tansocore.entity.Invoice tansoInvoice = new com.tansoflow.tansocore.entity.Invoice();
        tansoInvoice.setId(tansoInvoiceId);
        StripeInvoice stripeInvoiceEntity = new StripeInvoice();
        stripeInvoiceEntity.setInvoice(tansoInvoice);

        Invoice stripeInvoice = createStripeInvoiceWithSubscription("in_proration", "sub_sd_030");
        stripeInvoice.setAmountPaid(7450L);
        stripeInvoice.setStatus("paid");

        StripeSubscription bridge = new StripeSubscription();
        bridge.setSubscription(subscription);
        com.tansoflow.tansocore.entity.SubscriptionScheduledChange pending =
                new com.tansoflow.tansocore.entity.SubscriptionScheduledChange();
        pending.setSubscription(subscription);
        pending.setStripeInvoiceId("in_proration");

        when(stripeSubscriptionRepository.findStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_030"))
                .thenReturn(bridge);
        when(stripeSyncService.stripeInvoiceLinked("in_proration")).thenReturn(true);
        when(stripeSyncService.retrieveStripeInvoiceLinkedData("in_proration")).thenReturn(stripeInvoiceEntity);
        // Once fulfilled the change is COMPLETED, so the locked PENDING lookup finds nothing on the second delivery.
        when(subscriptionScheduledChangeRepository.findPendingUpgradeByStripeInvoiceId("in_proration"))
                .thenReturn(Optional.of(pending))
                .thenReturn(Optional.empty());

        stripeWebhook.handleStripeDrivenInvoicePaid(stripeInvoice, accountId);
        stripeWebhook.handleStripeDrivenInvoicePaid(stripeInvoice, accountId);

        verify(subscriptionService, org.mockito.Mockito.times(1)).fulfilPaidUpgrade(pending, new BigDecimal("74.50"),
                com.tansoflow.tansocore.model.apikey.type.SpendChannel.OFF_SESSION);
    }

    // A send_invoice invoice is only ever paid by a human on its hosted page, so the upgrade it pays for must stay
    // out of the mandate. A charge_automatically one (above) may have been paid by Stripe retrying the saved card.
    @Test
    void handleStripeDrivenInvoicePaid_AnEmailedInvoiceAHumanPaidIsRecordedAsHosted() {
        com.tansoflow.tansocore.entity.Invoice tansoInvoice = new com.tansoflow.tansocore.entity.Invoice();
        tansoInvoice.setId(UUID.randomUUID());
        StripeInvoice stripeInvoiceEntity = new StripeInvoice();
        stripeInvoiceEntity.setInvoice(tansoInvoice);

        Invoice stripeInvoice = createStripeInvoiceWithSubscription("in_proration", "sub_sd_030");
        stripeInvoice.setAmountPaid(7450L);
        stripeInvoice.setStatus("paid");
        stripeInvoice.setCollectionMethod("send_invoice");

        StripeSubscription bridge = new StripeSubscription();
        bridge.setSubscription(subscription);
        com.tansoflow.tansocore.entity.SubscriptionScheduledChange pending =
                new com.tansoflow.tansocore.entity.SubscriptionScheduledChange();
        pending.setSubscription(subscription);
        pending.setStripeInvoiceId("in_proration");

        when(stripeSubscriptionRepository.findStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_030"))
                .thenReturn(bridge);
        when(stripeSyncService.stripeInvoiceLinked("in_proration")).thenReturn(true);
        when(stripeSyncService.retrieveStripeInvoiceLinkedData("in_proration")).thenReturn(stripeInvoiceEntity);
        when(subscriptionScheduledChangeRepository.findPendingUpgradeByStripeInvoiceId("in_proration"))
                .thenReturn(Optional.of(pending));

        stripeWebhook.handleStripeDrivenInvoicePaid(stripeInvoice, accountId);

        verify(subscriptionService).fulfilPaidUpgrade(pending, new BigDecimal("74.50"),
                com.tansoflow.tansocore.model.apikey.type.SpendChannel.HOSTED);
    }

    @Test
    void handleStripeDrivenInvoicePaymentFailed_LeavesTheWaitingUpgradePending() {
        Invoice stripeInvoice = createStripeInvoiceWithSubscription("in_proration", "sub_sd_030");
        when(stripeSyncService.stripeInvoiceLinked("in_proration")).thenReturn(false);

        stripeWebhook.handleStripeDrivenInvoicePaymentFailed(stripeInvoice);

        verify(subscriptionService, never()).fulfilPaidUpgrade(any(), any(), any());
        verifyNoInteractions(subscriptionScheduledChangeRepository);
    }

    @Test
    void handlePendingUpdateExpired_CancelsTheUpgradeNobodyPaidFor() {
        StripeSubscription bridge = new StripeSubscription();
        bridge.setSubscription(subscription);
        Plan starter = new Plan();
        starter.setKey("starter");
        com.tansoflow.tansocore.entity.SubscriptionScheduledChange pending =
                new com.tansoflow.tansocore.entity.SubscriptionScheduledChange();
        pending.setSubscription(subscription);
        pending.setToPlan(starter);
        pending.setStatus("PENDING");
        pending.setStripeInvoiceId("in_proration");
        pending.setPaymentUrl("https://invoice.stripe.com/i/acct_test/in_proration");

        com.stripe.model.Subscription stripeSub = new com.stripe.model.Subscription();
        stripeSub.setId("sub_sd_030");
        when(stripeSubscriptionRepository.findStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_030"))
                .thenReturn(bridge);
        when(subscriptionScheduledChangeRepository.findPendingUpgradeBySubscription(subscription))
                .thenReturn(Optional.of(pending));

        stripeWebhook.handlePendingUpdateExpired(stripeSub);

        assertEquals("CANCELLED", pending.getStatus());
        verify(subscriptionScheduledChangeRepository).save(pending);
        verify(subscriptionService, never()).fulfilPaidUpgrade(any(), any(), any());
    }

    private com.tansoflow.tansocore.entity.SubscriptionScheduledChange upgradeWaitingOn(String stripeInvoiceId) {
        com.tansoflow.tansocore.entity.SubscriptionScheduledChange pending =
                new com.tansoflow.tansocore.entity.SubscriptionScheduledChange();
        pending.setSubscription(subscription);
        pending.setToPlan(plan);
        pending.setStatus("PENDING");
        pending.setStripeInvoiceId(stripeInvoiceId);
        pending.setPaymentUrl("https://invoice.stripe.com/i/acct_test/" + stripeInvoiceId);
        when(subscriptionScheduledChangeRepository.findPendingUpgradeByStripeInvoiceId(stripeInvoiceId))
                .thenReturn(Optional.of(pending));
        return pending;
    }

    private com.tansoflow.tansocore.entity.Invoice mirroredTansoInvoice(String stripeInvoiceId) {
        com.tansoflow.tansocore.entity.Invoice tansoInvoice = new com.tansoflow.tansocore.entity.Invoice();
        tansoInvoice.setId(UUID.randomUUID());
        tansoInvoice.setSubscription(subscription);
        tansoInvoice.setStatus(InvoiceStatus.DUE.name());
        StripeInvoice link = new StripeInvoice();
        link.setInvoice(tansoInvoice);
        when(stripeSyncService.retrieveStripeInvoiceLinkedData(stripeInvoiceId)).thenReturn(link);
        return tansoInvoice;
    }

    // An unpaid send_invoice upgrade used to wait forever once its invoice was voided: nothing but invoice.paid
    // ended it, and Stripe stayed on the new price. The invoice is already void, so it must not be voided again.
    @Test
    void handleInvoiceVoided_CancelsTheUpgradeWaitingOnItAndRestoresThePrice() throws Exception {
        com.tansoflow.tansocore.entity.SubscriptionScheduledChange pending = upgradeWaitingOn("in_proration");
        com.tansoflow.tansocore.entity.Invoice tansoInvoice = mirroredTansoInvoice("in_proration");

        stripeWebhook.handleInvoiceVoided(createStripeInvoiceWithSubscription("in_proration", "sub_si_1"));

        assertEquals("CANCELLED", pending.getStatus());
        assertEquals(null, pending.getPaymentUrl());
        verify(subscriptionScheduledChangeRepository).save(pending);
        verify(stripeSyncService).restorePriceAfterDroppedUpgrade(subscription.getId(), account.getId());
        verify(stripeSyncService, never()).cancelUnpaidUpgrade(any(), any(), any());
        verify(stripeSyncService, never()).voidStripeInvoice(any(), any());
        // Set directly, not through invoiceService.voidInvoice, which would ask Stripe to void it a second time.
        assertEquals(InvoiceStatus.VOID.name(), tansoInvoice.getStatus());
        verify(invoiceService, never()).voidInvoice(any());
    }

    @Test
    void handleInvoiceVoided_WithNoUpgradeWaitingOnlyMirrorsTheStatus() throws Exception {
        com.tansoflow.tansocore.entity.Invoice tansoInvoice = mirroredTansoInvoice("in_renewal");
        when(subscriptionScheduledChangeRepository.findPendingUpgradeByStripeInvoiceId("in_renewal"))
                .thenReturn(Optional.empty());

        stripeWebhook.handleInvoiceVoided(createStripeInvoiceWithSubscription("in_renewal", "sub_si_1"));

        assertEquals(InvoiceStatus.VOID.name(), tansoInvoice.getStatus());
        verify(stripeSyncService, never()).restorePriceAfterDroppedUpgrade(any(), any());
        verify(subscriptionScheduledChangeRepository, never()).save(any());
    }

    // Stripe still takes payment on an uncollectible invoice. Left open, paying it would charge for an upgrade
    // Tanso gave up on, so it goes through the full cancel path: void, then restore the old price.
    @Test
    void handleInvoiceMarkedUncollectible_VoidsTheInvoiceAndCancelsTheUpgradeWaitingOnIt() throws Exception {
        com.tansoflow.tansocore.entity.SubscriptionScheduledChange pending = upgradeWaitingOn("in_proration");
        com.tansoflow.tansocore.entity.Invoice tansoInvoice = mirroredTansoInvoice("in_proration");

        stripeWebhook.handleInvoiceMarkedUncollectible(createStripeInvoiceWithSubscription("in_proration", "sub_si_1"));

        assertEquals("CANCELLED", pending.getStatus());
        verify(subscriptionScheduledChangeRepository).save(pending);
        verify(stripeSyncService).cancelUnpaidUpgrade("in_proration", subscription.getId(), account.getId());
        assertEquals(InvoiceStatus.PAST_DUE.name(), tansoInvoice.getStatus());
    }

    // The upgrade call commits its charge-first change before Stripe raises the invoice and records the invoice id
    // afterwards. Voided or written off before that id was recorded, the invoice matched nothing, so the change stayed
    // PENDING forever and Stripe kept the new price.
    private com.tansoflow.tansocore.entity.SubscriptionScheduledChange chargeFirstUpgradeWithNoInvoiceRecordedOn(String stripeSubId) {
        com.tansoflow.tansocore.entity.SubscriptionScheduledChange pending =
                new com.tansoflow.tansocore.entity.SubscriptionScheduledChange();
        pending.setSubscription(subscription);
        Plan starter = new Plan();
        starter.setKey("starter");
        pending.setToPlan(starter);
        pending.setStatus("PENDING");
        pending.setStripeChargeFirst(true);
        StripeSubscription bridge = new StripeSubscription();
        bridge.setSubscription(subscription);
        when(stripeSubscriptionRepository.findStripeSubscriptionByStripeSubscriptionExternalId(stripeSubId)).thenReturn(bridge);
        when(subscriptionScheduledChangeRepository.findPendingUpgradeWithoutStripeInvoiceBySubscription(subscription))
                .thenReturn(Optional.of(pending));
        return pending;
    }

    @Test
    void handleInvoiceVoided_CancelsAChargeFirstUpgradeWhoseInvoiceWasNeverRecorded() throws Exception {
        com.tansoflow.tansocore.entity.SubscriptionScheduledChange pending = chargeFirstUpgradeWithNoInvoiceRecordedOn("sub_si_1");
        Invoice proration = createStripeInvoiceWithSubscription("in_proration", "sub_si_1");
        proration.setBillingReason("subscription_update");

        stripeWebhook.handleInvoiceVoided(proration);

        assertEquals("CANCELLED", pending.getStatus());
        assertEquals("in_proration", pending.getStripeInvoiceId());
        verify(stripeSyncService).restorePriceAfterDroppedUpgrade(subscription.getId(), account.getId());
    }

    @Test
    void handleInvoiceMarkedUncollectible_CancelsAChargeFirstUpgradeWhoseInvoiceWasNeverRecorded() throws Exception {
        com.tansoflow.tansocore.entity.SubscriptionScheduledChange pending = chargeFirstUpgradeWithNoInvoiceRecordedOn("sub_si_1");
        Invoice proration = createStripeInvoiceWithSubscription("in_proration", "sub_si_1");
        proration.setBillingReason("subscription_update");

        stripeWebhook.handleInvoiceMarkedUncollectible(proration);

        assertEquals("CANCELLED", pending.getStatus());
        verify(stripeSyncService).cancelUnpaidUpgrade("in_proration", subscription.getId(), account.getId());
    }

    // A renewal voided or written off is not the upgrade's invoice.
    @Test
    void handleInvoiceVoided_ARenewalLeavesAChargeFirstUpgradeAlone() throws Exception {
        Invoice renewal = createStripeInvoiceWithSubscription("in_renewal", "sub_si_1");
        renewal.setBillingReason("subscription_cycle");

        stripeWebhook.handleInvoiceVoided(renewal);

        verify(subscriptionScheduledChangeRepository, never()).findPendingUpgradeWithoutStripeInvoiceBySubscription(any());
        verify(stripeSyncService, never()).restorePriceAfterDroppedUpgrade(any(), any());
    }

    // ── Saved-card subscribe recovered by customer.subscription.created ──────
    // The subscribe call commits a PENDING direct charge before Stripe creates the subscription. If recording the
    // result fails after Stripe charged, this webhook creates the Tanso subscription; it must also record the spend
    // against the key that asked, which only the pending row still knows.

    private com.stripe.model.Subscription directlyChargedStripeSubscription(UUID pendingChargeId) {
        com.stripe.model.Subscription stripeSub = new com.stripe.model.Subscription();
        stripeSub.setId("sub_direct_1");
        stripeSub.setStatus("active");
        stripeSub.setMetadata(Map.of(
                "tanso_account_id", accountId,
                "tanso_customer_id", customer.getId().toString(),
                "tanso_plan_id", plan.getId().toString(),
                com.tansoflow.tansocore.entity.CheckoutSession.DIRECT_CHARGE_METADATA_KEY, pendingChargeId.toString()));
        when(stripeSubscriptionRepository.existsStripeSubscriptionByStripeSubscriptionExternalId("sub_direct_1")).thenReturn(false);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(customerService.validateAndRetrieveCustomer(customer.getId().toString(), accountId)).thenReturn(customer);
        when(planService.retrievePlan(account, plan.getId())).thenReturn(plan);
        return stripeSub;
    }

    @Test
    void subscriptionCreated_RecordsTheSpendOfASavedCardSubscribeTheCallCouldNotRecord() throws Exception {
        UUID keyId = UUID.randomUUID();
        com.tansoflow.tansocore.entity.CheckoutSession pendingCharge = new com.tansoflow.tansocore.entity.CheckoutSession();
        pendingCharge.setId(UUID.randomUUID());
        pendingCharge.setAccountId(account.getId());
        pendingCharge.setApiKeyId(keyId);
        pendingCharge.setAmount(new BigDecimal("49.00"));
        pendingCharge.setPurpose(com.tansoflow.tansocore.entity.CheckoutSession.PURPOSE_DIRECT_SUBSCRIPTION);
        when(checkoutSessionRepository.findByIdForUpdate(pendingCharge.getId())).thenReturn(Optional.of(pendingCharge));

        stripeWebhook.handleStripeIntegrationSubscriptionCreated(directlyChargedStripeSubscription(pendingCharge.getId()), accountId);

        verify(stripeSubscriptionRepository).save(any(StripeSubscription.class));
        // Charged to the saved card with nobody on a payment page, under the key the subscribe call would have used.
        verify(keyBudgetService).recordSpend(eq(account.getId()), eq(keyId),
                eq(com.tansoflow.tansocore.model.apikey.type.SpendKind.MONEY),
                eq(com.tansoflow.tansocore.model.apikey.type.SpendChannel.OFF_SESSION),
                eq(new BigDecimal("49.00")), eq("sub_direct_1"), eq("stripe_sub:sub_direct_1"));
        assertEquals(com.tansoflow.tansocore.entity.CheckoutSession.STATUS_COMPLETED, pendingCharge.getStatus());
    }

    @Test
    void subscriptionCreated_DoesNotRecordADirectChargeTwice() throws Exception {
        com.tansoflow.tansocore.entity.CheckoutSession done = new com.tansoflow.tansocore.entity.CheckoutSession();
        done.setId(UUID.randomUUID());
        done.setStatus(com.tansoflow.tansocore.entity.CheckoutSession.STATUS_COMPLETED);
        when(checkoutSessionRepository.findByIdForUpdate(done.getId())).thenReturn(Optional.of(done));

        stripeWebhook.handleStripeIntegrationSubscriptionCreated(directlyChargedStripeSubscription(done.getId()), accountId);

        verify(keyBudgetService, never()).recordSpend(any(), any(), any(), any(), any(), any(), any());
    }

    // ── STRIPE_INTEGRATION charge-first upgrades ──────────────────────────────

    private StripeInvoice linkedStripeInvoice(String stripeInvoiceId) {
        com.tansoflow.tansocore.entity.Invoice tansoInvoice = new com.tansoflow.tansocore.entity.Invoice();
        tansoInvoice.setId(UUID.randomUUID());
        tansoInvoice.setSubscription(subscription);
        StripeInvoice link = new StripeInvoice();
        link.setInvoice(tansoInvoice);
        when(stripeSyncService.stripeInvoiceLinked(stripeInvoiceId)).thenReturn(true);
        when(stripeSyncService.retrieveStripeInvoiceLinkedData(stripeInvoiceId)).thenReturn(link);
        return link;
    }

    // Matching by subscription let any paid invoice, such as a renewal, complete an upgrade whose proration was
    // never paid. Only the invoice Stripe raised for the upgrade completes it, and only once.
    @Test
    void handleFullSyncInvoicePaid_FulfilsAChargeFirstUpgradeOnlyByItsOwnInvoiceAndOnce() {
        com.tansoflow.tansocore.entity.SubscriptionScheduledChange pending =
                new com.tansoflow.tansocore.entity.SubscriptionScheduledChange();
        pending.setSubscription(subscription);
        pending.setToPlan(plan);
        pending.setStatus("PENDING");
        pending.setStripeInvoiceId("in_proration");

        Invoice renewal = createStripeInvoiceWithSubscription("in_renewal", "sub_si_1");
        renewal.setAmountPaid(14900L);
        linkedStripeInvoice("in_renewal");
        when(subscriptionScheduledChangeRepository.findPendingUpgradeByStripeInvoiceId("in_renewal"))
                .thenReturn(Optional.empty());
        // The pending change has its invoice recorded, so the lookup for changes without one does not return it.
        when(subscriptionScheduledChangeRepository.findPendingUpgradeWithoutStripeInvoiceBySubscription(subscription))
                .thenReturn(Optional.empty());

        stripeWebhook.handleFullSyncInvoicePaid(renewal, accountId);

        verify(subscriptionService, never()).fulfilPaidUpgrade(any(), any(), any());

        Invoice proration = createStripeInvoiceWithSubscription("in_proration", "sub_si_1");
        proration.setAmountPaid(7450L);
        linkedStripeInvoice("in_proration");
        when(subscriptionScheduledChangeRepository.findPendingUpgradeByStripeInvoiceId("in_proration"))
                .thenReturn(Optional.of(pending))
                .thenReturn(Optional.empty());

        stripeWebhook.handleFullSyncInvoicePaid(proration, accountId);
        stripeWebhook.handleFullSyncInvoicePaid(proration, accountId);

        verify(subscriptionService, org.mockito.Mockito.times(1)).fulfilPaidUpgrade(pending, new BigDecimal("74.50"),
                com.tansoflow.tansocore.model.apikey.type.SpendChannel.OFF_SESSION);
    }

    // The first charge attempt failing is how a charge-first upgrade starts waiting on a human. Reverting the price
    // and marking it FAILED here would kill the hosted invoice the caller was just handed.
    @Test
    void handleFullSyncInvoicePaymentFailed_LeavesAChargeFirstUpgradeWaiting() throws Exception {
        com.tansoflow.tansocore.entity.SubscriptionScheduledChange pending =
                new com.tansoflow.tansocore.entity.SubscriptionScheduledChange();
        pending.setSubscription(subscription);
        pending.setStatus("PENDING");
        pending.setStripeInvoiceId("in_proration");
        linkedStripeInvoice("in_proration");
        when(subscriptionScheduledChangeRepository.findPendingUpgradeBySubscription(subscription))
                .thenReturn(Optional.of(pending));

        stripeWebhook.handleFullSyncInvoicePaymentFailed(createStripeInvoiceWithSubscription("in_proration", "sub_si_1"));

        assertEquals("PENDING", pending.getStatus());
        verify(stripeSyncService, never()).updateStripeSubscriptionPrice(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(subscriptionScheduledChangeRepository, never()).save(any());
    }

    // ── charge-first upgrades whose Stripe answer Tanso never recorded ────────
    // The upgrade call commits its PENDING change before Stripe charges and writes the invoice id afterwards. If that
    // write fails (or this webhook wins the race), the change has no invoice id, and invoice.paid must still find it.

    private com.tansoflow.tansocore.entity.SubscriptionScheduledChange chargeFirstChangeWithNoInvoiceRecorded() {
        com.tansoflow.tansocore.entity.SubscriptionScheduledChange pending =
                new com.tansoflow.tansocore.entity.SubscriptionScheduledChange();
        pending.setSubscription(subscription);
        pending.setToPlan(plan);
        pending.setStatus("PENDING");
        pending.setStripeChargeFirst(true);
        when(subscriptionScheduledChangeRepository.findPendingUpgradeWithoutStripeInvoiceBySubscription(subscription))
                .thenReturn(Optional.of(pending));
        return pending;
    }

    @Test
    void handleStripeDrivenInvoicePaid_CompletesAChargeFirstUpgradeWhoseInvoiceWasNeverRecorded() {
        com.tansoflow.tansocore.entity.SubscriptionScheduledChange pending = chargeFirstChangeWithNoInvoiceRecorded();
        StripeSubscription bridge = new StripeSubscription();
        bridge.setSubscription(subscription);
        when(stripeSubscriptionRepository.findStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_030"))
                .thenReturn(bridge);
        when(stripeSyncService.stripeInvoiceLinked(any())).thenReturn(true);

        // A renewal is not the payment for the upgrade.
        Invoice renewal = createStripeInvoiceWithSubscription("in_renewal", "sub_sd_030");
        renewal.setBillingReason("subscription_cycle");
        stripeWebhook.handleStripeDrivenInvoicePaid(renewal, accountId);
        verify(subscriptionService, never()).fulfilPaidUpgrade(any(), any(), any());

        Invoice proration = createStripeInvoiceWithSubscription("in_proration", "sub_sd_030");
        proration.setBillingReason("subscription_update");
        proration.setAmountPaid(7450L);
        stripeWebhook.handleStripeDrivenInvoicePaid(proration, accountId);

        // charge_automatically: Stripe charged the saved card.
        verify(subscriptionService).fulfilPaidUpgrade(pending, new BigDecimal("74.50"),
                com.tansoflow.tansocore.model.apikey.type.SpendChannel.OFF_SESSION);
        assertEquals("in_proration", pending.getStripeInvoiceId());
    }

    @Test
    void handleFullSyncInvoicePaid_CompletesAChargeFirstUpgradeWhoseInvoiceWasNeverRecordedOnlyByTheUpgradeInvoice() {
        com.tansoflow.tansocore.entity.SubscriptionScheduledChange pending = chargeFirstChangeWithNoInvoiceRecorded();

        Invoice renewal = createStripeInvoiceWithSubscription("in_renewal", "sub_si_1");
        renewal.setBillingReason("subscription_cycle");
        renewal.setAmountPaid(14900L);
        linkedStripeInvoice("in_renewal");
        stripeWebhook.handleFullSyncInvoicePaid(renewal, accountId);
        verify(subscriptionService, never()).fulfilPaidUpgrade(any(), any(), any());

        Invoice proration = createStripeInvoiceWithSubscription("in_proration", "sub_si_1");
        proration.setBillingReason("subscription_update");
        proration.setAmountPaid(7450L);
        // Stripe emailed this one, so a human paid it on the hosted page.
        proration.setCollectionMethod("send_invoice");
        linkedStripeInvoice("in_proration");
        stripeWebhook.handleFullSyncInvoicePaid(proration, accountId);

        verify(subscriptionService).fulfilPaidUpgrade(pending, new BigDecimal("74.50"),
                com.tansoflow.tansocore.model.apikey.type.SpendChannel.HOSTED);
        assertEquals("in_proration", pending.getStripeInvoiceId());
    }

    // The declined first attempt fires invoice.payment_failed while the upgrade call is still waiting on Stripe. The
    // change has no invoice id yet; treating it as an in-arrears upgrade would revert the price and mark it FAILED.
    @Test
    void handleFullSyncInvoicePaymentFailed_LeavesAChargeFirstUpgradeInFlightAlone() throws Exception {
        com.tansoflow.tansocore.entity.SubscriptionScheduledChange pending =
                new com.tansoflow.tansocore.entity.SubscriptionScheduledChange();
        pending.setSubscription(subscription);
        pending.setStatus("PENDING");
        pending.setStripeChargeFirst(true);
        linkedStripeInvoice("in_proration");
        when(subscriptionScheduledChangeRepository.findPendingUpgradeBySubscription(subscription))
                .thenReturn(Optional.of(pending));

        stripeWebhook.handleFullSyncInvoicePaymentFailed(createStripeInvoiceWithSubscription("in_proration", "sub_si_1"));

        assertEquals("PENDING", pending.getStatus());
        verify(stripeSyncService, never()).updateStripeSubscriptionPrice(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    // A proration invoice covers now until period end. Reading the period off it moved the subscription's period
    // start, and the accumulate path billed a whole period's base price and usage on it.
    @Test
    void handleFullSyncInvoiceCreated_AnUpgradeProrationInvoiceKeepsThePeriodAndSkipsAccumulateBilling() throws Exception {
        Invoice proration = createStripeInvoiceWithPeriod("in_proration", 7450L,
                Instant.parse("2025-01-16T00:00:00Z").getEpochSecond(),
                Instant.parse("2025-02-01T00:00:00Z").getEpochSecond());
        proration.setBillingReason("subscription_update");
        proration.setMetadata(Map.of("tanso_subscription_id", subscription.getId().toString()));

        when(stripeSyncService.stripeInvoiceLinked("in_proration")).thenReturn(false);
        when(subscriptionService.getSubscriptionById(subscription.getId().toString(), accountId))
                .thenReturn(subscription);
        when(invoiceService.createInvoiceFromStripe(any(Subscription.class), any(), any(BigDecimal.class), any(InvoiceStatus.class), any(Instant.class), any(Instant.class), anyList()))
                .thenReturn(createInvoiceDto(UUID.randomUUID().toString()));

        stripeWebhook.handleFullSyncInvoiceCreated(proration, accountId);

        assertEquals(Instant.parse("2025-01-01T00:00:00Z"), subscription.getCurrentPeriodStart());
        verify(subscriptionRepository, never()).save(any());
        verify(invoiceService, never()).planHasAccumulateModeFeatures(any());
        verify(stripeSyncService, never()).disableAutoAdvanceOnStripeInvoice(any(), any());
        verify(invoiceService).createInvoiceFromStripe(eq(subscription), any(), eq(new BigDecimal("74.50")), eq(InvoiceStatus.DUE),
                eq(Instant.parse("2025-01-01T00:00:00Z")), eq(Instant.parse("2025-02-01T00:00:00Z")), anyList());
    }

    // ── Spend mandate completion records the owner email ─────────────────────

    private com.stripe.model.checkout.Session mandateSession(String email) {
        com.stripe.model.checkout.Session session = new com.stripe.model.checkout.Session();
        session.setId("cs_mandate_1");
        session.setMode("setup");
        session.setSetupIntent("seti_1");
        session.setCustomer("cus_1");
        session.setMetadata(Map.of("tanso_account_id", accountId,
                "tanso_purpose", com.tansoflow.tansocore.entity.CheckoutSession.PURPOSE_SPEND_MANDATE));
        com.stripe.model.checkout.Session.CustomerDetails details = new com.stripe.model.checkout.Session.CustomerDetails();
        details.setEmail(email);
        session.setCustomerDetails(details);

        com.tansoflow.tansocore.entity.CheckoutSession record = new com.tansoflow.tansocore.entity.CheckoutSession();
        record.setAccountId(account.getId());
        record.setCustomerId(customer.getId());
        record.setAmount(new BigDecimal("50.00"));
        when(checkoutSessionRepository.findByStripeSessionId("cs_mandate_1")).thenReturn(Optional.of(record));
        return session;
    }

    @Test
    void aCompletedSpendMandateRecordsTheCheckoutEmailWhenTheCustomerHasNone() throws Exception {
        com.stripe.model.checkout.Session session = mandateSession("owner@example.com");
        when(customerService.validateAndRetrieveCustomer(customer.getId().toString(), accountId)).thenReturn(customer);

        stripeWebhook.handleSessionsComplete(session);

        verify(agentLifecycleService).setOwnerEmail(customer, "owner@example.com");
    }

    @Test
    void aCompletedSpendMandateNeverReplacesAnExistingEmail() throws Exception {
        customer.setEmail("signup@example.com");
        com.stripe.model.checkout.Session session = mandateSession("owner@example.com");
        when(customerService.validateAndRetrieveCustomer(customer.getId().toString(), accountId)).thenReturn(customer);

        stripeWebhook.handleSessionsComplete(session);

        verify(agentLifecycleService, never()).setOwnerEmail(any(), any());
    }

    @Test
    void handleStripeDrivenInvoicePaid_NotYetLinked_CreatesMirrorThenMarksPaid() {
        UUID tansoInvoiceId = UUID.randomUUID();
        com.tansoflow.tansocore.entity.Invoice tansoInvoice = new com.tansoflow.tansocore.entity.Invoice();
        tansoInvoice.setId(tansoInvoiceId);

        StripeInvoice stripeInvoiceEntity = new StripeInvoice();
        stripeInvoiceEntity.setInvoice(tansoInvoice);

        Invoice stripeInvoice = createStripeInvoiceWithSubscription("inv_sd_race_001", "sub_sd_030");
        stripeInvoice.setAmountDue(3000L);
        stripeInvoice.setStatus("paid");

        StripeSubscription bridge = new StripeSubscription();
        bridge.setSubscription(subscription);

        when(stripeSubscriptionRepository.findStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_030"))
                .thenReturn(bridge);
        // Not linked on first check (invoice.paid before invoice.created), linked after mirror creation
        when(stripeSyncService.stripeInvoiceLinked("inv_sd_race_001"))
                .thenReturn(false)
                .thenReturn(false);
        when(invoiceService.createInvoiceFromStripe(any(Subscription.class), any(), any(BigDecimal.class), any(InvoiceStatus.class), any(Instant.class), any(Instant.class), anyList()))
                .thenReturn(createInvoiceDto(tansoInvoiceId.toString()));
        when(stripeSyncService.retrieveStripeInvoiceLinkedData("inv_sd_race_001")).thenReturn(stripeInvoiceEntity);
        when(invoiceService.retrieveInvoiceByInvoiceIdAndAccount(tansoInvoiceId.toString(), accountId)).thenReturn(tansoInvoice);

        stripeWebhook.handleStripeDrivenInvoicePaid(stripeInvoice, accountId);

        // The mirror is created DUE and marked paid in this transaction. markInvoiceAsPaid(String) opens a new one,
        // which could not see the mirror yet and threw "Invoice not found", failing the webhook with 400.
        verify(invoiceService).createInvoiceFromStripe(eq(subscription), any(), eq(new BigDecimal("30.00")), eq(InvoiceStatus.DUE),
                any(Instant.class), any(Instant.class), anyList());
        verify(invoiceService).markInvoiceAsPaid(tansoInvoice);
        verify(invoiceService, never()).markInvoiceAsPaid(any(String.class));
        verify(creditService).processCreditGrantsForSubscription(subscription);
    }

    @Test
    void handleStripeDrivenInvoicePaid_NoSubscription_Skips() {
        Invoice stripeInvoice = new Invoice();
        stripeInvoice.setId("inv_sd_002");
        // No parent/subscription

        stripeWebhook.handleStripeDrivenInvoicePaid(stripeInvoice, accountId);

        verifyNoInteractions(creditService);
    }

    @Test
    void handleStripeDrivenInvoicePaid_NoMapping_Skips() {
        Invoice stripeInvoice = createStripeInvoiceWithSubscription("inv_sd_003", "sub_sd_unmapped");

        when(stripeSubscriptionRepository.findStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_unmapped"))
                .thenReturn(null);

        stripeWebhook.handleStripeDrivenInvoicePaid(stripeInvoice, accountId);

        verifyNoInteractions(creditService);
    }

    @Test
    void handleStripeDrivenInvoicePaymentFailed_MarksPastDue() {
        UUID tansoInvoiceId = UUID.randomUUID();
        com.tansoflow.tansocore.entity.Invoice tansoInvoice = new com.tansoflow.tansocore.entity.Invoice();
        tansoInvoice.setId(tansoInvoiceId);
        tansoInvoice.setStatus(InvoiceStatus.DUE.name());

        StripeInvoice stripeInvoiceEntity = new StripeInvoice();
        stripeInvoiceEntity.setInvoice(tansoInvoice);

        Invoice stripeInvoice = createStripeInvoiceWithSubscription("inv_sd_fail_001", "sub_sd_030");

        when(stripeSyncService.stripeInvoiceLinked("inv_sd_fail_001")).thenReturn(true);
        when(stripeSyncService.retrieveStripeInvoiceLinkedData("inv_sd_fail_001")).thenReturn(stripeInvoiceEntity);

        stripeWebhook.handleStripeDrivenInvoicePaymentFailed(stripeInvoice);

        assertEquals(InvoiceStatus.PAST_DUE.name(), tansoInvoice.getStatus());
    }

    @Test
    void handleStripeDrivenSubscriptionCreated_IdempotentWhenBridgeExists() {
        com.stripe.model.Subscription stripeSub = createStripeSubscription("sub_sd_idempotent", "cus_sd_001", "prod_sd_001", "active");

        when(stripeSubscriptionRepository.existsStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_idempotent"))
                .thenReturn(true);

        stripeWebhook.handleStripeDrivenSubscriptionCreated(stripeSub, accountId);

        verify(subscriptionRepository, never()).save(any());
        verify(entitlementService, never()).processEntitlementsForSubscription(any());
    }

    // ── STRIPE_DRIVEN customer auto-creation tests ─────────────────────────

    @Test
    void handleStripeDrivenCustomerCreated_CreatesCustomerAndBridge() {
        com.stripe.model.Customer stripeCustomer = new com.stripe.model.Customer();
        stripeCustomer.setId("cus_new_001");
        stripeCustomer.setEmail("jane@example.com");
        stripeCustomer.setName("Jane Doe");
        stripeCustomer.setPhone("+1555123456");

        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(stripeCustomerRepository.existsByStripeCustomerExternalIdAndAccount("cus_new_001", account))
                .thenReturn(false);
        when(customerService.createCustomer(eq(account), any(CustomerDto.class))).thenReturn(customer);

        stripeWebhook.handleStripeDrivenCustomerCreated(stripeCustomer, accountId);

        verify(customerService).createCustomer(eq(account), argThat(dto ->
                "cus_new_001".equals(dto.getCustomerReferenceId())
                        && "jane@example.com".equals(dto.getEmail())
                        && "Jane".equals(dto.getFirstName())
                        && "Doe".equals(dto.getLastName())
                        && "+1555123456".equals(dto.getPhoneNumber())
        ));
        verify(stripeCustomerRepository).save(any(StripeCustomer.class));
    }

    @Test
    void handleStripeDrivenCustomerCreated_AlreadyMapped_Skips() {
        com.stripe.model.Customer stripeCustomer = new com.stripe.model.Customer();
        stripeCustomer.setId("cus_existing");

        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(stripeCustomerRepository.existsByStripeCustomerExternalIdAndAccount("cus_existing", account))
                .thenReturn(true);

        stripeWebhook.handleStripeDrivenCustomerCreated(stripeCustomer, accountId);

        verify(customerService, never()).createCustomer(any(Account.class), any(CustomerDto.class));
        verify(stripeCustomerRepository, never()).save(any(StripeCustomer.class));
    }

    @Test
    void handleStripeDrivenCustomerCreated_NoEmail_UsesFallback() {
        com.stripe.model.Customer stripeCustomer = new com.stripe.model.Customer();
        stripeCustomer.setId("cus_no_email");
        stripeCustomer.setName("Bob Smith");

        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(stripeCustomerRepository.existsByStripeCustomerExternalIdAndAccount("cus_no_email", account))
                .thenReturn(false);
        when(customerService.createCustomer(eq(account), any(CustomerDto.class))).thenReturn(customer);

        stripeWebhook.handleStripeDrivenCustomerCreated(stripeCustomer, accountId);

        verify(customerService).createCustomer(eq(account), argThat(dto ->
                "cus_no_email@stripe.placeholder".equals(dto.getEmail())
        ));
    }

    @Test
    void handleStripeDrivenCustomerCreated_NoName_UsesUnknown() {
        com.stripe.model.Customer stripeCustomer = new com.stripe.model.Customer();
        stripeCustomer.setId("cus_no_name");
        stripeCustomer.setEmail("anon@example.com");

        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(stripeCustomerRepository.existsByStripeCustomerExternalIdAndAccount("cus_no_name", account))
                .thenReturn(false);
        when(customerService.createCustomer(eq(account), any(CustomerDto.class))).thenReturn(customer);

        stripeWebhook.handleStripeDrivenCustomerCreated(stripeCustomer, accountId);

        verify(customerService).createCustomer(eq(account), argThat(dto ->
                "Unknown".equals(dto.getFirstName()) && "".equals(dto.getLastName())
        ));
    }

    @Test
    void handleStripeDrivenSubscriptionCreated_UnmappedCustomer_AutoCreates() {
        com.stripe.model.Subscription stripeSub = createStripeSubscription("sub_auto_001", "cus_auto_001", "prod_sd_001", "active");

        StripeProduct stripeProduct = new StripeProduct();
        stripeProduct.setPlan(plan);

        // First call: no mapping; after auto-creation: mapping exists
        StripeCustomer createdBridge = new StripeCustomer();
        createdBridge.setCustomer(customer);

        when(stripeSubscriptionRepository.existsStripeSubscriptionByStripeSubscriptionExternalId("sub_auto_001"))
                .thenReturn(false);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(stripeCustomerRepository.findByStripeCustomerExternalIdAndAccount("cus_auto_001", account))
                .thenReturn(null)            // first lookup in subscription handler
                .thenReturn(createdBridge);  // after auto-creation re-query
        // Idempotency check inside handleStripeDrivenCustomerCreated
        when(stripeCustomerRepository.existsByStripeCustomerExternalIdAndAccount("cus_auto_001", account))
                .thenReturn(false);
        when(customerService.createCustomer(eq(account), any(CustomerDto.class))).thenReturn(customer);
        when(stripeProductPlansRepository.findByStripeProductExternalIdAndAccount("prod_sd_001", account))
                .thenReturn(stripeProduct);

        // Use mockStatic for Customer.retrieve
        try (var mockedStatic = org.mockito.Mockito.mockStatic(com.stripe.model.Customer.class)) {
            com.stripe.model.Customer stripeCustomerObj = new com.stripe.model.Customer();
            stripeCustomerObj.setId("cus_auto_001");
            stripeCustomerObj.setEmail("auto@example.com");
            stripeCustomerObj.setName("Auto User");
            mockedStatic.when(() -> com.stripe.model.Customer.retrieve("cus_auto_001"))
                    .thenReturn(stripeCustomerObj);

            stripeWebhook.handleStripeDrivenSubscriptionCreated(stripeSub, accountId);
        }

        // Verify customer was auto-created
        verify(customerService).createCustomer(eq(account), any(CustomerDto.class));
        // Verify subscription was still created
        verify(subscriptionRepository).save(any(Subscription.class));
        verify(stripeSubscriptionRepository).save(any(StripeSubscription.class));
        verify(entitlementService).processEntitlementsForSubscription(any(Subscription.class));
    }

    // ── Helper methods ────────────────────────────────────────────────────────

    private com.stripe.model.Subscription createStripeSubscription(String subId, String customerId, String productId, String status) {
        Price price = new Price();
        price.setProduct(productId);

        SubscriptionItem subItem = new SubscriptionItem();
        subItem.setPrice(price);
        subItem.setCurrentPeriodStart(Instant.parse("2025-03-01T00:00:00Z").getEpochSecond());
        subItem.setCurrentPeriodEnd(Instant.parse("2025-04-01T00:00:00Z").getEpochSecond());

        SubscriptionItemCollection items = new SubscriptionItemCollection();
        items.setData(List.of(subItem));

        com.stripe.model.Subscription stripeSub = new com.stripe.model.Subscription();
        stripeSub.setId(subId);
        stripeSub.setCustomer(customerId);
        stripeSub.setItems(items);
        stripeSub.setStatus(status);
        return stripeSub;
    }

    private Invoice createStripeInvoiceWithSubscription(String invoiceId, String subscriptionId) {
        Invoice invoice = new Invoice();
        invoice.setId(invoiceId);

        Invoice.Parent parent = new Invoice.Parent();
        Invoice.Parent.SubscriptionDetails subDetails = new Invoice.Parent.SubscriptionDetails();
        subDetails.setSubscription(subscriptionId);
        parent.setSubscriptionDetails(subDetails);
        invoice.setParent(parent);

        return invoice;
    }

    private InvoiceDto createInvoiceDto(String id) {
        InvoiceDto dto = new InvoiceDto();
        dto.setId(id);
        return dto;
    }

    private Invoice createStripeInvoiceWithPeriod(String invoiceId, long amountDue, long periodStart, long periodEnd) {
        Invoice stripeInvoice = new Invoice();
        stripeInvoice.setId(invoiceId);
        stripeInvoice.setAmountDue(amountDue);

        InvoiceLineItem.Period period = new InvoiceLineItem.Period();
        period.setStart(periodStart);
        period.setEnd(periodEnd);

        InvoiceLineItem lineItem = new InvoiceLineItem();
        lineItem.setPeriod(period);
        lineItem.setAmount(amountDue);

        InvoiceLineItemCollection lineItems = new InvoiceLineItemCollection();
        lineItems.setData(List.of(lineItem));

        stripeInvoice.setLines(lineItems);
        return stripeInvoice;
    }
}
