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
package com.tansoflow.tansocore.service.internal.monetization.implementation;

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.Invoice;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.entity.SubscriptionScheduledChange;
import com.tansoflow.tansocore.mapper.monetization.InvoiceMapper;
import com.tansoflow.tansocore.mapper.monetization.SubscriptionMapper;
import com.tansoflow.tansocore.mapper.monetization.SubscriptionScheduledChangeMapper;
import com.tansoflow.tansocore.model.billing.CreateInvoiceParams;
import com.tansoflow.tansocore.model.billing.InvoiceDto;
import com.tansoflow.tansocore.model.billing.type.InvoiceStatus;
import com.tansoflow.tansocore.model.billing.type.InvoiceType;
import com.tansoflow.tansocore.model.event.service.SubscriptionActivatedEvent;
import com.tansoflow.tansocore.model.plan.BillingTiming;
import com.tansoflow.tansocore.model.plan.PlanStatus;
import com.tansoflow.tansocore.model.subscription.SubscriptionDto;
import com.tansoflow.tansocore.model.subscription.SubscriptionScheduledChangeDto;
import com.tansoflow.tansocore.model.subscription.response.SubscribedCustomerResponse;
import com.tansoflow.tansocore.repository.SubscriptionRepository;
import com.tansoflow.tansocore.repository.SubscriptionScheduledChangeRepository;
import com.tansoflow.tansocore.service.internal.account.implementation.CustomerServiceImpl;
import com.tansoflow.tansocore.service.internal.monetization.InvoiceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceImplTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private InvoiceService invoiceService;

    @Mock
    private InvoiceMapper invoiceMapper;

    @Mock
    private SubscriptionMapper subscriptionMapper;

    @Mock
    private SubscriptionScheduledChangeMapper subscriptionScheduledChangeMapper;

    @Mock
    private SubscriptionScheduledChangeRepository subscriptionScheduledChangeRepository;

    @Mock
    private CustomerServiceImpl customerService;

    @Mock
    private com.tansoflow.tansocore.service.internal.account.AccountService accountService;

    @Mock
    private com.tansoflow.tansocore.integration.stripe.StripeSyncService stripeSyncService;

    @Mock
    private com.tansoflow.tansocore.service.internal.monetization.EntitlementService entitlementService;

    @Mock
    private com.tansoflow.tansocore.service.internal.monetization.CreditService creditService;

    @Mock
    private com.tansoflow.tansocore.service.internal.monetization.PlanService planService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private com.tansoflow.tansocore.service.internal.account.KeyBudgetService keyBudgetService;

    @Mock
    private com.tansoflow.tansocore.repository.PlanCreditAllocationRepository planCreditAllocationRepository;

    @Mock
    private com.tansoflow.tansocore.repository.CheckoutSessionRepository checkoutSessionRepository;

    @Mock
    private com.tansoflow.tansocore.repository.CustomerRepository customerRepository;

    @Mock
    private com.tansoflow.tansocore.repository.StripeSubscriptionRepository stripeSubscriptionRepository;

    @Mock
    private org.springframework.beans.factory.ObjectProvider<com.tansoflow.tansocore.integration.stripe.StripeWebhook> stripeWebhookProvider;

    // Real Spring transactions with no database, so a test can ask whether code runs inside one.
    private final com.tansoflow.tansocore.util.TestTransactionManager transactionManager =
            new com.tansoflow.tansocore.util.TestTransactionManager();

    @org.mockito.Spy
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate =
            new org.springframework.transaction.support.TransactionTemplate(transactionManager);

    @InjectMocks
    private SubscriptionServiceImpl subscriptionService;

    // Scheduled changes the service saved, so the locked re-read in the upgrade's last step finds them.
    private final List<SubscriptionScheduledChange> savedChanges = new java.util.ArrayList<>();

    // Regression: the account-wide cap on a single agent-initiated charge
    // covered credit top-ups but not subscribe, so an agent capped at a small
    // top-up could still commit its customer to an expensive recurring plan.
    // Found by /qa on 2026-08-21
    @org.junit.jupiter.api.Test
    void paidSubscribeStopsBeforeStripeWhenTheAccountCapIsExceeded() {
        com.tansoflow.tansocore.entity.Account account = new com.tansoflow.tansocore.entity.Account();
        UUID accountId = UUID.randomUUID();
        account.setId(accountId);

        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setAccount(account);
        customer.setStripeDefaultPaymentMethodId("pm_saved");

        Plan paidPlan = new Plan();
        paidPlan.setId(UUID.randomUUID());
        paidPlan.setStatus(com.tansoflow.tansocore.model.plan.PlanStatus.ACTIVE.name());
        paidPlan.setBillingTiming(com.tansoflow.tansocore.model.plan.BillingTiming.IN_ADVANCE.name());
        paidPlan.setPriceAmount(new java.math.BigDecimal("5000.00"));

        com.tansoflow.tansocore.entity.AccountSetting setting = new com.tansoflow.tansocore.entity.AccountSetting();
        setting.setStripeMode(com.tansoflow.tansocore.model.api.external.StripeMode.STRIPE_INTEGRATION);
        setting.setAgentMaxTopupAmount(new java.math.BigDecimal("100.00"));
        when(accountService.retrieveAccountSettings(accountId.toString())).thenReturn(setting);
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId()))
                .thenReturn(java.util.List.of());

        // The cap applies to agent callers, which is what a ck_ key identifies.
        com.tansoflow.tansocore.auth.UserContext ctx = new com.tansoflow.tansocore.auth.UserContext(
                accountId.toString(), customer.getId().toString(), "ref", java.util.List.of("purchase"),
                null, UUID.randomUUID());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new com.tansoflow.tansocore.auth.UserContextAuthentication(ctx, java.util.List.of()));
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> subscriptionService.subscribe(customer, paidPlan, accountId.toString(), null))
                    .isInstanceOf(com.tansoflow.tansocore.model.exception.BudgetExceededException.class);
            verifyNoInteractions(stripeSyncService);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    // Regression: the budget gate originally sat inside the STRIPE_INTEGRATION
    // branch, so an account where Tanso does the billing created the
    // subscription and its invoice without ever consulting the budget. Caught by
    // running the crash dummy against a fresh stack, whose seeded account is
    // stripeMode NONE — every earlier check had run on a Stripe-integration
    // account and passed.
    // Found by /qa on 2026-08-21
    @org.junit.jupiter.api.Test
    void paidSubscribeRespectsTheBudgetWhenTansoDoesTheBilling() {
        com.tansoflow.tansocore.entity.Account account = new com.tansoflow.tansocore.entity.Account();
        UUID accountId = UUID.randomUUID();
        account.setId(accountId);

        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setAccount(account);

        Plan paidPlan = new Plan();
        paidPlan.setId(UUID.randomUUID());
        paidPlan.setStatus(com.tansoflow.tansocore.model.plan.PlanStatus.ACTIVE.name());
        paidPlan.setBillingTiming(com.tansoflow.tansocore.model.plan.BillingTiming.IN_ADVANCE.name());
        paidPlan.setPriceAmount(new java.math.BigDecimal("9.00"));

        // Tanso collects, not Stripe — the mode the fresh seed script produces.
        com.tansoflow.tansocore.entity.AccountSetting setting = new com.tansoflow.tansocore.entity.AccountSetting();
        setting.setStripeMode(com.tansoflow.tansocore.model.api.external.StripeMode.NONE);
        when(accountService.retrieveAccountSettings(accountId.toString())).thenReturn(setting);

        org.mockito.Mockito.doThrow(new com.tansoflow.tansocore.model.exception.BudgetExceededException(
                        com.tansoflow.tansocore.model.apikey.type.SpendKind.MONEY,
                        java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
                        new java.math.BigDecimal("9.00"), java.time.Instant.now()))
                .when(keyBudgetService).assertWithinBudget(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq(com.tansoflow.tansocore.model.apikey.type.SpendKind.MONEY),
                        org.mockito.ArgumentMatchers.eq(new java.math.BigDecimal("9.00")));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> subscriptionService.subscribe(customer, paidPlan, accountId.toString(), null))
                .isInstanceOf(com.tansoflow.tansocore.model.exception.BudgetExceededException.class);

        // No subscription, and therefore no invoice, was created.
        verify(subscriptionRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(invoiceService);
    }

    // Regression: a paid subscription moves money, so the calling key's spend
    // budget has to gate it. Before this, an agent capped at a few dollars of
    // credit top-ups could still commit its customer to an expensive plan.
    // Found by /qa on 2026-08-21
    // Report: .gstack/qa-reports/qa-report-tanso-oss-2026-08-21.md
    @org.junit.jupiter.api.Test
    void paidSubscribeStopsBeforeStripeWhenTheKeyBudgetIsExhausted() {
        com.tansoflow.tansocore.entity.Account account = new com.tansoflow.tansocore.entity.Account();
        UUID accountId = UUID.randomUUID();
        account.setId(accountId);

        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setAccount(account);
        customer.setStripeDefaultPaymentMethodId("pm_saved");

        Plan paidPlan = new Plan();
        paidPlan.setId(UUID.randomUUID());
        paidPlan.setStatus(com.tansoflow.tansocore.model.plan.PlanStatus.ACTIVE.name());
        paidPlan.setBillingTiming(com.tansoflow.tansocore.model.plan.BillingTiming.IN_ADVANCE.name());
        paidPlan.setPriceAmount(new java.math.BigDecimal("9000.00"));

        com.tansoflow.tansocore.entity.AccountSetting setting = new com.tansoflow.tansocore.entity.AccountSetting();
        setting.setStripeMode(com.tansoflow.tansocore.model.api.external.StripeMode.STRIPE_INTEGRATION);
        when(accountService.retrieveAccountSettings(accountId.toString())).thenReturn(setting);
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId()))
                .thenReturn(java.util.List.of());

        org.mockito.Mockito.doThrow(new com.tansoflow.tansocore.model.exception.BudgetExceededException(
                        com.tansoflow.tansocore.model.apikey.type.SpendKind.MONEY,
                        new java.math.BigDecimal("5.00"), new java.math.BigDecimal("1.00"),
                        new java.math.BigDecimal("9000.00"), java.time.Instant.now()))
                .when(keyBudgetService).assertWithinBudget(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq(com.tansoflow.tansocore.model.apikey.type.SpendKind.MONEY),
                        org.mockito.ArgumentMatchers.eq(new java.math.BigDecimal("9000.00")));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> subscriptionService.subscribe(customer, paidPlan, accountId.toString(), null))
                .isInstanceOf(com.tansoflow.tansocore.model.exception.BudgetExceededException.class);

        // The whole point: no Stripe call, no charge, no checkout link handed back.
        verifyNoInteractions(stripeSyncService);
    }

    private void keyBudgetAndMandateAreExhausted(String amount) {
        org.mockito.Mockito.lenient().doThrow(new com.tansoflow.tansocore.model.exception.BudgetExceededException(
                        com.tansoflow.tansocore.model.apikey.type.SpendKind.MONEY,
                        new java.math.BigDecimal("5.00"), new java.math.BigDecimal("5.00"),
                        new java.math.BigDecimal(amount), java.time.Instant.now()))
                .when(keyBudgetService).assertWithinBudget(any(), any(), any());
        org.mockito.Mockito.lenient().doThrow(new com.tansoflow.tansocore.model.exception.SpendMandateExceededException(
                        "ref", new java.math.BigDecimal("5.00"), new java.math.BigDecimal("5.00"),
                        new java.math.BigDecimal(amount), "month", java.time.Instant.now().plusSeconds(60)))
                .when(keyBudgetService).assertWithinMandate(any(), any());
    }

    // A human pays the Stripe Checkout page in person, which is its own approval. An exhausted key budget used to
    // answer budget_exceeded instead of handing over the page.
    @org.junit.jupiter.api.Test
    void aPaidSubscribeWithNoCardReachesHostedCheckoutPastAnExhaustedKeyBudget() throws Exception {
        plan.setStatus(com.tansoflow.tansocore.model.plan.PlanStatus.ACTIVE.name());
        plan.setBillingTiming(com.tansoflow.tansocore.model.plan.BillingTiming.IN_ADVANCE.name());
        com.tansoflow.tansocore.entity.AccountSetting setting = new com.tansoflow.tansocore.entity.AccountSetting();
        setting.setStripeMode(com.tansoflow.tansocore.model.api.external.StripeMode.STRIPE_INTEGRATION);
        when(accountService.retrieveAccountSettings(account.getId().toString())).thenReturn(setting);
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId())).thenReturn(java.util.List.of());
        keyBudgetAndMandateAreExhausted("29.99");
        // Stop at Stripe: reaching it proves neither limit refused the page.
        when(stripeSyncService.createSubscriptionCheckoutSession(account.getId(), customer.getId(), plan.getId()))
                .thenThrow(new IllegalStateException("reached stripe"));

        callingWithAnApiKey(() -> org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> subscriptionService.subscribe(customer, plan, account.getId().toString(), null))
                .hasRootCauseMessage("reached stripe"));

        verify(keyBudgetService, never()).assertWithinBudget(any(), any(), any());
        verify(keyBudgetService, never()).assertWithinMandate(any(), any());
    }

    // Pass-through hands the agent Stripe's hosted invoice, which a human pays in person.
    @org.junit.jupiter.api.Test
    void aPaidSubscribeOnPassThroughIsNotRefusedByAnExhaustedKeyBudget() {
        plan.setStatus(com.tansoflow.tansocore.model.plan.PlanStatus.ACTIVE.name());
        plan.setBillingTiming(com.tansoflow.tansocore.model.plan.BillingTiming.IN_ADVANCE.name());
        com.tansoflow.tansocore.entity.AccountSetting setting = new com.tansoflow.tansocore.entity.AccountSetting();
        setting.setStripeMode(com.tansoflow.tansocore.model.api.external.StripeMode.PAYMENT_PASS_THROUGH);
        when(accountService.retrieveAccountSettings(account.getId().toString())).thenReturn(setting);
        keyBudgetAndMandateAreExhausted("29.99");
        // Creating the subscription is the first step past the guards.
        when(customerService.validateAndRetrieveCustomer(customer.getId().toString(), account.getId().toString()))
                .thenThrow(new IllegalStateException("past the guards"));

        callingWithAnApiKey(() -> org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> subscriptionService.subscribe(customer, plan, account.getId().toString(), null))
                .hasMessage("past the guards"));

        verify(keyBudgetService, never()).assertWithinBudget(any(), any(), any());
    }

    // The operator's per-charge cap is kept on hosted pages: it bounds what an agent may start, whoever pays.
    @org.junit.jupiter.api.Test
    void aPaidSubscribeWithNoCardIsStillHeldToTheOperatorsPerChargeCap() {
        plan.setStatus(com.tansoflow.tansocore.model.plan.PlanStatus.ACTIVE.name());
        plan.setBillingTiming(com.tansoflow.tansocore.model.plan.BillingTiming.IN_ADVANCE.name());
        com.tansoflow.tansocore.entity.AccountSetting setting = new com.tansoflow.tansocore.entity.AccountSetting();
        setting.setStripeMode(com.tansoflow.tansocore.model.api.external.StripeMode.STRIPE_INTEGRATION);
        setting.setAgentMaxTopupAmount(new java.math.BigDecimal("10.00"));
        when(accountService.retrieveAccountSettings(account.getId().toString())).thenReturn(setting);
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId())).thenReturn(java.util.List.of());

        callingWithAnApiKey(() -> org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> subscriptionService.subscribe(customer, plan, account.getId().toString(), null))
                .isInstanceOf(com.tansoflow.tansocore.model.exception.BudgetExceededException.class));

        verifyNoInteractions(stripeSyncService);
    }

    private UUID subscriptionId;
    private Subscription subscription;
    private Plan plan;
    private Customer customer;
    private Account account;

    @BeforeEach
    void setUp() {
        subscriptionId = UUID.randomUUID();
        account = new Account();
        account.setId(UUID.randomUUID());

        customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setAccount(account);

        plan = new Plan();
        plan.setId(UUID.randomUUID());
        plan.setIntervalMonths(1);
        plan.setPriceAmount(new BigDecimal("29.99"));

        subscription = new Subscription();
        subscription.setId(subscriptionId);
        subscription.setCustomer(customer);
        subscription.setPlan(plan);
        subscription.setAccount(account);
        subscription.setIsActive(true);
        subscription.setBillingAnchorDay((short) 1);
        subscription.setCurrentPeriodStart(Instant.now().minus(30, ChronoUnit.DAYS));
        subscription.setCurrentPeriodEnd(Instant.now().minus(1, ChronoUnit.MINUTES));

        // Stripe-billed subscribes lock the customer row first; the row exists in every test that gets there.
        org.mockito.Mockito.lenient().when(customerRepository.findByIdAndAccountIdForUpdate(any(), any()))
                .thenAnswer(i -> Optional.of(new Customer()));

        // A save assigns the id, as JPA does on persist; the charge-first upgrade keys Stripe on it.
        org.mockito.Mockito.lenient().when(subscriptionScheduledChangeRepository.save(any())).thenAnswer(i -> {
            SubscriptionScheduledChange change = i.getArgument(0);
            if (change.getId() == null) {
                change.setId(UUID.randomUUID());
            }
            if (!savedChanges.contains(change)) {
                savedChanges.add(change);
            }
            return change;
        });
        org.mockito.Mockito.lenient().when(subscriptionScheduledChangeRepository.findPendingUpgradeByIdForUpdate(any()))
                .thenAnswer(i -> savedChanges.stream()
                        .filter(c -> c.getId().equals(i.getArgument(0)) && "PENDING".equals(c.getStatus()))
                        .findFirst());
        org.mockito.Mockito.lenient().when(subscriptionScheduledChangeRepository.findById(any()))
                .thenAnswer(i -> savedChanges.stream().filter(c -> c.getId().equals(i.getArgument(0))).findFirst());
    }

    @Test
    void testProcessSingleSubscriptionCycle_FlatBilling_InAdvance() {
        // Setup
        plan.setBillingTiming(BillingTiming.IN_ADVANCE.name());

        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));
        when(invoiceService.hasPastDueInvoice(subscription)).thenReturn(false);
        when(invoiceService.existsInvoiceForPeriod(eq(subscription), any(), any())).thenReturn(false);

        // Execute
        subscriptionService.processSingleSubscriptionCycle(subscriptionId);

        // Verify
        ArgumentCaptor<CreateInvoiceParams> paramsCaptor = ArgumentCaptor.forClass(CreateInvoiceParams.class);
        verify(invoiceService).createNewInvoice(paramsCaptor.capture());

        CreateInvoiceParams params = paramsCaptor.getValue();
        assertEquals(InvoiceStatus.DUE, params.status());
        assertEquals(LocalDate.now(ZoneOffset.UTC), params.dueDate());
        assertEquals(InvoiceType.REGULAR, params.type());
        assertEquals(plan.getPriceAmount(), params.amount());
        
        verify(subscriptionRepository).save(subscription);
    }

    @Test
    void testProcessSingleSubscriptionCycle_UsageBilling_InArrears() {
        // Setup
        plan.setBillingTiming(BillingTiming.IN_ARREARS.name());

        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));
        when(invoiceService.hasPastDueInvoice(subscription)).thenReturn(false);
        when(invoiceService.existsInvoiceForPeriod(eq(subscription), any(), any())).thenReturn(false);

        // Execute
        subscriptionService.processSingleSubscriptionCycle(subscriptionId);

        // Verify
        ArgumentCaptor<CreateInvoiceParams> paramsCaptor = ArgumentCaptor.forClass(CreateInvoiceParams.class);
        verify(invoiceService).createNewInvoice(paramsCaptor.capture());

        CreateInvoiceParams params = paramsCaptor.getValue();
        assertEquals(InvoiceStatus.PENDING, params.status());
        // For IN_ARREARS, due date is calculated based on newPeriodEnd + anchor day
        // newPeriodEnd is roughly now + 1 month.
        
        assertEquals(InvoiceType.REGULAR, params.type());
        verify(subscriptionRepository).save(subscription);
    }

    @Test
    void testProcessSingleSubscriptionCycle_FlatBilling_InAdvance_FreePlan() {
        // Setup
        plan.setBillingTiming(BillingTiming.IN_ADVANCE.name());
        plan.setPriceAmount(BigDecimal.ZERO);

        Invoice nextInvoice = new Invoice();
        nextInvoice.setId(UUID.randomUUID());

        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));
        when(invoiceService.hasPastDueInvoice(subscription)).thenReturn(false);
        when(invoiceService.existsInvoiceForPeriod(eq(subscription), any(), any())).thenReturn(false);
        when(invoiceService.createNewInvoice(any())).thenReturn(nextInvoice);

        // Execute
        subscriptionService.processSingleSubscriptionCycle(subscriptionId);

        // Verify
        verify(invoiceService).markInvoiceAsPaid(nextInvoice);
        verify(subscriptionRepository).save(subscription);
    }

    @Test
    void testProcessSubscriptionCycles_CallsProcessSingleCycle() {
        // Setup
        when(subscriptionRepository.findActiveNeedingRollover(any(), any()))
                .thenReturn(new PageImpl<>(List.of(subscription), PageRequest.of(0, 500), 1))
                .thenReturn(new PageImpl<>(List.of()));

        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));

        // Execute
        subscriptionService.processSubscriptionCycles();

        // Verify
        verify(subscriptionRepository, atLeastOnce()).findById(subscriptionId);
    }

    @Test
    void testProcessSingleSubscriptionCycle_Hybrid_InAdvance() {
        // Setup: Hybrid plan (Flat + Usage features)
        plan.setBillingTiming(BillingTiming.IN_ADVANCE.name());
        plan.setPriceAmount(new BigDecimal("10.00"));

        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));
        when(invoiceService.hasPastDueInvoice(subscription)).thenReturn(false);
        when(invoiceService.existsInvoiceForPeriod(eq(subscription), any(), any())).thenReturn(false);

        // Execute
        subscriptionService.processSingleSubscriptionCycle(subscriptionId);

        // Verify: Invoice created for flat fee, status DUE
        ArgumentCaptor<CreateInvoiceParams> paramsCaptor = ArgumentCaptor.forClass(CreateInvoiceParams.class);
        verify(invoiceService).createNewInvoice(paramsCaptor.capture());

        CreateInvoiceParams params = paramsCaptor.getValue();
        assertEquals(InvoiceStatus.DUE, params.status());
        assertEquals(new BigDecimal("10.00"), params.amount());
        
        verify(subscriptionRepository).save(subscription);
    }

    @Test
    void testProcessSingleSubscriptionCycle_AvoidDoubleInvoicing() {
        // Setup
        plan.setBillingTiming(BillingTiming.IN_ADVANCE.name());

        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));
        when(invoiceService.hasPastDueInvoice(subscription)).thenReturn(false);
        // Simulate that an invoice ALREADY exists for the next period
        when(invoiceService.existsInvoiceForPeriod(eq(subscription), any(), any())).thenReturn(true);

        // Execute
        subscriptionService.processSingleSubscriptionCycle(subscriptionId);

        // Verify: createNewInvoice should NOT be called
        verify(invoiceService, never()).createNewInvoice(any(CreateInvoiceParams.class));
        verify(subscriptionRepository).save(subscription);
    }

    @Test
    void testProcessSingleSubscriptionCycle_AvoidRolloverWhenPastDue() {
        // Setup
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));
        // Simulate a PAST_DUE invoice exists
        when(invoiceService.hasPastDueInvoice(subscription)).thenReturn(true);

        // Execute
        subscriptionService.processSingleSubscriptionCycle(subscriptionId);

        // Verify: No new period set and no invoice created
        verify(invoiceService, never()).createNewInvoice(any(CreateInvoiceParams.class));
        // subscriptionRepository.save(subscription) is only called at the end if rollover happened.
        // Wait, if it returns early, save is not called.
        verify(subscriptionRepository, never()).save(subscription);
    }

    @Test
    void testGetScheduledChangesByAccount() {
        UUID accountId = account.getId();
        SubscriptionScheduledChange change = new SubscriptionScheduledChange();
        SubscriptionScheduledChangeDto dto = new SubscriptionScheduledChangeDto();

        when(subscriptionScheduledChangeRepository.findAllPendingChangesByAccountId(accountId))
                .thenReturn(List.of(change));
        when(subscriptionScheduledChangeMapper.toDtoList(any()))
                .thenReturn(List.of(dto));

        List<SubscriptionScheduledChangeDto> result = subscriptionService.getScheduledChangesByAccount(accountId.toString());

        assertEquals(1, result.size());
        verify(subscriptionScheduledChangeRepository).findAllPendingChangesByAccountId(accountId);
    }

    @Test
    void testGetScheduledCancellationsByAccount() {
        UUID accountId = account.getId();
        Subscription sub = new Subscription();
        SubscriptionDto dto = new SubscriptionDto();

        when(subscriptionRepository.findAllScheduledCancellationsByAccountId(accountId))
                .thenReturn(List.of(sub));
        when(subscriptionMapper.subscriptionEntityListToSubscriptionDtoList(any()))
                .thenReturn(List.of(dto));

        List<SubscriptionDto> result = subscriptionService.getScheduledCancellationsByAccount(accountId.toString());

        assertEquals(1, result.size());
        verify(subscriptionRepository).findAllScheduledCancellationsByAccountId(accountId);
    }

    @Test
    void testGetSubscriptionsByCustomer_WithScheduledChange() {
        // Setup
        String customerUuid = customer.getId().toString();
        String accountIdStr = account.getId().toString();

        when(customerService.validateAndRetrieveCustomer(customerUuid, accountIdStr)).thenReturn(customer);
        when(subscriptionRepository.findSubscriptionsByCustomer(customer)).thenReturn(List.of(subscription));

        SubscriptionDto dto = new SubscriptionDto();
        dto.setId(subscription.getId().toString());
        when(subscriptionMapper.subscriptionEntityListToSubscriptionDtoList(any())).thenReturn(List.of(dto));

        when(subscriptionScheduledChangeRepository.existsSubscriptionScheduledChangeBySubscriptionIn(any())).thenReturn(true);

        SubscriptionScheduledChange scheduledChange = new SubscriptionScheduledChange();
        scheduledChange.setId(UUID.randomUUID());
        scheduledChange.setSubscription(subscription);
        scheduledChange.setStatus("PENDING");
        scheduledChange.setType("DOWNGRADE");
        scheduledChange.setEffectiveAt(Instant.now().plus(1, ChronoUnit.DAYS));

        when(subscriptionScheduledChangeRepository.findSubscriptionScheduledChangesByStatusAndSubscriptionIsIn(eq("PENDING"), any()))
                .thenReturn(List.of(scheduledChange));

        SubscriptionScheduledChangeDto scDto = new SubscriptionScheduledChangeDto();
        scDto.setId(scheduledChange.getId());
        scDto.setType("DOWNGRADE");
        when(subscriptionScheduledChangeMapper.toDto(scheduledChange)).thenReturn(scDto);

        // Execute
        List<SubscriptionDto> result = subscriptionService.getSubscriptionsByCustomer(customerUuid, accountIdStr);

        // Verify
        assertEquals(1, result.size());
        assertEquals(scDto, result.getFirst().getScheduledChange());
        verify(subscriptionScheduledChangeMapper).toDto(scheduledChange);
    }

    @Test
    void testSubscribe_FreeInAdvancePlan_FullSync_MarksInvoiceAsPaid() {
        // Setup: free IN_ADVANCE plan
        plan.setStatus(PlanStatus.ACTIVE.name());
        plan.setBillingTiming(BillingTiming.IN_ADVANCE.name());
        plan.setPriceAmount(BigDecimal.ZERO);
        plan.setIntervalMonths(1);

        String accountId = account.getId().toString();

        // createSubscription() internally calls these
        when(customerService.validateAndRetrieveCustomer(customer.getId().toString(), accountId))
                .thenReturn(customer);
        when(planService.retrievePlan(account, plan.getId()))
                .thenReturn(plan);

        // saveAndFlush on the new subscription
        when(subscriptionRepository.saveAndFlush(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Invoice creation returns an InvoiceDto with a known ID
        String invoiceDtoId = UUID.randomUUID().toString();
        InvoiceDto invoiceDto = new InvoiceDto();
        invoiceDto.setId(invoiceDtoId);
        when(invoiceService.createNewInvoice(any(Subscription.class), any(LocalDate.class),
                eq(InvoiceStatus.DUE), eq(InvoiceType.IN_ADVANCE_INITIAL)))
                .thenReturn(invoiceDto);

        // The fix: retrieve the Invoice entity, then mark paid with entity overload
        Invoice invoiceEntity = new Invoice();
        invoiceEntity.setId(UUID.fromString(invoiceDtoId));
        when(invoiceService.retrieveInvoiceByInvoiceIdAndAccount(invoiceDtoId, accountId))
                .thenReturn(invoiceEntity);

        // Mapper for the response
        SubscriptionDto subscriptionDto = new SubscriptionDto();
        when(subscriptionMapper.subscriptionEntityToSubscriptionDto(any(Subscription.class)))
                .thenReturn(subscriptionDto);

        // Execute
        SubscribedCustomerResponse response = subscriptionService.subscribe(customer, plan, accountId);

        // Verify: the entity-fetching path was used (the fix)
        verify(invoiceService).retrieveInvoiceByInvoiceIdAndAccount(invoiceDtoId, accountId);

        // Verify: markInvoiceAsPaid called with the Invoice entity (REQUIRED propagation)
        verify(invoiceService).markInvoiceAsPaid(invoiceEntity);

        // Verify: the old String-based overload (REQUIRES_NEW) was NOT called
        verify(invoiceService, never()).markInvoiceAsPaid(anyString());

        // Verify: subscription activated event published for STRIPE_INTEGRATION
        verify(eventPublisher).publishEvent(any(SubscriptionActivatedEvent.class));

        // Verify: response contains the expected DTOs
        assertNotNull(response);
        assertEquals(invoiceDto, response.getInvoice());
        assertEquals(subscriptionDto, response.getSubscription());
    }

    @Test
    void testSubscribe_PaidInAdvancePlan_SetsInactiveAndUsesInitialType() {
        // Setup: paid IN_ADVANCE plan
        plan.setStatus(PlanStatus.ACTIVE.name());
        plan.setBillingTiming(BillingTiming.IN_ADVANCE.name());
        plan.setPriceAmount(new BigDecimal("29.99"));
        plan.setIntervalMonths(1);

        String accountId = account.getId().toString();

        when(customerService.validateAndRetrieveCustomer(customer.getId().toString(), accountId))
                .thenReturn(customer);
        when(planService.retrievePlan(account, plan.getId()))
                .thenReturn(plan);

        when(subscriptionRepository.saveAndFlush(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        String invoiceDtoId = UUID.randomUUID().toString();
        InvoiceDto invoiceDto = new InvoiceDto();
        invoiceDto.setId(invoiceDtoId);
        when(invoiceService.createNewInvoice(any(Subscription.class), any(LocalDate.class),
                eq(InvoiceStatus.DUE), eq(InvoiceType.IN_ADVANCE_INITIAL)))
                .thenReturn(invoiceDto);

        SubscriptionDto subscriptionDto = new SubscriptionDto();
        when(subscriptionMapper.subscriptionEntityToSubscriptionDto(any(Subscription.class)))
                .thenReturn(subscriptionDto);

        // Execute
        SubscribedCustomerResponse response = subscriptionService.subscribe(customer, plan, accountId);

        // Verify: invoice type is IN_ADVANCE_INITIAL
        verify(invoiceService).createNewInvoice(any(Subscription.class), any(LocalDate.class),
                eq(InvoiceStatus.DUE), eq(InvoiceType.IN_ADVANCE_INITIAL));

        // Verify: markInvoiceAsPaid was NOT called (paid plan, awaiting payment)
        verify(invoiceService, never()).markInvoiceAsPaid(any(Invoice.class));
        verify(invoiceService, never()).markInvoiceAsPaid(anyString());

        // Verify: SubscriptionActivatedEvent NOT published (deferred until invoice paid)
        verify(eventPublisher, never()).publishEvent(any(SubscriptionActivatedEvent.class));

        // Verify: subscription saved with isActive = false
        ArgumentCaptor<Subscription> subCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository, atLeastOnce()).saveAndFlush(subCaptor.capture());
        assertFalse(subCaptor.getValue().getIsActive());

        assertNotNull(response);
    }

    // An agent that got a 402 retries the same subscribe after paying or nominating an owner. The retry
    // must return the subscription and DUE invoice already waiting, not create a second pair.
    @Test
    void clientSubscribeRetryReturnsThePendingSubscriptionInsteadOfASecondOne() {
        customer.setExternalClientCustomerId("agent_retry");
        customer.setAgentStatus(com.tansoflow.tansocore.entity.AgentStatus.PROVISIONAL);
        plan.setKey("starter");
        plan.setStatus(com.tansoflow.tansocore.model.plan.PlanStatus.ACTIVE.name());
        subscription.setIsActive(false);
        com.tansoflow.tansocore.entity.Invoice due = new com.tansoflow.tansocore.entity.Invoice();
        due.setId(UUID.randomUUID());
        due.setSubscription(subscription);
        due.setStatus(InvoiceStatus.DUE.name());
        com.tansoflow.tansocore.model.subscription.SubscriptionDto subscriptionDto =
                new com.tansoflow.tansocore.model.subscription.SubscriptionDto();
        subscriptionDto.setId(subscriptionId.toString());
        com.tansoflow.tansocore.model.billing.InvoiceDto invoiceDto = new com.tansoflow.tansocore.model.billing.InvoiceDto();
        invoiceDto.setId(due.getId().toString());
        invoiceDto.setStatus("DUE");

        when(customerService.retrieveCustomerByExternalClientCustomerIdAndAccount("agent_retry", account.getId().toString()))
                .thenReturn(customer);
        when(planService.retrievePlanByIdOrKey(account, "starter")).thenReturn(plan);
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId())).thenReturn(java.util.List.of(subscription));
        when(invoiceService.retrieveCurrentlyDueBySubscription(subscription)).thenReturn(due);
        when(subscriptionMapper.subscriptionEntityToSubscriptionDto(subscription)).thenReturn(subscriptionDto);
        when(invoiceMapper.invoiceEntityToInvoiceDto(due)).thenReturn(invoiceDto);

        com.tansoflow.tansocore.model.subscription.request.ClientSubscriptionRequest request =
                new com.tansoflow.tansocore.model.subscription.request.ClientSubscriptionRequest();
        request.setCustomerReferenceId("agent_retry");
        request.setPlanKey("starter");

        var response = subscriptionService.clientSubscribeCustomer(request, account.getId().toString());

        assertEquals(subscriptionId.toString(), response.getSubscription().getId());
        assertEquals("DUE", response.getInvoice().getStatus());
        verify(invoiceService, org.mockito.Mockito.never()).createNewInvoice(any(), any(), any(), any());
    }

    // After the agent pays, the same retry must not open a second subscription: it gets the active one back.
    @Test
    void clientSubscribeRetryAfterPayingReturnsTheActiveSubscription() {
        customer.setExternalClientCustomerId("agent_paid");
        customer.setAgentStatus(com.tansoflow.tansocore.entity.AgentStatus.CLAIMED);
        plan.setKey("starter");
        plan.setStatus(com.tansoflow.tansocore.model.plan.PlanStatus.ACTIVE.name());
        subscription.setIsActive(true);
        com.tansoflow.tansocore.model.subscription.SubscriptionDto subscriptionDto =
                new com.tansoflow.tansocore.model.subscription.SubscriptionDto();
        subscriptionDto.setId(subscriptionId.toString());

        when(customerService.retrieveCustomerByExternalClientCustomerIdAndAccount("agent_paid", account.getId().toString()))
                .thenReturn(customer);
        when(planService.retrievePlanByIdOrKey(account, "starter")).thenReturn(plan);
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId())).thenReturn(java.util.List.of(subscription));
        when(subscriptionMapper.subscriptionEntityToSubscriptionDto(subscription)).thenReturn(subscriptionDto);

        com.tansoflow.tansocore.model.subscription.request.ClientSubscriptionRequest request =
                new com.tansoflow.tansocore.model.subscription.request.ClientSubscriptionRequest();
        request.setCustomerReferenceId("agent_paid");
        request.setPlanKey("starter");

        var response = subscriptionService.clientSubscribeCustomer(request, account.getId().toString());

        assertEquals(subscriptionId.toString(), response.getSubscription().getId());
        assertEquals(null, response.getInvoice());
        verify(keyBudgetService, org.mockito.Mockito.never()).assertWithinBudget(any(), any(), any());
        verify(subscriptionRepository, org.mockito.Mockito.never()).saveAndFlush(any());
    }

    // A tenant-created customer (no agent status) keeps the old behavior: no short-circuit, the budget check runs.
    @Test
    void subscribeForNonAgentCustomerDoesNotReuseAPendingSubscription() {
        customer.setAgentStatus(null);
        plan.setStatus(com.tansoflow.tansocore.model.plan.PlanStatus.ACTIVE.name());
        org.mockito.Mockito.doThrow(new IllegalStateException("stop after the guards")).when(keyBudgetService)
                .assertWithinBudget(any(), any(), any());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> subscriptionService.subscribe(customer, plan, account.getId().toString(), null));
        verify(invoiceService, org.mockito.Mockito.never()).retrieveCurrentlyDueBySubscription(any());
    }

    // --- plan change / upgrade -------------------------------------------------------------------------------
    // The upgrade path had no spend guard at all and granted the new plan before the adjustment invoice was paid,
    // so a key with no budget could raise its own customer onto an expensive plan. Found reviewing the agent
    // surface after the 2026-09-17 end-to-end run; filed as issue #37.

    private Subscription subscriptionOnPlanForUpgrade(Plan current, String currentSubscriptionId, String accountIdString) {
        Subscription existing = new Subscription();
        existing.setId(UUID.fromString(currentSubscriptionId));
        existing.setCustomer(customer);
        existing.setAccount(account);
        existing.setPlan(current);
        existing.setIsActive(true);
        existing.setCurrentPeriodStart(java.time.Instant.now().minus(java.time.Duration.ofDays(15)));
        existing.setCurrentPeriodEnd(java.time.Instant.now().plus(java.time.Duration.ofDays(15)));
        // The upgrade reads it under a row lock; cancelling scheduled changes reads it plainly. Not every test
        // reaches both, hence lenient.
        org.mockito.Mockito.lenient().when(subscriptionRepository.findSubscriptionByUuidAndAccountIdForUpdate(
                UUID.fromString(currentSubscriptionId), UUID.fromString(accountIdString))).thenReturn(existing);
        org.mockito.Mockito.lenient().when(subscriptionRepository.findSubscriptionByUuidAndAccountId(
                UUID.fromString(currentSubscriptionId), UUID.fromString(accountIdString))).thenReturn(existing);
        return existing;
    }

    private Plan inAdvancePlan(String key, String price) {
        Plan p = new Plan();
        p.setId(UUID.randomUUID());
        p.setKey(key);
        p.setStatus(com.tansoflow.tansocore.model.plan.PlanStatus.ACTIVE.name());
        p.setBillingTiming(com.tansoflow.tansocore.model.plan.BillingTiming.IN_ADVANCE.name());
        p.setPriceAmount(new java.math.BigDecimal(price));
        return p;
    }

    private void callingWithAnApiKey(Runnable body) {
        com.tansoflow.tansocore.auth.UserContext ctx = new com.tansoflow.tansocore.auth.UserContext(
                account.getId().toString(), customer.getId().toString(), "ref", java.util.List.of("read", "purchase"),
                null, UUID.randomUUID());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new com.tansoflow.tansocore.auth.UserContextAuthentication(ctx, java.util.List.of()));
        try {
            body.run();
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @org.junit.jupiter.api.Test
    void upgradeOnAnApiKeyWaitsForTheAdjustmentInvoiceInsteadOfGrantingThePlan() {
        String accountIdString = account.getId().toString();
        String currentSubscriptionId = UUID.randomUUID().toString();
        Plan free = inAdvancePlan("developer_demo", "0.00");
        Plan starter = inAdvancePlan("starter", "149.00");
        Subscription existing = subscriptionOnPlanForUpgrade(free, currentSubscriptionId, accountIdString);
        when(planService.retrievePlan(account, UUID.fromString(starter.getId().toString()))).thenReturn(starter);

        com.tansoflow.tansocore.entity.AccountSetting setting = new com.tansoflow.tansocore.entity.AccountSetting();
        setting.setStripeMode(com.tansoflow.tansocore.model.api.external.StripeMode.PAYMENT_PASS_THROUGH);
        when(accountService.retrieveAccountSettings(accountIdString)).thenReturn(setting);

        com.tansoflow.tansocore.entity.Invoice adjustment = new com.tansoflow.tansocore.entity.Invoice();
        adjustment.setId(UUID.randomUUID());
        when(invoiceService.createAdjustmentInvoice(eq(free), eq(starter), eq(existing), any(), any()))
                .thenReturn(adjustment);

        callingWithAnApiKey(() -> {
            com.tansoflow.tansocore.model.subscription.UpgradeResult pending = subscriptionService.upgradeSubscription(
                    currentSubscriptionId, accountIdString, starter.getId().toString(), true);
            org.assertj.core.api.Assertions.assertThat(pending.pendingInvoiceId()).isEqualTo(adjustment.getId());
        });

        // The plan is not granted and no entitlement moves until the invoice is paid.
        org.assertj.core.api.Assertions.assertThat(existing.getPlan()).isEqualTo(free);
        verifyNoInteractions(entitlementService);

        ArgumentCaptor<com.tansoflow.tansocore.entity.SubscriptionScheduledChange> saved =
                ArgumentCaptor.forClass(com.tansoflow.tansocore.entity.SubscriptionScheduledChange.class);
        verify(subscriptionScheduledChangeRepository).save(saved.capture());
        org.assertj.core.api.Assertions.assertThat(saved.getValue().getStatus())
                .isEqualTo(com.tansoflow.tansocore.model.subscription.type.SubscriptionScheduledChangeStatus.PENDING.name());
        org.assertj.core.api.Assertions.assertThat(saved.getValue().getAdjustmentInvoice()).isEqualTo(adjustment);
        org.assertj.core.api.Assertions.assertThat(saved.getValue().getToPlan()).isEqualTo(starter);
    }

    // Pass-through hands the agent Stripe's hosted invoice for the adjustment; a human pays it in person, so neither
    // the key budget nor the mandate refuses the upgrade. The plan still waits on the invoice.
    @org.junit.jupiter.api.Test
    void anUpgradePaidOnAPassThroughHostedInvoiceIsNotRefusedByAnExhaustedBudgetOrMandate() {
        String accountIdString = account.getId().toString();
        String currentSubscriptionId = UUID.randomUUID().toString();
        Plan free = inAdvancePlan("developer_demo", "0.00");
        Plan starter = inAdvancePlan("starter", "149.00");
        Subscription existing = subscriptionOnPlanForUpgrade(free, currentSubscriptionId, accountIdString);
        when(planService.retrievePlan(account, UUID.fromString(starter.getId().toString()))).thenReturn(starter);

        com.tansoflow.tansocore.entity.AccountSetting setting = new com.tansoflow.tansocore.entity.AccountSetting();
        setting.setStripeMode(com.tansoflow.tansocore.model.api.external.StripeMode.PAYMENT_PASS_THROUGH);
        when(accountService.retrieveAccountSettings(accountIdString)).thenReturn(setting);
        keyBudgetAndMandateAreExhausted("74.50");

        com.tansoflow.tansocore.entity.Invoice adjustment = new com.tansoflow.tansocore.entity.Invoice();
        adjustment.setId(UUID.randomUUID());
        when(invoiceService.createAdjustmentInvoice(eq(free), eq(starter), eq(existing), any(), any()))
                .thenReturn(adjustment);

        callingWithAnApiKey(() -> {
            com.tansoflow.tansocore.model.subscription.UpgradeResult pending = subscriptionService.upgradeSubscription(
                    currentSubscriptionId, accountIdString, starter.getId().toString(), true);
            org.assertj.core.api.Assertions.assertThat(pending.pendingInvoiceId()).isEqualTo(adjustment.getId());
        });

        org.assertj.core.api.Assertions.assertThat(existing.getPlan()).isEqualTo(free);
        verify(keyBudgetService, never()).assertWithinBudget(any(), any(), any());
        verify(keyBudgetService, never()).assertWithinMandate(any(), any());
    }

    // Where Stripe runs the billing, the charge-first upgrade charges the saved card in this same call, so both
    // limits still apply before Stripe is asked.
    @org.junit.jupiter.api.Test
    void aChargeFirstUpgradeIsStillRefusedByAnExhaustedMandate() {
        String accountIdString = account.getId().toString();
        String currentSubscriptionId = UUID.randomUUID().toString();
        Plan free = inAdvancePlan("developer_demo", "0.00");
        Plan starter = inAdvancePlan("starter", "149.00");
        subscriptionOnPlanForUpgrade(free, currentSubscriptionId, accountIdString);
        when(planService.retrievePlan(account, UUID.fromString(starter.getId().toString()))).thenReturn(starter);
        stripeIntegration(accountIdString);
        org.mockito.Mockito.doThrow(new com.tansoflow.tansocore.model.exception.SpendMandateExceededException(
                        "ref", new java.math.BigDecimal("5.00"), new java.math.BigDecimal("5.00"),
                        new java.math.BigDecimal("74.50"), "month", java.time.Instant.now().plusSeconds(60)))
                .when(keyBudgetService).assertWithinMandate(eq(customer.getId()), any());

        callingWithAnApiKey(() -> org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> subscriptionService.upgradeSubscription(
                                currentSubscriptionId, accountIdString, starter.getId().toString(), true))
                .isInstanceOf(com.tansoflow.tansocore.model.exception.SpendMandateExceededException.class));

        verifyNoInteractions(stripeSyncService);
    }

    // Where Tanso bills with no payment processor there is no hosted page, so the key budget still applies.
    @org.junit.jupiter.api.Test
    void upgradeOnAnApiKeyStopsWhenTheKeyBudgetCannotCoverTheProration() {
        String accountIdString = account.getId().toString();
        String currentSubscriptionId = UUID.randomUUID().toString();
        Plan free = inAdvancePlan("developer_demo", "0.00");
        Plan starter = inAdvancePlan("starter", "149.00");
        subscriptionOnPlanForUpgrade(free, currentSubscriptionId, accountIdString);
        when(planService.retrievePlan(account, UUID.fromString(starter.getId().toString()))).thenReturn(starter);

        com.tansoflow.tansocore.entity.AccountSetting setting = new com.tansoflow.tansocore.entity.AccountSetting();
        setting.setStripeMode(com.tansoflow.tansocore.model.api.external.StripeMode.NONE);
        when(accountService.retrieveAccountSettings(accountIdString)).thenReturn(setting);

        org.mockito.Mockito.doThrow(new com.tansoflow.tansocore.model.exception.BudgetExceededException(
                        com.tansoflow.tansocore.model.apikey.type.SpendKind.MONEY,
                        new java.math.BigDecimal("5.00"), java.math.BigDecimal.ZERO,
                        new java.math.BigDecimal("74.50"), java.time.Instant.now()))
                .when(keyBudgetService).assertWithinBudget(any(),
                        eq(com.tansoflow.tansocore.model.apikey.type.SpendKind.MONEY), any());

        callingWithAnApiKey(() -> org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> subscriptionService.upgradeSubscription(
                                currentSubscriptionId, accountIdString, starter.getId().toString(), true))
                .isInstanceOf(com.tansoflow.tansocore.model.exception.BudgetExceededException.class));

        // No invoice was raised and no plan was swapped.
        verifyNoInteractions(invoiceService);
        verify(subscriptionScheduledChangeRepository, org.mockito.Mockito.never()).save(any());
    }

    @org.junit.jupiter.api.Test
    void askingForTheSameUpgradeTwiceReturnsTheSameInvoiceInsteadOfChargingAgain() {
        String accountIdString = account.getId().toString();
        String currentSubscriptionId = UUID.randomUUID().toString();
        Plan free = inAdvancePlan("developer_demo", "0.00");
        Plan starter = inAdvancePlan("starter", "149.00");
        Subscription existing = subscriptionOnPlanForUpgrade(free, currentSubscriptionId, accountIdString);
        when(planService.retrievePlan(account, UUID.fromString(starter.getId().toString()))).thenReturn(starter);

        com.tansoflow.tansocore.entity.AccountSetting setting = new com.tansoflow.tansocore.entity.AccountSetting();
        setting.setStripeMode(com.tansoflow.tansocore.model.api.external.StripeMode.PAYMENT_PASS_THROUGH);
        when(accountService.retrieveAccountSettings(accountIdString)).thenReturn(setting);

        com.tansoflow.tansocore.entity.Invoice open = new com.tansoflow.tansocore.entity.Invoice();
        open.setId(UUID.randomUUID());
        open.setStatus(com.tansoflow.tansocore.model.billing.type.InvoiceStatus.DUE.name());
        com.tansoflow.tansocore.entity.SubscriptionScheduledChange waiting =
                new com.tansoflow.tansocore.entity.SubscriptionScheduledChange();
        waiting.setSubscription(existing);
        waiting.setToPlan(starter);
        waiting.setAdjustmentInvoice(open);
        when(subscriptionScheduledChangeRepository.findPendingUpgradeBySubscription(existing))
                .thenReturn(java.util.Optional.of(waiting));

        callingWithAnApiKey(() -> {
            com.tansoflow.tansocore.model.subscription.UpgradeResult pending = subscriptionService.upgradeSubscription(
                    currentSubscriptionId, accountIdString, starter.getId().toString(), true);
            org.assertj.core.api.Assertions.assertThat(pending.pendingInvoiceId()).isEqualTo(open.getId());
        });

        verifyNoInteractions(invoiceService);
        verify(subscriptionScheduledChangeRepository, org.mockito.Mockito.never()).save(any());
    }

    // Two concurrent calls (an agent retrying after a timeout) both used to read "nothing pending" and each raised
    // a payable adjustment invoice. The subscription row is now locked before that check, so the second call waits
    // for the first to commit and then finds its pending change.
    @org.junit.jupiter.api.Test
    void upgradeLocksTheSubscriptionBeforeCheckingForAPendingUpgrade() {
        String accountIdString = account.getId().toString();
        String currentSubscriptionId = UUID.randomUUID().toString();
        Plan free = inAdvancePlan("developer_demo", "0.00");
        Plan starter = inAdvancePlan("starter", "149.00");
        Subscription existing = subscriptionOnPlanForUpgrade(free, currentSubscriptionId, accountIdString);
        when(planService.retrievePlan(account, UUID.fromString(starter.getId().toString()))).thenReturn(starter);

        com.tansoflow.tansocore.entity.AccountSetting setting = new com.tansoflow.tansocore.entity.AccountSetting();
        setting.setStripeMode(com.tansoflow.tansocore.model.api.external.StripeMode.PAYMENT_PASS_THROUGH);
        when(accountService.retrieveAccountSettings(accountIdString)).thenReturn(setting);

        com.tansoflow.tansocore.entity.Invoice open = new com.tansoflow.tansocore.entity.Invoice();
        open.setId(UUID.randomUUID());
        open.setStatus(com.tansoflow.tansocore.model.billing.type.InvoiceStatus.DUE.name());
        com.tansoflow.tansocore.entity.SubscriptionScheduledChange waiting =
                new com.tansoflow.tansocore.entity.SubscriptionScheduledChange();
        waiting.setSubscription(existing);
        waiting.setToPlan(starter);
        waiting.setAdjustmentInvoice(open);
        when(subscriptionScheduledChangeRepository.findPendingUpgradeBySubscription(existing))
                .thenReturn(java.util.Optional.of(waiting));

        callingWithAnApiKey(() -> subscriptionService.upgradeSubscription(
                currentSubscriptionId, accountIdString, starter.getId().toString(), true));

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(subscriptionRepository, subscriptionScheduledChangeRepository);
        inOrder.verify(subscriptionRepository).findSubscriptionByUuidAndAccountIdForUpdate(
                UUID.fromString(currentSubscriptionId), UUID.fromString(accountIdString));
        inOrder.verify(subscriptionScheduledChangeRepository).findPendingUpgradeBySubscription(existing);
        verify(subscriptionRepository, org.mockito.Mockito.never()).findSubscriptionByUuidAndAccountId(any(), any());
    }

    // The upgrade's credit delta is keyed on its scheduled change, so the change is saved before the grant.
    @org.junit.jupiter.api.Test
    void immediateUpgradeGrantsTheCreditDeltaUnderTheScheduledChangeId() {
        String accountIdString = account.getId().toString();
        String currentSubscriptionId = UUID.randomUUID().toString();
        Plan free = inAdvancePlan("developer_demo", "0.00");
        Plan starter = inAdvancePlan("starter", "149.00");
        Subscription existing = subscriptionOnPlanForUpgrade(free, currentSubscriptionId, accountIdString);
        when(planService.retrievePlan(account, UUID.fromString(starter.getId().toString()))).thenReturn(starter);

        com.tansoflow.tansocore.entity.AccountSetting setting = new com.tansoflow.tansocore.entity.AccountSetting();
        setting.setStripeMode(com.tansoflow.tansocore.model.api.external.StripeMode.PAYMENT_PASS_THROUGH);
        when(accountService.retrieveAccountSettings(accountIdString)).thenReturn(setting);
        when(invoiceService.createAdjustmentInvoice(eq(free), eq(starter), eq(existing), any(), any()))
                .thenReturn(new com.tansoflow.tansocore.entity.Invoice());

        UUID changeId = UUID.randomUUID();
        org.mockito.Mockito.doAnswer(i -> {
            com.tansoflow.tansocore.entity.SubscriptionScheduledChange change = i.getArgument(0);
            change.setId(changeId);
            return change;
        }).when(subscriptionScheduledChangeRepository).save(any());

        subscriptionService.upgradeSubscription(currentSubscriptionId, accountIdString, starter.getId().toString(), true);

        verify(creditService).grantUpgradeDelta(existing, free, starter, changeId);
    }

    @org.junit.jupiter.api.Test
    void changingTheTargetPlanVoidsTheInvoiceNobodyCanPayAnyMore() {
        String accountIdString = account.getId().toString();
        String currentSubscriptionId = UUID.randomUUID().toString();
        Plan free = inAdvancePlan("developer_demo", "0.00");
        Plan starter = inAdvancePlan("starter", "149.00");
        Plan growth = inAdvancePlan("growth", "499.00");
        Subscription existing = subscriptionOnPlanForUpgrade(free, currentSubscriptionId, accountIdString);
        when(planService.retrievePlan(account, UUID.fromString(growth.getId().toString()))).thenReturn(growth);

        com.tansoflow.tansocore.entity.AccountSetting setting = new com.tansoflow.tansocore.entity.AccountSetting();
        setting.setStripeMode(com.tansoflow.tansocore.model.api.external.StripeMode.PAYMENT_PASS_THROUGH);
        when(accountService.retrieveAccountSettings(accountIdString)).thenReturn(setting);

        com.tansoflow.tansocore.entity.Invoice stale = new com.tansoflow.tansocore.entity.Invoice();
        stale.setId(UUID.randomUUID());
        stale.setStatus(com.tansoflow.tansocore.model.billing.type.InvoiceStatus.DUE.name());
        com.tansoflow.tansocore.entity.SubscriptionScheduledChange waiting =
                new com.tansoflow.tansocore.entity.SubscriptionScheduledChange();
        waiting.setSubscription(existing);
        waiting.setToPlan(starter);
        waiting.setAdjustmentInvoice(stale);
        when(subscriptionScheduledChangeRepository.findPendingUpgradeBySubscription(existing))
                .thenReturn(java.util.Optional.of(waiting));

        com.tansoflow.tansocore.entity.Invoice fresh = new com.tansoflow.tansocore.entity.Invoice();
        fresh.setId(UUID.randomUUID());
        when(invoiceService.createAdjustmentInvoice(eq(free), eq(growth), eq(existing), any(), any()))
                .thenReturn(fresh);

        callingWithAnApiKey(() -> {
            com.tansoflow.tansocore.model.subscription.UpgradeResult pending = subscriptionService.upgradeSubscription(
                    currentSubscriptionId, accountIdString, growth.getId().toString(), true);
            org.assertj.core.api.Assertions.assertThat(pending.pendingInvoiceId()).isEqualTo(fresh.getId());
        });

        verify(invoiceService).voidInvoice(stale);
    }

    // Cancelling a scheduled change leaves its invoice payable unless it is voided, and paying one nobody can
    // fulfil takes the money for nothing. Every cancel surface goes through this method: the DELETE endpoint,
    // the console, scheduling a downgrade, and retargeting an upgrade.
    @org.junit.jupiter.api.Test
    void cancellingScheduledChangesVoidsTheInvoiceBehindAPendingUpgrade() {
        String currentSubscriptionId = UUID.randomUUID().toString();
        Plan free = inAdvancePlan("developer_demo", "0.00");
        Plan starter = inAdvancePlan("starter", "149.00");
        Subscription existing = subscriptionOnPlanForUpgrade(free, currentSubscriptionId, account.getId().toString());

        com.tansoflow.tansocore.entity.Invoice open = new com.tansoflow.tansocore.entity.Invoice();
        open.setId(UUID.randomUUID());
        open.setStatus(com.tansoflow.tansocore.model.billing.type.InvoiceStatus.DUE.name());
        com.tansoflow.tansocore.entity.SubscriptionScheduledChange waiting =
                new com.tansoflow.tansocore.entity.SubscriptionScheduledChange();
        waiting.setSubscription(existing);
        waiting.setToPlan(starter);
        waiting.setAdjustmentInvoice(open);
        when(subscriptionScheduledChangeRepository.findPendingUpgradeBySubscription(existing))
                .thenReturn(java.util.Optional.of(waiting));

        subscriptionService.cancelScheduledChangesForSubscription(
                UUID.fromString(currentSubscriptionId), account.getId());

        verify(invoiceService).voidInvoice(open);
        verify(subscriptionScheduledChangeRepository).cancelAllScheduledChanges(existing);
    }

    // An arrears upgrade takes no money on this call, so capping it would refuse an agent over money that has
    // not moved. The charge lands on the next invoice instead.
    @org.junit.jupiter.api.Test
    void anArrearsUpgradeDoesNotConsultTheKeyBudget() {
        String accountIdString = account.getId().toString();
        String currentSubscriptionId = UUID.randomUUID().toString();
        Plan small = inAdvancePlan("metered_small", "0.00");
        small.setBillingTiming(com.tansoflow.tansocore.model.plan.BillingTiming.IN_ARREARS.name());
        Plan big = inAdvancePlan("metered_big", "499.00");
        big.setBillingTiming(com.tansoflow.tansocore.model.plan.BillingTiming.IN_ARREARS.name());
        Subscription existing = subscriptionOnPlanForUpgrade(small, currentSubscriptionId, accountIdString);
        when(planService.retrievePlan(account, UUID.fromString(big.getId().toString()))).thenReturn(big);

        com.tansoflow.tansocore.entity.AccountSetting setting = new com.tansoflow.tansocore.entity.AccountSetting();
        setting.setStripeMode(com.tansoflow.tansocore.model.api.external.StripeMode.PAYMENT_PASS_THROUGH);
        when(accountService.retrieveAccountSettings(accountIdString)).thenReturn(setting);

        callingWithAnApiKey(() -> {
            com.tansoflow.tansocore.model.subscription.UpgradeResult pending = subscriptionService.upgradeSubscription(
                    currentSubscriptionId, accountIdString, big.getId().toString(), true);
            org.assertj.core.api.Assertions.assertThat(pending.waitingOnPayment()).isFalse();
        });

        verify(keyBudgetService, org.mockito.Mockito.never()).assertWithinBudget(any(), any(), any());
        org.assertj.core.api.Assertions.assertThat(existing.getPlan()).isEqualTo(big);
    }

    @org.junit.jupiter.api.Test
    void aMixedBillingTimingChangeSaysSoInsteadOfDoingNothing() {
        String accountIdString = account.getId().toString();
        String currentSubscriptionId = UUID.randomUUID().toString();
        Plan advance = inAdvancePlan("starter", "149.00");
        Plan arrears = inAdvancePlan("metered", "0.00");
        arrears.setBillingTiming(com.tansoflow.tansocore.model.plan.BillingTiming.IN_ARREARS.name());
        subscriptionOnPlanForUpgrade(advance, currentSubscriptionId, accountIdString);
        when(planService.retrievePlan(account, UUID.fromString(arrears.getId().toString()))).thenReturn(arrears);

        com.tansoflow.tansocore.entity.AccountSetting setting = new com.tansoflow.tansocore.entity.AccountSetting();
        setting.setStripeMode(com.tansoflow.tansocore.model.api.external.StripeMode.PAYMENT_PASS_THROUGH);
        when(accountService.retrieveAccountSettings(accountIdString)).thenReturn(setting);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> subscriptionService.upgradeSubscription(
                        currentSubscriptionId, accountIdString, arrears.getId().toString(), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot change from an");
    }

    @org.junit.jupiter.api.Test
    void upgradeFromTheConsoleStillSwapsThePlanImmediately() {
        String accountIdString = account.getId().toString();
        String currentSubscriptionId = UUID.randomUUID().toString();
        Plan free = inAdvancePlan("developer_demo", "0.00");
        Plan starter = inAdvancePlan("starter", "149.00");
        Subscription existing = subscriptionOnPlanForUpgrade(free, currentSubscriptionId, accountIdString);
        when(planService.retrievePlan(account, UUID.fromString(starter.getId().toString()))).thenReturn(starter);

        com.tansoflow.tansocore.entity.AccountSetting setting = new com.tansoflow.tansocore.entity.AccountSetting();
        setting.setStripeMode(com.tansoflow.tansocore.model.api.external.StripeMode.PAYMENT_PASS_THROUGH);
        when(accountService.retrieveAccountSettings(accountIdString)).thenReturn(setting);
        when(invoiceService.createAdjustmentInvoice(eq(free), eq(starter), eq(existing), any(), any()))
                .thenReturn(new com.tansoflow.tansocore.entity.Invoice());

        // No security context: an operator acting in the console, not an agent holding a key.
        com.tansoflow.tansocore.model.subscription.UpgradeResult pending = subscriptionService.upgradeSubscription(
                currentSubscriptionId, accountIdString, starter.getId().toString(), true);

        org.assertj.core.api.Assertions.assertThat(pending.waitingOnPayment()).isFalse();
        org.assertj.core.api.Assertions.assertThat(existing.getPlan()).isEqualTo(starter);
        verify(entitlementService).processEntitlementsForSubscription(existing);
    }

    // --- STRIPE_DRIVEN: Stripe charges before the plan moves -------------------------------------------------
    // PR #44 swapped the plan at once and let Stripe add a CREATE_PRORATIONS item billed at the next renewal, so an
    // agent used the paid tier for up to a period before anyone paid, and a failed renewal never took it back.

    private void stripeDriven(String accountIdString) {
        com.tansoflow.tansocore.entity.AccountSetting setting = new com.tansoflow.tansocore.entity.AccountSetting();
        setting.setStripeMode(com.tansoflow.tansocore.model.api.external.StripeMode.STRIPE_DRIVEN);
        when(accountService.retrieveAccountSettings(accountIdString)).thenReturn(setting);
    }

    @org.junit.jupiter.api.Test
    void anAgentUpgradeOnStripeDrivenDoesNotSwapUntilStripeHasThePayment() throws Exception {
        String accountIdString = account.getId().toString();
        String currentSubscriptionId = UUID.randomUUID().toString();
        Plan free = inAdvancePlan("developer_demo", "0.00");
        Plan starter = inAdvancePlan("starter", "149.00");
        Subscription existing = subscriptionOnPlanForUpgrade(free, currentSubscriptionId, accountIdString);
        when(planService.retrievePlan(account, UUID.fromString(starter.getId().toString()))).thenReturn(starter);
        stripeDriven(accountIdString);
        // No card, or a decline: Stripe keeps the old price, leaves a pending_update and an open invoice.
        when(stripeSyncService.chargeUpgradeBeforeApplying(eq(existing.getId()), eq(account.getId()), eq(starter.getId()), any()))
                .thenReturn(new com.tansoflow.tansocore.model.data.stripe.StripeUpgradeCharge(
                        "in_proration", "https://invoice.stripe.com/i/acct_test/in_proration",
                        java.math.BigDecimal.ZERO, false));

        callingWithAnApiKey(() -> {
            com.tansoflow.tansocore.model.subscription.UpgradeResult result = subscriptionService.upgradeSubscription(
                    currentSubscriptionId, accountIdString, starter.getId().toString(), true);
            org.assertj.core.api.Assertions.assertThat(result.waitingOnPayment()).isTrue();
            org.assertj.core.api.Assertions.assertThat(result.stripePaymentUrl())
                    .isEqualTo("https://invoice.stripe.com/i/acct_test/in_proration");
        });

        org.assertj.core.api.Assertions.assertThat(existing.getPlan()).isEqualTo(free);
        verifyNoInteractions(entitlementService);
        verify(keyBudgetService, org.mockito.Mockito.never()).recordSpend(any(), any(), any(), any(), any(), any(), any());
        verify(invoiceService, org.mockito.Mockito.never()).createAdjustmentInvoice(any(), any(), any(), any(), any());
        // The price change went to Stripe as a charge-first call, not as the prorate-at-renewal event.
        verify(eventPublisher, org.mockito.Mockito.never()).publishEvent(
                org.mockito.ArgumentMatchers.isA(com.tansoflow.tansocore.model.event.service.SubscriptionPlanChangedEvent.class));

        // Saved once PENDING before Stripe is called, and again with what Stripe answered.
        ArgumentCaptor<com.tansoflow.tansocore.entity.SubscriptionScheduledChange> saved =
                ArgumentCaptor.forClass(com.tansoflow.tansocore.entity.SubscriptionScheduledChange.class);
        verify(subscriptionScheduledChangeRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        com.tansoflow.tansocore.entity.SubscriptionScheduledChange change = saved.getValue();
        org.assertj.core.api.Assertions.assertThat(change.getStatus())
                .isEqualTo(com.tansoflow.tansocore.model.subscription.type.SubscriptionScheduledChangeStatus.PENDING.name());
        org.assertj.core.api.Assertions.assertThat(change.getToPlan()).isEqualTo(starter);
        org.assertj.core.api.Assertions.assertThat(change.getStripeInvoiceId()).isEqualTo("in_proration");
        org.assertj.core.api.Assertions.assertThat(change.getPaymentUrl())
                .isEqualTo("https://invoice.stripe.com/i/acct_test/in_proration");
        org.assertj.core.api.Assertions.assertThat(change.getApiKeyId()).isNotNull();
    }

    @org.junit.jupiter.api.Test
    void anAgentUpgradeOnStripeDrivenSwapsOnceStripeHasChargedTheSavedCard() throws Exception {
        String accountIdString = account.getId().toString();
        String currentSubscriptionId = UUID.randomUUID().toString();
        Plan free = inAdvancePlan("developer_demo", "0.00");
        Plan starter = inAdvancePlan("starter", "149.00");
        Subscription existing = subscriptionOnPlanForUpgrade(free, currentSubscriptionId, accountIdString);
        when(planService.retrievePlan(account, UUID.fromString(starter.getId().toString()))).thenReturn(starter);
        stripeDriven(accountIdString);
        when(stripeSyncService.chargeUpgradeBeforeApplying(eq(existing.getId()), eq(account.getId()), eq(starter.getId()), any()))
                .thenReturn(new com.tansoflow.tansocore.model.data.stripe.StripeUpgradeCharge(
                        "in_proration", "https://invoice.stripe.com/i/acct_test/in_proration",
                        new java.math.BigDecimal("74.50"), true));

        callingWithAnApiKey(() -> {
            com.tansoflow.tansocore.model.subscription.UpgradeResult result = subscriptionService.upgradeSubscription(
                    currentSubscriptionId, accountIdString, starter.getId().toString(), true);
            org.assertj.core.api.Assertions.assertThat(result.waitingOnPayment()).isFalse();
        });

        org.assertj.core.api.Assertions.assertThat(existing.getPlan()).isEqualTo(starter);
        verify(entitlementService).processEntitlementsForSubscription(existing);
        verify(creditService).grantUpgradeDelta(eq(existing), eq(free), eq(starter), any());
        // The budget is drawn down by what Stripe actually collected.
        verify(keyBudgetService).recordSpend(eq(account.getId()), org.mockito.ArgumentMatchers.notNull(),
                eq(com.tansoflow.tansocore.model.apikey.type.SpendKind.MONEY),
                eq(com.tansoflow.tansocore.model.apikey.type.SpendChannel.OFF_SESSION), eq(new java.math.BigDecimal("74.50")),
                eq(existing.getId().toString()), org.mockito.ArgumentMatchers.startsWith("plan_change:"));

        ArgumentCaptor<com.tansoflow.tansocore.entity.SubscriptionScheduledChange> saved =
                ArgumentCaptor.forClass(com.tansoflow.tansocore.entity.SubscriptionScheduledChange.class);
        verify(subscriptionScheduledChangeRepository, org.mockito.Mockito.times(3)).save(saved.capture());
        org.assertj.core.api.Assertions.assertThat(saved.getValue().getStatus())
                .isEqualTo(com.tansoflow.tansocore.model.subscription.type.SubscriptionScheduledChangeStatus.COMPLETED.name());
    }

    @org.junit.jupiter.api.Test
    void askingForTheSameStripeDrivenUpgradeAgainReturnsTheSameInvoiceWithoutCallingStripe() {
        String accountIdString = account.getId().toString();
        String currentSubscriptionId = UUID.randomUUID().toString();
        Plan free = inAdvancePlan("developer_demo", "0.00");
        Plan starter = inAdvancePlan("starter", "149.00");
        Subscription existing = subscriptionOnPlanForUpgrade(free, currentSubscriptionId, accountIdString);
        when(planService.retrievePlan(account, UUID.fromString(starter.getId().toString()))).thenReturn(starter);
        stripeDriven(accountIdString);

        com.tansoflow.tansocore.entity.SubscriptionScheduledChange waiting =
                new com.tansoflow.tansocore.entity.SubscriptionScheduledChange();
        waiting.setSubscription(existing);
        waiting.setToPlan(starter);
        waiting.setStripeInvoiceId("in_proration");
        waiting.setPaymentUrl("https://invoice.stripe.com/i/acct_test/in_proration");
        when(subscriptionScheduledChangeRepository.findPendingUpgradeBySubscription(existing))
                .thenReturn(java.util.Optional.of(waiting));

        callingWithAnApiKey(() -> {
            com.tansoflow.tansocore.model.subscription.UpgradeResult result = subscriptionService.upgradeSubscription(
                    currentSubscriptionId, accountIdString, starter.getId().toString(), true);
            org.assertj.core.api.Assertions.assertThat(result.stripePaymentUrl())
                    .isEqualTo("https://invoice.stripe.com/i/acct_test/in_proration");
        });

        verifyNoInteractions(stripeSyncService);
        verify(subscriptionScheduledChangeRepository, org.mockito.Mockito.never()).save(any());
    }

    // Voiding the invoice is how Stripe drops a pending_update. Left open, a human paying it would move Stripe to
    // a plan Tanso no longer has a change for.
    @org.junit.jupiter.api.Test
    void cancellingAStripeDrivenPendingUpgradeVoidsItsStripeInvoice() throws Exception {
        String currentSubscriptionId = UUID.randomUUID().toString();
        Plan free = inAdvancePlan("developer_demo", "0.00");
        Plan starter = inAdvancePlan("starter", "149.00");
        Subscription existing = subscriptionOnPlanForUpgrade(free, currentSubscriptionId, account.getId().toString());

        com.tansoflow.tansocore.entity.SubscriptionScheduledChange waiting =
                new com.tansoflow.tansocore.entity.SubscriptionScheduledChange();
        waiting.setSubscription(existing);
        waiting.setToPlan(starter);
        waiting.setStripeInvoiceId("in_stale");
        when(subscriptionScheduledChangeRepository.findPendingUpgradeBySubscription(existing))
                .thenReturn(java.util.Optional.of(waiting));

        subscriptionService.cancelScheduledChangesForSubscription(
                UUID.fromString(currentSubscriptionId), account.getId());

        verify(stripeSyncService).cancelUnpaidUpgrade("in_stale", existing.getId(), account.getId());
        verify(subscriptionScheduledChangeRepository).cancelAllScheduledChanges(existing);
    }

    @org.junit.jupiter.api.Test
    void anOperatorUpgradeOnStripeDrivenStillSwapsAtOnceAndPricesStripeOnTheNewPlan() throws Exception {
        String accountIdString = account.getId().toString();
        String currentSubscriptionId = UUID.randomUUID().toString();
        Plan free = inAdvancePlan("developer_demo", "0.00");
        Plan starter = inAdvancePlan("starter", "149.00");
        Subscription existing = subscriptionOnPlanForUpgrade(free, currentSubscriptionId, accountIdString);
        when(planService.retrievePlan(account, UUID.fromString(starter.getId().toString()))).thenReturn(starter);
        stripeDriven(accountIdString);

        // No security context: an operator in the console.
        com.tansoflow.tansocore.model.subscription.UpgradeResult result = subscriptionService.upgradeSubscription(
                currentSubscriptionId, accountIdString, starter.getId().toString(), true);

        org.assertj.core.api.Assertions.assertThat(result.waitingOnPayment()).isFalse();
        org.assertj.core.api.Assertions.assertThat(existing.getPlan()).isEqualTo(starter);
        verify(entitlementService).processEntitlementsForSubscription(existing);
        verify(stripeSyncService, org.mockito.Mockito.never()).chargeUpgradeBeforeApplying(any(), any(), any(), any());
        verify(invoiceService, org.mockito.Mockito.never()).createAdjustmentInvoice(any(), any(), any(), any(), any());
        verify(eventPublisher).publishEvent(new com.tansoflow.tansocore.model.event.service.SubscriptionPlanChangedEvent(
                account.getId(), existing.getId(), starter.getId(), true));
    }

    // --- STRIPE_INTEGRATION: every caller is charged the proration before the plan moves ---------------------
    // The upgrade used to record a PENDING change and send Stripe a CREATE_PRORATIONS price change, so the call
    // answered 200 while the plan only moved when the NEXT renewal invoice was paid.

    private void stripeIntegration(String accountIdString) {
        com.tansoflow.tansocore.entity.AccountSetting setting = new com.tansoflow.tansocore.entity.AccountSetting();
        setting.setStripeMode(com.tansoflow.tansocore.model.api.external.StripeMode.STRIPE_INTEGRATION);
        when(accountService.retrieveAccountSettings(accountIdString)).thenReturn(setting);
    }

    @org.junit.jupiter.api.Test
    void anOperatorUpgradeOnStripeIntegrationIsChargedFirstAndWaitsOnTheInvoice() throws Exception {
        String accountIdString = account.getId().toString();
        String currentSubscriptionId = UUID.randomUUID().toString();
        Plan free = inAdvancePlan("developer_demo", "0.00");
        Plan starter = inAdvancePlan("starter", "149.00");
        Subscription existing = subscriptionOnPlanForUpgrade(free, currentSubscriptionId, accountIdString);
        when(planService.retrievePlan(account, UUID.fromString(starter.getId().toString()))).thenReturn(starter);
        stripeIntegration(accountIdString);
        when(stripeSyncService.chargeUpgradeBeforeApplying(eq(existing.getId()), eq(account.getId()), eq(starter.getId()), any()))
                .thenReturn(new com.tansoflow.tansocore.model.data.stripe.StripeUpgradeCharge(
                        "in_proration", "https://invoice.stripe.com/i/acct_test/in_proration",
                        java.math.BigDecimal.ZERO, false));

        // No security context: an operator.
        com.tansoflow.tansocore.model.subscription.UpgradeResult result = subscriptionService.upgradeSubscription(
                currentSubscriptionId, accountIdString, starter.getId().toString(), true);

        org.assertj.core.api.Assertions.assertThat(result.stripePaymentUrl())
                .isEqualTo("https://invoice.stripe.com/i/acct_test/in_proration");
        org.assertj.core.api.Assertions.assertThat(existing.getPlan()).isEqualTo(free);
        verifyNoInteractions(entitlementService);
        // No prorate-at-renewal price change: Stripe got the charge-first call instead.
        verify(eventPublisher, org.mockito.Mockito.never()).publishEvent(
                org.mockito.ArgumentMatchers.isA(com.tansoflow.tansocore.model.event.service.SubscriptionPlanChangedEvent.class));

        ArgumentCaptor<com.tansoflow.tansocore.entity.SubscriptionScheduledChange> saved =
                ArgumentCaptor.forClass(com.tansoflow.tansocore.entity.SubscriptionScheduledChange.class);
        verify(subscriptionScheduledChangeRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        org.assertj.core.api.Assertions.assertThat(saved.getValue().getStatus())
                .isEqualTo(com.tansoflow.tansocore.model.subscription.type.SubscriptionScheduledChangeStatus.PENDING.name());
        org.assertj.core.api.Assertions.assertThat(saved.getValue().getStripeInvoiceId()).isEqualTo("in_proration");
        org.assertj.core.api.Assertions.assertThat(saved.getValue().getApiKeyId()).isNull();
    }

    @org.junit.jupiter.api.Test
    void anAgentUpgradeOnStripeIntegrationSwapsOnceStripeHasChargedTheSavedCard() throws Exception {
        String accountIdString = account.getId().toString();
        String currentSubscriptionId = UUID.randomUUID().toString();
        Plan free = inAdvancePlan("developer_demo", "0.00");
        Plan starter = inAdvancePlan("starter", "149.00");
        Subscription existing = subscriptionOnPlanForUpgrade(free, currentSubscriptionId, accountIdString);
        when(planService.retrievePlan(account, UUID.fromString(starter.getId().toString()))).thenReturn(starter);
        stripeIntegration(accountIdString);
        when(stripeSyncService.chargeUpgradeBeforeApplying(eq(existing.getId()), eq(account.getId()), eq(starter.getId()), any()))
                .thenReturn(new com.tansoflow.tansocore.model.data.stripe.StripeUpgradeCharge(
                        "in_proration", "https://invoice.stripe.com/i/acct_test/in_proration",
                        new java.math.BigDecimal("74.50"), true));

        callingWithAnApiKey(() -> {
            com.tansoflow.tansocore.model.subscription.UpgradeResult result = subscriptionService.upgradeSubscription(
                    currentSubscriptionId, accountIdString, starter.getId().toString(), true);
            org.assertj.core.api.Assertions.assertThat(result.waitingOnPayment()).isFalse();
        });

        org.assertj.core.api.Assertions.assertThat(existing.getPlan()).isEqualTo(starter);
        verify(entitlementService).processEntitlementsForSubscription(existing);
        verify(keyBudgetService).recordSpend(eq(account.getId()), org.mockito.ArgumentMatchers.notNull(),
                eq(com.tansoflow.tansocore.model.apikey.type.SpendKind.MONEY),
                eq(com.tansoflow.tansocore.model.apikey.type.SpendChannel.OFF_SESSION), eq(new java.math.BigDecimal("74.50")),
                eq(existing.getId().toString()), org.mockito.ArgumentMatchers.startsWith("plan_change:"));
    }

    @org.junit.jupiter.api.Test
    void anOperatorUpgradeOnStripeIntegrationSwapsWhenStripeChargedTheCard() throws Exception {
        String accountIdString = account.getId().toString();
        String currentSubscriptionId = UUID.randomUUID().toString();
        Plan free = inAdvancePlan("developer_demo", "0.00");
        Plan starter = inAdvancePlan("starter", "149.00");
        Subscription existing = subscriptionOnPlanForUpgrade(free, currentSubscriptionId, accountIdString);
        when(planService.retrievePlan(account, UUID.fromString(starter.getId().toString()))).thenReturn(starter);
        stripeIntegration(accountIdString);
        when(stripeSyncService.chargeUpgradeBeforeApplying(eq(existing.getId()), eq(account.getId()), eq(starter.getId()), any()))
                .thenReturn(new com.tansoflow.tansocore.model.data.stripe.StripeUpgradeCharge(
                        "in_proration", null, new java.math.BigDecimal("74.50"), true));

        com.tansoflow.tansocore.model.subscription.UpgradeResult result = subscriptionService.upgradeSubscription(
                currentSubscriptionId, accountIdString, starter.getId().toString(), true);

        org.assertj.core.api.Assertions.assertThat(result.waitingOnPayment()).isFalse();
        org.assertj.core.api.Assertions.assertThat(existing.getPlan()).isEqualTo(starter);
        verify(creditService).grantUpgradeDelta(eq(existing), eq(free), eq(starter), any());
    }

    // Nothing is charged now for an in-arrears upgrade, so it keeps waiting on the next paid invoice. The listener
    // used to read subscription.getPlan(), so Stripe was re-sent the old price and never billed the upgrade.
    @org.junit.jupiter.api.Test
    void anInArrearsStripeIntegrationUpgradeSendsStripeTheNewPlanNotTheUnswappedOne() {
        String accountIdString = account.getId().toString();
        String currentSubscriptionId = UUID.randomUUID().toString();
        Plan small = inAdvancePlan("metered_small", "10.00");
        small.setBillingTiming(com.tansoflow.tansocore.model.plan.BillingTiming.IN_ARREARS.name());
        Plan large = inAdvancePlan("metered_large", "50.00");
        large.setBillingTiming(com.tansoflow.tansocore.model.plan.BillingTiming.IN_ARREARS.name());
        Subscription existing = subscriptionOnPlanForUpgrade(small, currentSubscriptionId, accountIdString);
        when(planService.retrievePlan(account, UUID.fromString(large.getId().toString()))).thenReturn(large);
        stripeIntegration(accountIdString);

        subscriptionService.upgradeSubscription(currentSubscriptionId, accountIdString, large.getId().toString(), true);

        org.assertj.core.api.Assertions.assertThat(existing.getPlan()).isEqualTo(small);
        verifyNoInteractions(stripeSyncService);
        verify(eventPublisher).publishEvent(new com.tansoflow.tansocore.model.event.service.SubscriptionPlanChangedEvent(
                account.getId(), existing.getId(), large.getId(), true));
    }

    @org.junit.jupiter.api.Test
    void fulfillingAnUpgradeThatIsNoLongerPendingIsRefused() {
        com.tansoflow.tansocore.entity.SubscriptionScheduledChange done =
                new com.tansoflow.tansocore.entity.SubscriptionScheduledChange();
        done.setStatus(com.tansoflow.tansocore.model.subscription.type.SubscriptionScheduledChangeStatus.COMPLETED.name());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> subscriptionService.fulfilPaidUpgrade(done, new java.math.BigDecimal("74.50"),
                                com.tansoflow.tansocore.model.apikey.type.SpendChannel.OFF_SESSION))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(keyBudgetService, entitlementService);
    }

    // --- charge-first upgrades: no money moves inside a transaction ------------------------------------------
    // upgradeSubscription was @Transactional and called Stripe inside it. A failed commit after Stripe charged left
    // the customer charged with no pending change in Tanso, so invoice.paid had nothing to complete.

    /** The service as Spring wires it: @Transactional methods open a transaction, calls inside the class do not. */
    private com.tansoflow.tansocore.service.internal.monetization.SubscriptionService transactionalProxy() {
        org.springframework.aop.framework.ProxyFactory factory = new org.springframework.aop.framework.ProxyFactory(subscriptionService);
        factory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(
                (org.springframework.transaction.TransactionManager) transactionManager,
                new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        return (com.tansoflow.tansocore.service.internal.monetization.SubscriptionService) factory.getProxy();
    }

    @org.junit.jupiter.api.Test
    void stripeIsChargedWithNoTransactionOpenAndOnlyAfterThePendingChangeIsCommitted() throws Exception {
        String accountIdString = account.getId().toString();
        String currentSubscriptionId = UUID.randomUUID().toString();
        Plan free = inAdvancePlan("developer_demo", "0.00");
        Plan starter = inAdvancePlan("starter", "149.00");
        Subscription existing = subscriptionOnPlanForUpgrade(free, currentSubscriptionId, accountIdString);
        when(planService.retrievePlan(account, UUID.fromString(starter.getId().toString()))).thenReturn(starter);
        stripeDriven(accountIdString);
        when(stripeSyncService.chargeUpgradeBeforeApplying(eq(existing.getId()), eq(account.getId()), eq(starter.getId()), any()))
                .thenAnswer(i -> {
                    org.assertj.core.api.Assertions.assertThat(
                                    org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
                            .as("Stripe charged inside a transaction").isFalse();
                    // The PENDING change is already committed, and Stripe is keyed on its id.
                    org.assertj.core.api.Assertions.assertThat(transactionManager.commits()).isEqualTo(1);
                    org.assertj.core.api.Assertions.assertThat(savedChanges).singleElement().satisfies(change -> {
                        org.assertj.core.api.Assertions.assertThat(change.getStatus()).isEqualTo("PENDING");
                        org.assertj.core.api.Assertions.assertThat(change.isStripeChargeFirst()).isTrue();
                        org.assertj.core.api.Assertions.assertThat(change.getApiKeyId()).isNotNull();
                        org.assertj.core.api.Assertions.assertThat(change.getId()).isEqualTo(i.getArgument(3));
                    });
                    return new com.tansoflow.tansocore.model.data.stripe.StripeUpgradeCharge(
                            "in_proration", null, new java.math.BigDecimal("74.50"), true);
                });
        // The plan moves in a transaction of its own, after Stripe.
        org.mockito.Mockito.doAnswer(i -> {
            org.assertj.core.api.Assertions.assertThat(
                    org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return null;
        }).when(entitlementService).processEntitlementsForSubscription(existing);

        callingWithAnApiKey(() -> {
            com.tansoflow.tansocore.model.subscription.UpgradeResult result = transactionalProxy().upgradeSubscription(
                    currentSubscriptionId, accountIdString, starter.getId().toString(), true);
            org.assertj.core.api.Assertions.assertThat(result.waitingOnPayment()).isFalse();
        });

        org.assertj.core.api.Assertions.assertThat(existing.getPlan()).isEqualTo(starter);
        org.assertj.core.api.Assertions.assertThat(savedChanges.getFirst().getStatus()).isEqualTo("COMPLETED");
        org.assertj.core.api.Assertions.assertThat(savedChanges.getFirst().getStripeInvoiceId()).isEqualTo("in_proration");
        org.assertj.core.api.Assertions.assertThat(transactionManager.commits()).isEqualTo(2);
    }

    // Stripe took the money, then recording that failed. The committed PENDING change is what invoice.paid (see
    // StripeWebhookImplTest) or a retry completes; the retry asks Stripe again under the same idempotency key.
    @org.junit.jupiter.api.Test
    void whenRecordingStripesAnswerFailsTheChangeStaysPendingAndARetryReusesItsIdempotencyKey() throws Exception {
        String accountIdString = account.getId().toString();
        String currentSubscriptionId = UUID.randomUUID().toString();
        Plan free = inAdvancePlan("developer_demo", "0.00");
        Plan starter = inAdvancePlan("starter", "149.00");
        Subscription existing = subscriptionOnPlanForUpgrade(free, currentSubscriptionId, accountIdString);
        when(planService.retrievePlan(account, UUID.fromString(starter.getId().toString()))).thenReturn(starter);
        stripeIntegration(accountIdString);
        when(stripeSyncService.chargeUpgradeBeforeApplying(eq(existing.getId()), eq(account.getId()), eq(starter.getId()), any()))
                .thenReturn(new com.tansoflow.tansocore.model.data.stripe.StripeUpgradeCharge(
                        "in_proration", null, new java.math.BigDecimal("74.50"), true));
        when(subscriptionScheduledChangeRepository.findPendingUpgradeByIdForUpdate(any()))
                .thenThrow(new org.springframework.dao.CannotAcquireLockException("lock timeout"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> transactionalProxy().upgradeSubscription(
                        currentSubscriptionId, accountIdString, starter.getId().toString(), true))
                .isInstanceOf(org.springframework.dao.CannotAcquireLockException.class);

        SubscriptionScheduledChange pending = savedChanges.getFirst();
        org.assertj.core.api.Assertions.assertThat(savedChanges).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(pending.getStatus()).isEqualTo("PENDING");
        org.assertj.core.api.Assertions.assertThat(pending.isStripeChargeFirst()).isTrue();
        org.assertj.core.api.Assertions.assertThat(pending.getStripeInvoiceId()).isNull();
        org.assertj.core.api.Assertions.assertThat(existing.getPlan()).isEqualTo(free);
        // Phase one committed; phase three rolled back.
        org.assertj.core.api.Assertions.assertThat(transactionManager.commits()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(transactionManager.rollbacks()).isEqualTo(1);

        // The caller retries. The pending change is found, Stripe is asked again for the same change, and this time
        // the answer is recorded.
        org.mockito.Mockito.reset(subscriptionScheduledChangeRepository);
        when(subscriptionScheduledChangeRepository.findPendingUpgradeBySubscription(existing)).thenReturn(Optional.of(pending));
        when(subscriptionScheduledChangeRepository.findPendingUpgradeByIdForUpdate(pending.getId())).thenReturn(Optional.of(pending));

        com.tansoflow.tansocore.model.subscription.UpgradeResult retried = transactionalProxy().upgradeSubscription(
                currentSubscriptionId, accountIdString, starter.getId().toString(), true);

        org.assertj.core.api.Assertions.assertThat(retried.waitingOnPayment()).isFalse();
        ArgumentCaptor<UUID> keyedOn = ArgumentCaptor.forClass(UUID.class);
        verify(stripeSyncService, org.mockito.Mockito.times(2))
                .chargeUpgradeBeforeApplying(any(), any(), any(), keyedOn.capture());
        org.assertj.core.api.Assertions.assertThat(keyedOn.getAllValues()).containsOnly(pending.getId());
        org.assertj.core.api.Assertions.assertThat(pending.getStatus()).isEqualTo("COMPLETED");
        org.assertj.core.api.Assertions.assertThat(existing.getPlan()).isEqualTo(starter);
        verify(subscriptionScheduledChangeRepository, never()).cancelAllScheduledChanges(any());
    }

    // Stripe refused the change. Left PENDING, the next attempt would re-send the refused request under the same key
    // and get the same refusal back for a day.
    @org.junit.jupiter.api.Test
    void aChargeStripeRefusesMarksTheChangeFailedSoTheNextAttemptStartsAfresh() throws Exception {
        String accountIdString = account.getId().toString();
        String currentSubscriptionId = UUID.randomUUID().toString();
        Plan free = inAdvancePlan("developer_demo", "0.00");
        Plan starter = inAdvancePlan("starter", "149.00");
        Subscription existing = subscriptionOnPlanForUpgrade(free, currentSubscriptionId, accountIdString);
        when(planService.retrievePlan(account, UUID.fromString(starter.getId().toString()))).thenReturn(starter);
        stripeIntegration(accountIdString);
        when(stripeSyncService.chargeUpgradeBeforeApplying(eq(existing.getId()), eq(account.getId()), eq(starter.getId()), any()))
                .thenThrow(new com.stripe.exception.InvalidRequestException(
                        "No such price", "items", "req_1", "resource_missing", 400, null));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> subscriptionService.upgradeSubscription(
                        currentSubscriptionId, accountIdString, starter.getId().toString(), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No such price");

        org.assertj.core.api.Assertions.assertThat(savedChanges).singleElement()
                .satisfies(change -> org.assertj.core.api.Assertions.assertThat(change.getStatus()).isEqualTo("FAILED"));
        org.assertj.core.api.Assertions.assertThat(existing.getPlan()).isEqualTo(free);
        verifyNoInteractions(entitlementService);
    }

    // No answer at all: Stripe may have charged. The change stays PENDING so invoice.paid can still complete it and a
    // retry gets Stripe's first answer under the same key.
    @org.junit.jupiter.api.Test
    void noAnswerFromStripeLeavesTheChangePending() throws Exception {
        String accountIdString = account.getId().toString();
        String currentSubscriptionId = UUID.randomUUID().toString();
        Plan free = inAdvancePlan("developer_demo", "0.00");
        Plan starter = inAdvancePlan("starter", "149.00");
        Subscription existing = subscriptionOnPlanForUpgrade(free, currentSubscriptionId, accountIdString);
        when(planService.retrievePlan(account, UUID.fromString(starter.getId().toString()))).thenReturn(starter);
        stripeIntegration(accountIdString);
        when(stripeSyncService.chargeUpgradeBeforeApplying(eq(existing.getId()), eq(account.getId()), eq(starter.getId()), any()))
                .thenThrow(new com.stripe.exception.ApiConnectionException("read timed out"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> subscriptionService.upgradeSubscription(
                        currentSubscriptionId, accountIdString, starter.getId().toString(), true))
                .isInstanceOf(IllegalStateException.class);

        org.assertj.core.api.Assertions.assertThat(savedChanges).singleElement()
                .satisfies(change -> org.assertj.core.api.Assertions.assertThat(change.getStatus()).isEqualTo("PENDING"));
    }

    private com.tansoflow.tansocore.entity.Invoice paidInvoiceOfType(InvoiceType type, Instant periodStart, Instant periodEnd) {
        com.tansoflow.tansocore.entity.Invoice invoice = new com.tansoflow.tansocore.entity.Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setSubscription(subscription);
        invoice.setType(type.name());
        invoice.setInvoicePeriodStart(periodStart);
        invoice.setInvoicePeriodEnd(periodEnd);
        when(invoiceService.retrieveInvoiceByInvoiceIdAndAccount(invoice.getId().toString(), account.getId().toString()))
                .thenReturn(invoice);
        return invoice;
    }

    // An operator marking an upgrade's adjustment invoice paid used to move the billing period to the upgrade
    // moment. That broke the cycle and gave the period credit grant a new idempotency key, so the full new-plan
    // allocation was granted on top of the upgrade delta.
    @Test
    void markingAnAdjustmentInvoicePaidLeavesTheBillingPeriodAlone() {
        plan.setBillingTiming(BillingTiming.IN_ADVANCE.name());
        Instant periodStart = subscription.getCurrentPeriodStart();
        Instant periodEnd = subscription.getCurrentPeriodEnd();
        Instant upgradedAt = periodStart.plus(10, ChronoUnit.DAYS);
        com.tansoflow.tansocore.entity.Invoice adjustment = paidInvoiceOfType(InvoiceType.ADJUSTMENT, upgradedAt, periodEnd);

        subscriptionService.subscriptionInvoicePaid(adjustment.getId().toString(), account.getId().toString());

        assertEquals(periodStart, subscription.getCurrentPeriodStart());
        assertEquals(periodEnd, subscription.getCurrentPeriodEnd());
        verify(invoiceService).markInvoiceAsPaid(adjustment);
    }

    @Test
    void markingARegularInAdvanceInvoicePaidStillMovesTheBillingPeriod() {
        plan.setBillingTiming(BillingTiming.IN_ADVANCE.name());
        Instant nextStart = subscription.getCurrentPeriodEnd();
        Instant nextEnd = nextStart.plus(30, ChronoUnit.DAYS);
        com.tansoflow.tansocore.entity.Invoice regular = paidInvoiceOfType(InvoiceType.REGULAR, nextStart, nextEnd);

        subscriptionService.subscriptionInvoicePaid(regular.getId().toString(), account.getId().toString());

        assertEquals(nextStart, subscription.getCurrentPeriodStart());
        assertEquals(nextEnd, subscription.getCurrentPeriodEnd());
    }

    // --- saved-card subscribe: no money moves inside a transaction --------------------------------------------
    // subscribe was @Transactional and created (and charged) the Stripe subscription inside it. A commit that failed
    // after Stripe charged left a paid Stripe subscription, no spend record, and no key for a retry to reuse.

    private final List<com.tansoflow.tansocore.entity.CheckoutSession> savedCharges = new java.util.ArrayList<>();

    private Plan savedCardSubscribeOnStripeIntegration() {
        customer.setStripeDefaultPaymentMethodId("pm_saved");
        Plan paid = inAdvancePlan("starter", "49.00");
        paid.setIntervalMonths(1);
        stripeIntegration(account.getId().toString());
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId())).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(checkoutSessionRepository.save(any())).thenAnswer(i -> {
            com.tansoflow.tansocore.entity.CheckoutSession row = i.getArgument(0);
            if (row.getId() == null) {
                row.setId(UUID.randomUUID());
                savedCharges.add(row);
            }
            return row;
        });
        org.mockito.Mockito.lenient().when(checkoutSessionRepository.findByIdForUpdate(any()))
                .thenAnswer(i -> savedCharges.stream().filter(r -> r.getId().equals(i.getArgument(0))).findFirst());
        return paid;
    }

    private com.stripe.model.Subscription createdStripeSubscription() {
        com.stripe.model.Subscription stripeSub = new com.stripe.model.Subscription();
        stripeSub.setId("sub_direct_1");
        return stripeSub;
    }

    @org.junit.jupiter.api.Test
    void aSavedCardSubscribeChargesWithNoTransactionOpenAfterThePendingChargeIsCommitted() throws Exception {
        Plan paid = savedCardSubscribeOnStripeIntegration();
        com.tansoflow.tansocore.integration.stripe.StripeWebhook webhook =
                org.mockito.Mockito.mock(com.tansoflow.tansocore.integration.stripe.StripeWebhook.class);
        when(stripeWebhookProvider.getObject()).thenReturn(webhook);
        when(stripeSyncService.createDirectSubscription(eq(account.getId()), eq(customer.getId()), eq(paid.getId()),
                eq("pm_saved"), any())).thenAnswer(i -> {
            org.assertj.core.api.Assertions.assertThat(
                            org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
                    .as("Stripe charged inside a transaction").isFalse();
            // The pending charge is committed, and Stripe is keyed on its id.
            org.assertj.core.api.Assertions.assertThat(transactionManager.commits()).isEqualTo(1);
            org.assertj.core.api.Assertions.assertThat(savedCharges).singleElement().satisfies(row -> {
                org.assertj.core.api.Assertions.assertThat(row.getId()).isEqualTo(i.getArgument(4));
                org.assertj.core.api.Assertions.assertThat(row.getStatus()).isEqualTo("PENDING");
                org.assertj.core.api.Assertions.assertThat(row.getPurpose()).isEqualTo("DIRECT_SUBSCRIPTION");
                org.assertj.core.api.Assertions.assertThat(row.getAmount()).isEqualByComparingTo("49.00");
            });
            return createdStripeSubscription();
        });
        // Materializing happens in a transaction of its own, after Stripe.
        org.mockito.Mockito.doAnswer(i -> {
            org.assertj.core.api.Assertions.assertThat(
                    org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return null;
        }).when(webhook).materializeStripeSubscription(any(), eq(account.getId().toString()));

        transactionalProxy().subscribe(customer, paid, account.getId().toString(), null);

        verify(webhook).materializeStripeSubscription(any(), eq(account.getId().toString()));
        org.assertj.core.api.Assertions.assertThat(transactionManager.commits()).isEqualTo(2);
    }

    // Recording failed after Stripe charged. The pending row stays PENDING for customer.subscription.created (see
    // StripeWebhookImplTest), and a retry reuses it, so Stripe is asked under the same key.
    @org.junit.jupiter.api.Test
    void whenRecordingASavedCardSubscribeFailsARetryReusesThePendingChargeAndItsKey() throws Exception {
        Plan paid = savedCardSubscribeOnStripeIntegration();
        com.tansoflow.tansocore.integration.stripe.StripeWebhook webhook =
                org.mockito.Mockito.mock(com.tansoflow.tansocore.integration.stripe.StripeWebhook.class);
        when(stripeWebhookProvider.getObject()).thenReturn(webhook);
        when(stripeSyncService.createDirectSubscription(any(), any(), any(), any(), any())).thenReturn(createdStripeSubscription());
        org.mockito.Mockito.doThrow(new org.springframework.dao.CannotAcquireLockException("lock timeout"))
                .when(webhook).materializeStripeSubscription(any(), any());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> transactionalProxy().subscribe(
                        customer, paid, account.getId().toString(), null))
                .isInstanceOf(org.springframework.dao.CannotAcquireLockException.class);
        com.tansoflow.tansocore.entity.CheckoutSession pending = savedCharges.getFirst();
        org.assertj.core.api.Assertions.assertThat(pending.getStatus()).isEqualTo("PENDING");

        when(checkoutSessionRepository.findFirstByCustomerIdAndPlanIdAndPurposeAndStatusOrderByCreatedAtDesc(
                customer.getId(), paid.getId(), "DIRECT_SUBSCRIPTION", "PENDING")).thenReturn(Optional.of(pending));
        org.mockito.Mockito.doNothing().when(webhook).materializeStripeSubscription(any(), any());

        transactionalProxy().subscribe(customer, paid, account.getId().toString(), null);

        ArgumentCaptor<UUID> keyedOn = ArgumentCaptor.forClass(UUID.class);
        verify(stripeSyncService, org.mockito.Mockito.times(2))
                .createDirectSubscription(any(), any(), any(), any(), keyedOn.capture());
        org.assertj.core.api.Assertions.assertThat(keyedOn.getAllValues()).containsOnly(pending.getId());
        org.assertj.core.api.Assertions.assertThat(savedCharges).hasSize(1);
    }

    // Stripe refused the card. Left PENDING, the next attempt would reuse the key and get the refusal back for a day.
    @org.junit.jupiter.api.Test
    void aSavedCardSubscribeStripeRefusesMarksThePendingChargeFailed() throws Exception {
        Plan paid = savedCardSubscribeOnStripeIntegration();
        when(stripeSyncService.createDirectSubscription(any(), any(), any(), any(), any()))
                .thenThrow(new com.stripe.exception.CardException("Your card was declined.", "req_1", "card_declined",
                        null, "generic_decline", null, 402, null));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> subscriptionService.subscribe(
                        customer, paid, account.getId().toString(), null))
                .hasMessageContaining("Payment with the saved payment method failed");

        org.assertj.core.api.Assertions.assertThat(savedCharges).singleElement()
                .satisfies(row -> org.assertj.core.api.Assertions.assertThat(row.getStatus()).isEqualTo("FAILED"));
        verifyNoInteractions(stripeWebhookProvider);
    }

    // Two saved-card subscribes at once each found no pending charge, each opened one under its own idempotency key,
    // and Stripe created and charged two subscriptions. The customer row is now locked before the lookup, so the
    // second waits, finds the first one's pending charge, and asks Stripe under the same key.
    @org.junit.jupiter.api.Test
    void aConcurrentSavedCardSubscribeLocksTheCustomerThenReusesThePendingChargeAndItsKey() throws Exception {
        Plan paid = savedCardSubscribeOnStripeIntegration();
        com.tansoflow.tansocore.entity.CheckoutSession firstCallersCharge = new com.tansoflow.tansocore.entity.CheckoutSession();
        firstCallersCharge.setId(UUID.randomUUID());
        firstCallersCharge.setStatus("PENDING");
        when(checkoutSessionRepository.findFirstByCustomerIdAndPlanIdAndPurposeAndStatusOrderByCreatedAtDesc(
                customer.getId(), paid.getId(), "DIRECT_SUBSCRIPTION", "PENDING")).thenReturn(Optional.of(firstCallersCharge));
        when(stripeSyncService.createDirectSubscription(any(), any(), any(), any(), any()))
                .thenThrow(new com.stripe.exception.IdempotencyException(
                        "There is currently another in-progress request using this Stripe token", "req_2", null, 409));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> subscriptionService.subscribe(
                customer, paid, account.getId().toString(), null));

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(customerRepository, subscriptionRepository, checkoutSessionRepository);
        inOrder.verify(customerRepository).findByIdAndAccountIdForUpdate(customer.getId(), account.getId());
        inOrder.verify(subscriptionRepository).findSubscriptionsByCustomer_Id(customer.getId());
        inOrder.verify(checkoutSessionRepository).findFirstByCustomerIdAndPlanIdAndPurposeAndStatusOrderByCreatedAtDesc(
                customer.getId(), paid.getId(), "DIRECT_SUBSCRIPTION", "PENDING");
        verify(stripeSyncService).createDirectSubscription(account.getId(), customer.getId(), paid.getId(), "pm_saved",
                firstCallersCharge.getId());
        // No second pending charge, and the first caller's is left PENDING for the request Stripe is still running.
        org.assertj.core.api.Assertions.assertThat(savedCharges).isEmpty();
        org.assertj.core.api.Assertions.assertThat(firstCallersCharge.getStatus()).isEqualTo("PENDING");
    }

    // A caller that wraps subscribe in its own transaction would put the charge back inside it.
    @org.junit.jupiter.api.Test
    void aSavedCardSubscribeInsideTheCallersTransactionIsRefusedBeforeStripe() {
        Plan paid = savedCardSubscribeOnStripeIntegration();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                        subscriptionService.subscribe(customer, paid, account.getId().toString(), null)))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(stripeSyncService);
    }
}
