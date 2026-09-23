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
import com.tansoflow.tansocore.entity.AccountSetting;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.Event;
import com.tansoflow.tansocore.entity.Feature;
import com.tansoflow.tansocore.entity.Invoice;
import com.tansoflow.tansocore.entity.InvoiceItem;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.entity.PlanFeatureRule;
import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.model.billing.CreateInvoiceParams;
import com.tansoflow.tansocore.model.billing.type.InvoiceStatus;
import com.tansoflow.tansocore.model.billing.type.InvoiceType;
import com.tansoflow.tansocore.repository.AccountSettingRepository;
import com.tansoflow.tansocore.repository.EventRepository;
import com.tansoflow.tansocore.repository.InvoiceItemRepository;
import com.tansoflow.tansocore.repository.InvoiceRepository;
import com.tansoflow.tansocore.repository.PlanFeatureRuleRepository;
import com.tansoflow.tansocore.repository.SubscriptionRepository;
import com.tansoflow.tansocore.service.internal.monetization.EntitlementService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@org.springframework.test.context.event.RecordApplicationEvents
class InvoiceServiceImplTest {

    @Autowired
    private InvoiceServiceImpl invoiceService;

    @Autowired
    private org.springframework.test.context.event.ApplicationEvents applicationEvents;

    @MockitoBean
    private InvoiceRepository invoiceRepository;

    @MockitoBean
    private EntitlementService entitlementService;

    @MockitoBean
    private com.tansoflow.tansocore.service.internal.monetization.CreditService creditService;

    @MockitoBean
    private EventRepository eventRepository;

    @MockitoBean
    private PlanFeatureRuleRepository planFeatureRuleRepository;

    @MockitoBean
    private InvoiceItemRepository invoiceItemRepository;

    @MockitoBean
    private AccountSettingRepository accountSettingRepository;

    @MockitoBean
    private SubscriptionRepository subscriptionRepository;

    @MockitoBean
    private com.tansoflow.tansocore.repository.SubscriptionScheduledChangeRepository subscriptionScheduledChangeRepository;

    @MockitoBean
    private com.tansoflow.tansocore.service.internal.account.KeyBudgetService keyBudgetService;

    @Test
    void testCreateNewInvoice_CalculatesUsageCosts() {
        // Setup
        Account account = new Account();
        account.setId(UUID.randomUUID());

        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setAccount(account);

        Plan plan = new Plan();
        plan.setId(UUID.randomUUID());
        plan.setPriceAmount(BigDecimal.ZERO);

        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setCustomer(customer);
        subscription.setPlan(plan);
        subscription.setAccount(account);
        subscription.setIsActive(true);

        Feature feature = new Feature();
        feature.setId(UUID.randomUUID());
        feature.setKey("llm.generate");

        PlanFeatureRule rule = new PlanFeatureRule();
        rule.setFeature(feature);
        Map<String, Object> pricing = new HashMap<>();
        pricing.put("model", "usage");
        pricing.put("price_per_unit", 0.01);
        rule.setValue(pricing);

        Instant start = Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS);
        Instant end = Instant.now();

        Event event1 = new Event();
        event1.setEventName("llm.generate");
        event1.setUsageUnits(new BigDecimal("100"));
        event1.setRevenueAmount(new BigDecimal("1.00"));

        Event event2 = new Event();
        event2.setEventName("llm.generate");
        event2.setUsageUnits(new BigDecimal("50"));
        event2.setRevenueAmount(new BigDecimal("0.50"));

        when(eventRepository.findEventsForBillingBySubscription(eq(customer.getId()), eq(subscription.getId()), any(), eq(start), eq(end)))
                .thenReturn(List.of(event1, event2));
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId()))
                .thenReturn(List.of(subscription));
        when(eventRepository.findEventsForBillingUntagged(eq(customer.getId()), any(), eq(start), eq(end)))
                .thenReturn(List.of());
        when(planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(any())).thenReturn(List.of(rule));
        
        AccountSetting accountSetting = new AccountSetting();
        accountSetting.setStripeMode(com.tansoflow.tansocore.model.api.external.StripeMode.NONE);
        when(accountSettingRepository.findAccountSettingById(account.getId())).thenReturn(accountSetting);

        when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(i -> {
            Invoice inv = i.getArgument(0);
            inv.setId(UUID.randomUUID());
            return inv;
        });

        CreateInvoiceParams params = new CreateInvoiceParams(
                subscription,
                LocalDate.now(),
                InvoiceStatus.DUE,
                start,
                end,
                BigDecimal.ZERO,
                "USD",
                InvoiceType.REGULAR
        );

        // Execute
        Invoice result = invoiceService.createNewInvoice(params);

        // Verify
        // 150 total usage * 0.01 = 1.50
        assertEquals(new BigDecimal("1.50"), result.getAmount());
        verify(invoiceItemRepository, atLeastOnce()).saveAll(anyList());
        // Base price is ZERO, so no base price item should be saved
        verify(invoiceItemRepository, never()).save(any(InvoiceItem.class));
    }

    // An invoice Stripe computed is stored as Stripe computed it. createNewInvoice resets the amount to the plan price
    // plus Tanso's usage, which turned a $30.00 upgrade proration into the new plan's 60.00.
    @Test
    void createInvoiceFromStripe_KeepsStripesAmountAndLines() {
        Account account = new Account();
        account.setId(UUID.randomUUID());
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setAccount(account);
        Plan plan = new Plan();
        plan.setId(UUID.randomUUID());
        plan.setPriceAmount(new BigDecimal("60.00"));
        plan.setName("Pro");
        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setCustomer(customer);
        subscription.setPlan(plan);
        subscription.setAccount(account);
        Instant start = Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS);
        Instant end = Instant.now();
        when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(i -> {
            Invoice inv = i.getArgument(0);
            inv.setId(UUID.randomUUID());
            return inv;
        });

        invoiceService.createInvoiceFromStripe(subscription, LocalDate.now(), new BigDecimal("30.00"), InvoiceStatus.DUE,
                start, end, List.of(
                        new com.tansoflow.tansocore.service.internal.monetization.InvoiceService.SyncLineItem(new BigDecimal("-30.00"), "Unused time on Paid"),
                        new com.tansoflow.tansocore.service.internal.monetization.InvoiceService.SyncLineItem(new BigDecimal("60.00"), "Remaining time on Pro")));

        org.mockito.ArgumentCaptor<Invoice> saved = org.mockito.ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).saveAndFlush(saved.capture());
        assertEquals(new BigDecimal("30.00"), saved.getValue().getAmount());
        assertEquals(start, saved.getValue().getInvoicePeriodStart());
        org.mockito.ArgumentCaptor<InvoiceItem> items = org.mockito.ArgumentCaptor.forClass(InvoiceItem.class);
        verify(invoiceItemRepository, org.mockito.Mockito.times(2)).save(items.capture());
        assertEquals(List.of(new BigDecimal("-30.00"), new BigDecimal("60.00")),
                items.getAllValues().stream().map(InvoiceItem::getChargeAmount).toList());
        verify(planFeatureRuleRepository, never()).getPlanFeatureRuleByPlanIn(any());
        verify(eventRepository, never()).findEventsForBillingBySubscription(any(), any(), any(), any(), any());
    }

    @Test
    void testCreateNewInvoice_CreatesBasePriceItem() {
        // Setup
        Account account = new Account();
        account.setId(UUID.randomUUID());

        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setAccount(account);

        Plan plan = new Plan();
        plan.setId(UUID.randomUUID());
        plan.setPriceAmount(new BigDecimal("49.99"));
        plan.setName("Pro Plan");

        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setCustomer(customer);
        subscription.setPlan(plan);
        subscription.setAccount(account);
        subscription.setIsActive(true);

        Instant start = Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS);
        Instant end = Instant.now();

        AccountSetting accountSetting = new AccountSetting();
        accountSetting.setStripeMode(com.tansoflow.tansocore.model.api.external.StripeMode.NONE);
        when(accountSettingRepository.findAccountSettingById(account.getId())).thenReturn(accountSetting);

        when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(i -> {
            Invoice inv = i.getArgument(0);
            inv.setId(UUID.randomUUID());
            return inv;
        });

        when(planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(any())).thenReturn(List.of());

        CreateInvoiceParams params = new CreateInvoiceParams(
                subscription,
                LocalDate.now(),
                InvoiceStatus.DUE,
                start,
                end,
                new BigDecimal("49.99"),
                "USD",
                InvoiceType.REGULAR
        );

        // Execute
        Invoice result = invoiceService.createNewInvoice(params);

        // Verify base price item was created
        ArgumentCaptor<InvoiceItem> itemCaptor = ArgumentCaptor.forClass(InvoiceItem.class);
        verify(invoiceItemRepository).save(itemCaptor.capture());

        InvoiceItem basePriceItem = itemCaptor.getValue();
        assertEquals(new BigDecimal("49.99"), basePriceItem.getChargeAmount());
        assertEquals("Plan base price: Pro Plan", basePriceItem.getDescription());
        assertEquals(result, basePriceItem.getInvoice());
        assertEquals(account, basePriceItem.getAccount());
    }

    @Test
    void testCreateNewInvoice_AdjustmentType_NoBasePriceItem() {
        // Setup
        Account account = new Account();
        account.setId(UUID.randomUUID());

        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setAccount(account);

        Plan plan = new Plan();
        plan.setId(UUID.randomUUID());
        plan.setPriceAmount(new BigDecimal("49.99"));
        plan.setName("Pro Plan");

        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setCustomer(customer);
        subscription.setPlan(plan);
        subscription.setAccount(account);
        subscription.setIsActive(true);

        Instant start = Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS);
        Instant end = Instant.now();

        AccountSetting accountSetting = new AccountSetting();
        accountSetting.setStripeMode(com.tansoflow.tansocore.model.api.external.StripeMode.NONE);
        when(accountSettingRepository.findAccountSettingById(account.getId())).thenReturn(accountSetting);

        when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(i -> {
            Invoice inv = i.getArgument(0);
            inv.setId(UUID.randomUUID());
            return inv;
        });

        when(planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(any())).thenReturn(List.of());

        CreateInvoiceParams params = new CreateInvoiceParams(
                subscription,
                LocalDate.now(),
                InvoiceStatus.DUE,
                start,
                end,
                new BigDecimal("10.00"),
                "USD",
                InvoiceType.ADJUSTMENT
        );

        // Execute
        invoiceService.createNewInvoice(params);

        // Verify no base price item for ADJUSTMENT invoices
        verify(invoiceItemRepository, never()).save(any(InvoiceItem.class));
    }

    @Test
    void testProcessPendingInvoices_MarkAsDueWhenOverdue() {
        // Setup test data
        Invoice pendingInvoice = new Invoice();
        pendingInvoice.setId(UUID.randomUUID());
        pendingInvoice.setStatus(InvoiceStatus.PENDING.name());
        pendingInvoice.setDueDate(LocalDate.now().minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());

        Plan plan = new Plan();
        plan.setPriceAmount(BigDecimal.TEN);

        Subscription subscription = new Subscription();
        subscription.setIntervalMonths(1);
        subscription.setGracePeriodDays(0);
        subscription.setPlan(plan);
        pendingInvoice.setSubscription(subscription);

        when(planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(any())).thenReturn(List.of());
        when(invoiceRepository.getInvoicesByStatusExcludingFullSyncPaged(eq(InvoiceStatus.PENDING.name()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pendingInvoice)));
        when(invoiceRepository.save(any(Invoice.class))).thenReturn(pendingInvoice);

        // Execute method
        invoiceService.processPendingInvoices();

        // Verify behavior
        ArgumentCaptor<Invoice> invoiceCaptor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository, times(1)).save(invoiceCaptor.capture());

        Invoice updatedInvoice = invoiceCaptor.getValue();
        assertEquals(InvoiceStatus.DUE.name(), updatedInvoice.getStatus());
    }

    @Test
    void testProcessPendingInvoices_NoChangeForNotOverdue() {
        // Setup test data
        Invoice pendingInvoice = new Invoice();
        pendingInvoice.setId(UUID.randomUUID());
        pendingInvoice.setStatus(InvoiceStatus.PENDING.name());
        pendingInvoice.setDueDate(LocalDate.now().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());

        Plan plan = new Plan();
        plan.setPriceAmount(BigDecimal.TEN);

        Subscription subscription = new Subscription();
        subscription.setIntervalMonths(1);
        subscription.setGracePeriodDays(0);
        subscription.setPlan(plan);
        pendingInvoice.setSubscription(subscription);

        when(planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(any())).thenReturn(List.of());
        when(invoiceRepository.getInvoicesByStatusExcludingFullSyncPaged(eq(InvoiceStatus.PENDING.name()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pendingInvoice)));

        // Execute method
        invoiceService.processPendingInvoices();

        // Verify behavior
        verify(invoiceRepository, never()).save(any(Invoice.class));
    }

    @Test
    void testProcessPendingInvoices_MultipleInvoices() {
        // Setup test data
        Plan plan = new Plan();
        plan.setPriceAmount(BigDecimal.TEN);

        Invoice overdueInvoice = new Invoice();
        overdueInvoice.setId(UUID.randomUUID());
        overdueInvoice.setStatus(InvoiceStatus.PENDING.name());
        overdueInvoice.setDueDate(LocalDate.now().minusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant());

        Invoice notOverdueInvoice = new Invoice();
        notOverdueInvoice.setId(UUID.randomUUID());
        notOverdueInvoice.setStatus(InvoiceStatus.PENDING.name());
        notOverdueInvoice.setDueDate(LocalDate.now().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());

        Subscription subscription = new Subscription();
        subscription.setIntervalMonths(1);
        subscription.setGracePeriodDays(0);
        subscription.setPlan(plan);
        overdueInvoice.setSubscription(subscription);
        notOverdueInvoice.setSubscription(subscription);

        when(planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(any())).thenReturn(List.of());
        when(invoiceRepository.getInvoicesByStatusExcludingFullSyncPaged(eq(InvoiceStatus.PENDING.name()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(overdueInvoice, notOverdueInvoice)));
        when(invoiceRepository.save(any(Invoice.class))).thenReturn(overdueInvoice);

        // Execute method
        invoiceService.processPendingInvoices();

        // Verify behavior
        verify(invoiceRepository, times(1)).save(overdueInvoice);
        verify(invoiceRepository, never()).save(notOverdueInvoice);
    }

    @Test
    void testProcessDueInvoices_MarkAsPastDueWhenOverdue() {
        // Setup test data
        Invoice dueInvoice = new Invoice();
        dueInvoice.setId(UUID.randomUUID());
        dueInvoice.setStatus(InvoiceStatus.DUE.name());
        dueInvoice.setDueDate(LocalDate.now().minusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant());

        Plan plan = new Plan();
        plan.setId(UUID.randomUUID());
        plan.setPriceAmount(BigDecimal.TEN);

        Subscription subscription = new Subscription();
        subscription.setPlan(plan);
        subscription.setIntervalMonths(1);
        subscription.setGracePeriodDays(0);
        dueInvoice.setSubscription(subscription);

        when(invoiceRepository.getInvoicesByStatusExcludingFullSyncPaged(eq(InvoiceStatus.DUE.name()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(dueInvoice)));
        when(invoiceRepository.save(any(Invoice.class))).thenReturn(dueInvoice);
        when(planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(anyList())).thenReturn(List.of());

        // Execute method
        invoiceService.processDueInvoices();

        // Verify behavior
        ArgumentCaptor<Invoice> invoiceCaptor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository, atLeastOnce()).save(invoiceCaptor.capture());

        Invoice updatedInvoice = invoiceCaptor.getValue();
        assertEquals(InvoiceStatus.PAST_DUE.name(), updatedInvoice.getStatus());
    }

    @Test
    void testProcessDueInvoices_KeepStatusDueWhenNotOverdue() {
        // Setup test data
        Invoice dueInvoice = new Invoice();
        dueInvoice.setId(UUID.randomUUID());
        dueInvoice.setStatus(InvoiceStatus.DUE.name());
        dueInvoice.setDueDate(LocalDate.now().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());

        Plan plan = new Plan();
        plan.setId(UUID.randomUUID());
        plan.setPriceAmount(BigDecimal.TEN);

        Subscription subscription = new Subscription();
        subscription.setPlan(plan);
        subscription.setIntervalMonths(1);
        subscription.setGracePeriodDays(5);
        dueInvoice.setSubscription(subscription);

        when(invoiceRepository.getInvoicesByStatusExcludingFullSyncPaged(eq(InvoiceStatus.DUE.name()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(dueInvoice)));
        when(planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(anyList())).thenReturn(List.of());

        // Execute method
        invoiceService.processDueInvoices();

        // Verify behavior
        // verify(invoiceRepository, never()).save(any(Invoice.class));
        // Actually, it might be saved if we added usage rules check, but here we returned empty list for rules
        // But wait, if hasUsageRules returns false, it should NOT save if not overdue.
        verify(invoiceRepository, never()).save(any(Invoice.class));
    }

    @Test
    void testProcessDueInvoices_RevokeEntitlementsWhenPastDue() {
        // Setup test data
        Invoice overdueInvoice = new Invoice();
        overdueInvoice.setId(UUID.randomUUID());
        overdueInvoice.setStatus(InvoiceStatus.DUE.name());
        overdueInvoice.setDueDate(LocalDate.now().minusDays(5).atStartOfDay(ZoneOffset.UTC).toInstant());

        Plan plan = new Plan();
        plan.setId(UUID.randomUUID());
        plan.setPriceAmount(BigDecimal.TEN);

        Subscription subscription = new Subscription();
        subscription.setPlan(plan);
        subscription.setIntervalMonths(1);
        subscription.setGracePeriodDays(3);
        overdueInvoice.setSubscription(subscription);

        when(invoiceRepository.getInvoicesByStatusExcludingFullSyncPaged(eq(InvoiceStatus.DUE.name()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(overdueInvoice)));
        when(invoiceRepository.save(any(Invoice.class))).thenReturn(overdueInvoice);
        when(planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(anyList())).thenReturn(List.of());

        // Execute method
        invoiceService.processDueInvoices();

        // Verify behavior
        verify(invoiceRepository, atLeastOnce()).save(overdueInvoice);
        verify(entitlementService, times(1)).processEntitlementRevokeForSubscription(subscription);
    }

    @Test
    void testProcessPendingInvoices_RefreshesUsageForUsagePlans() {
        // Setup
        Account account = new Account();
        account.setId(UUID.randomUUID());

        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setAccount(account);

        Plan plan = new Plan();
        plan.setId(UUID.randomUUID());
        plan.setPriceAmount(BigDecimal.ZERO);

        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setCustomer(customer);
        subscription.setPlan(plan);
        subscription.setAccount(account);
        subscription.setIsActive(true);
        subscription.setGracePeriodDays(7);

        Invoice pendingInvoice = new Invoice();
        pendingInvoice.setId(UUID.randomUUID());
        pendingInvoice.setStatus(InvoiceStatus.PENDING.name());
        pendingInvoice.setSubscription(subscription);
        pendingInvoice.setAccount(account);
        pendingInvoice.setAmount(BigDecimal.ZERO);
        pendingInvoice.setDueDate(LocalDate.now().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        pendingInvoice.setInvoicePeriodStart(Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS));
        pendingInvoice.setInvoicePeriodEnd(Instant.now());

        Feature feature = new Feature();
        feature.setKey("usage.feature");

        PlanFeatureRule rule = new PlanFeatureRule();
        rule.setFeature(feature);
        rule.setValue(Map.of("model", "usage", "price_per_unit", 0.5));

        Event usageEvent = new Event();
        usageEvent.setEventName("usage.feature");
        usageEvent.setUsageUnits(new BigDecimal("10"));
        usageEvent.setRevenueAmount(new BigDecimal("5.00"));

        when(invoiceRepository.getInvoicesByStatusExcludingFullSyncPaged(eq(InvoiceStatus.PENDING.name()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pendingInvoice)));
        when(eventRepository.findEventsForBillingBySubscription(any(), any(), any(), any(), any()))
                .thenReturn(List.of(usageEvent));
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(any()))
                .thenReturn(List.of(subscription));
        when(eventRepository.findEventsForBillingUntagged(any(), any(), any(), any()))
                .thenReturn(List.of());
        when(planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(any())).thenReturn(List.of(rule));

        // Execute
        invoiceService.processPendingInvoices();

        // Verify
        // 10 units * 0.5 = 5.00
        assertEquals(new BigDecimal("5.00"), pendingInvoice.getAmount());
        verify(invoiceItemRepository).deleteUsageItemsByInvoice(pendingInvoice);
        verify(invoiceItemRepository).saveAll(anyList());
        verify(invoiceRepository).save(pendingInvoice);
    }

    @Test
    void testClockSkimming_BoundaryEvents() {
        // Setup
        Account account = new Account();
        account.setId(UUID.randomUUID());

        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setAccount(account);

        Plan plan = new Plan();
        plan.setBillingTiming("IN_ADVANCE");
        plan.setPriceAmount(BigDecimal.TEN);

        Subscription sub = new Subscription();
        sub.setId(UUID.randomUUID());
        sub.setCustomer(customer);
        sub.setPlan(plan);
        sub.setIsActive(true);

        Instant boundary = Instant.parse("2026-01-24T00:00:00Z");
        
        // Invoice 1: [T-1h, boundary)
        Invoice inv1 = new Invoice();
        inv1.setSubscription(sub);
        inv1.setStatus(InvoiceStatus.DUE.name());
        inv1.setInvoicePeriodStart(boundary.minus(1, java.time.temporal.ChronoUnit.HOURS));
        inv1.setInvoicePeriodEnd(boundary);
        inv1.setAmount(BigDecimal.TEN);
        inv1.setDueDate(boundary.plus(7, java.time.temporal.ChronoUnit.DAYS));

        // Invoice 2: [boundary, T+1h)
        Invoice inv2 = new Invoice();
        inv2.setSubscription(sub);
        inv2.setStatus(InvoiceStatus.DUE.name());
        inv2.setInvoicePeriodStart(boundary);
        inv2.setInvoicePeriodEnd(boundary.plus(1, java.time.temporal.ChronoUnit.HOURS));
        inv2.setAmount(BigDecimal.TEN);
        inv2.setDueDate(boundary.plus(30, java.time.temporal.ChronoUnit.DAYS));

        Feature feature = new Feature();
        feature.setKey("usage.feature");
        PlanFeatureRule rule = new PlanFeatureRule();
        rule.setFeature(feature);
        rule.setValue(Map.of("model", "usage", "price_per_unit", 1.0));

        // Event EXACTLY at boundary
        Event boundaryEvent = new Event();
        boundaryEvent.setEventName("usage.feature");
        boundaryEvent.setUsageUnits(new BigDecimal("1"));
        boundaryEvent.setRevenueAmount(new BigDecimal("1.00"));
        boundaryEvent.setOccurredAt(boundary);

        when(planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(any())).thenReturn(List.of(rule));
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(any()))
                .thenReturn(List.of(sub));
        when(eventRepository.findEventsForBillingUntagged(any(), any(), any(), any()))
                .thenReturn(List.of());

        // When processing inv1, it should NOT find boundaryEvent because it's < boundary
        when(eventRepository.findEventsForBillingBySubscription(any(), any(), any(), eq(inv1.getInvoicePeriodStart()), eq(inv1.getInvoicePeriodEnd())))
                .thenReturn(List.of());

        // When processing inv2, it SHOULD find boundaryEvent because it's >= boundary
        when(eventRepository.findEventsForBillingBySubscription(any(), any(), any(), eq(inv2.getInvoicePeriodStart()), eq(inv2.getInvoicePeriodEnd())))
                .thenReturn(List.of(boundaryEvent));

        // Execute for Inv 1 (explicitly calling private helper via public-like simulation)
        // Since we are mocking getInvoicesByStatus(DUE), we can just call processDueInvoices()
        
        when(invoiceRepository.getInvoicesByStatusExcludingFullSyncPaged(eq(InvoiceStatus.DUE.name()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(inv1)));
        invoiceService.processDueInvoices();
        assertEquals(0, new BigDecimal("10.00").compareTo(inv1.getAmount()), "Inv1 should NOT capture event at its end boundary");

        // Execute for Inv 2
        when(invoiceRepository.getInvoicesByStatusExcludingFullSyncPaged(eq(InvoiceStatus.DUE.name()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(inv2)));
        invoiceService.processDueInvoices();
        assertEquals(0, new BigDecimal("11.00").compareTo(inv2.getAmount()), "Inv2 SHOULD capture event at its start boundary");
    }

    @Test
    void testProcessDueInvoices_HybridPlan_UpdatesUsage() {
        // Setup
        Account account = new Account();
        account.setId(UUID.randomUUID());

        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setAccount(account);

        Plan plan = new Plan();
        plan.setId(UUID.randomUUID());
        plan.setBillingTiming("IN_ADVANCE");
        plan.setPriceAmount(new BigDecimal("100.00"));

        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setCustomer(customer);
        subscription.setPlan(plan);
        subscription.setIsActive(true);
        subscription.setGracePeriodDays(7);

        Invoice dueInvoice = new Invoice();
        dueInvoice.setId(UUID.randomUUID());
        dueInvoice.setStatus(InvoiceStatus.DUE.name());
        dueInvoice.setSubscription(subscription);
        dueInvoice.setAmount(new BigDecimal("100.00"));
        dueInvoice.setDueDate(LocalDate.now().plusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant());
        dueInvoice.setInvoicePeriodStart(Instant.now().minus(5, java.time.temporal.ChronoUnit.DAYS));
        dueInvoice.setInvoicePeriodEnd(Instant.now().plus(25, java.time.temporal.ChronoUnit.DAYS));

        Feature feature = new Feature();
        feature.setKey("hybrid.feature");

        PlanFeatureRule rule = new PlanFeatureRule();
        rule.setFeature(feature);
        rule.setValue(Map.of("model", "usage", "price_per_unit", 10.0));

        Event usageEvent = new Event();
        usageEvent.setEventName("hybrid.feature");
        usageEvent.setUsageUnits(new BigDecimal("2"));
        usageEvent.setRevenueAmount(new BigDecimal("20.00"));

        when(invoiceRepository.getInvoicesByStatusExcludingFullSyncPaged(eq(InvoiceStatus.DUE.name()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(dueInvoice)));
        when(planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(any())).thenReturn(List.of(rule));
        when(eventRepository.findEventsForBillingBySubscription(any(), any(), any(), any(), any()))
                .thenReturn(List.of(usageEvent));
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(any()))
                .thenReturn(List.of(subscription));
        when(eventRepository.findEventsForBillingUntagged(any(), any(), any(), any()))
                .thenReturn(List.of());

        // Execute
        invoiceService.processDueInvoices();

        // Verify: 100.00 base + (2 * 10.00) usage = 120.00
        assertEquals(new BigDecimal("120.00"), dueInvoice.getAmount());
        verify(invoiceRepository, atLeastOnce()).save(dueInvoice);
    }

    // ── calculateUsageChargeForPeriod tests ──────────────────────────────────

    @Test
    void testCalculateUsageChargeForPeriod_AccumulateGraduated_MultiPeriod() {
        // Graduated tiers: 0-100 @ $1, 101+ @ $0.50
        // Period 1: 80 units → cost(80) - cost(0) = $80
        // Period 2: 50 units, cumulative=130 → cost(130) - cost(80) = $115 - $80 = $35
        Account account = new Account();
        account.setId(UUID.randomUUID());

        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setAccount(account);

        Plan plan = new Plan();
        plan.setId(UUID.randomUUID());

        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setCustomer(customer);
        subscription.setPlan(plan);
        subscription.setIsActive(true);
        Feature feature = new Feature();
        feature.setId(UUID.randomUUID());
        feature.setKey("api.calls");

        // Graduated pricing with accumulate mode
        Map<String, Object> pricingValue = new HashMap<>();
        pricingValue.put("model", "graduated");
        pricingValue.put("reset_mode", "accumulate");
        pricingValue.put("tiers", List.of(
                Map.of("up_to", 100, "price_per_unit", 1.0),
                Map.of("up_to", "inf", "price_per_unit", 0.5)
        ));

        PlanFeatureRule rule = new PlanFeatureRule();
        rule.setFeature(feature);
        rule.setValue(pricingValue);

        Instant period2Start = Instant.parse("2025-02-01T00:00:00Z");
        Instant period2End = Instant.parse("2025-03-01T00:00:00Z");

        // Period 2: 50 events in this period
        Event event1 = new Event();
        event1.setFeatureId(feature.getId());
        event1.setUsageUnits(new BigDecimal("30"));
        Event event2 = new Event();
        event2.setFeatureId(feature.getId());
        event2.setUsageUnits(new BigDecimal("20"));

        when(eventRepository.findEventsForBillingBySubscription(
                eq(customer.getId()), eq(subscription.getId()), any(), eq(period2Start), eq(period2End)))
                .thenReturn(List.of(event1, event2));
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId()))
                .thenReturn(List.of(subscription));
        when(eventRepository.findEventsForBillingUntagged(eq(customer.getId()), any(), eq(period2Start), eq(period2End)))
                .thenReturn(List.of());
        when(planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(any())).thenReturn(List.of(rule));

        // Cumulative total = 130 (80 from period 1 + 50 from period 2)
        when(eventRepository.sumUsageUnitsForSubscriptionOrUntaggedSince(
                eq(customer.getId()), eq(subscription.getId()), eq(feature.getId()), any(), any()))
                .thenReturn(new BigDecimal("130"));

        // Execute
        BigDecimal charge = invoiceService.calculateUsageChargeForPeriod(subscription, period2Start, period2End);

        // cost(130) = 100*1 + 30*0.5 = 115
        // cost(80) = 80*1 = 80
        // charge = 115 - 80 = 35
        assertEquals(new BigDecimal("35.00"), charge);
    }

    @Test
    void testCalculateUsageChargeForPeriod_AccumulatePerUnit() {
        // Per-unit pricing @ $0.10 per unit with accumulate mode
        // delta * rate = same result regardless of accumulation
        Account account = new Account();
        account.setId(UUID.randomUUID());

        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setAccount(account);

        Plan plan = new Plan();
        plan.setId(UUID.randomUUID());

        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setCustomer(customer);
        subscription.setPlan(plan);
        subscription.setIsActive(true);
        Feature feature = new Feature();
        feature.setId(UUID.randomUUID());
        feature.setKey("messages.sent");

        Map<String, Object> pricingValue = new HashMap<>();
        pricingValue.put("model", "usage");
        pricingValue.put("reset_mode", "accumulate");
        pricingValue.put("price_per_unit", 0.10);

        PlanFeatureRule rule = new PlanFeatureRule();
        rule.setFeature(feature);
        rule.setValue(pricingValue);

        Instant periodStart = Instant.parse("2025-02-01T00:00:00Z");
        Instant periodEnd = Instant.parse("2025-03-01T00:00:00Z");

        Event event = new Event();
        event.setFeatureId(feature.getId());
        event.setUsageUnits(new BigDecimal("200"));

        when(eventRepository.findEventsForBillingBySubscription(
                eq(customer.getId()), eq(subscription.getId()), any(), eq(periodStart), eq(periodEnd)))
                .thenReturn(List.of(event));
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId()))
                .thenReturn(List.of(subscription));
        when(eventRepository.findEventsForBillingUntagged(eq(customer.getId()), any(), eq(periodStart), eq(periodEnd)))
                .thenReturn(List.of());
        when(planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(any())).thenReturn(List.of(rule));
        when(eventRepository.sumUsageUnitsForSubscriptionOrUntaggedSince(
                eq(customer.getId()), eq(subscription.getId()), eq(feature.getId()), any(), any()))
                .thenReturn(new BigDecimal("500"));

        BigDecimal charge = invoiceService.calculateUsageChargeForPeriod(subscription, periodStart, periodEnd);

        // 200 units * $0.10 = $20.00
        assertEquals(new BigDecimal("20.00"), charge);
    }

    @Test
    void testCalculateUsageChargeForPeriod_NoEvents_ReturnsZero() {
        Account account = new Account();
        account.setId(UUID.randomUUID());

        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setAccount(account);

        Plan plan = new Plan();
        plan.setId(UUID.randomUUID());

        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setCustomer(customer);
        subscription.setPlan(plan);
        subscription.setIsActive(true);

        Instant periodStart = Instant.parse("2025-02-01T00:00:00Z");
        Instant periodEnd = Instant.parse("2025-03-01T00:00:00Z");

        when(eventRepository.findEventsForBillingBySubscription(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(any()))
                .thenReturn(List.of(subscription));
        when(eventRepository.findEventsForBillingUntagged(any(), any(), any(), any()))
                .thenReturn(List.of());

        BigDecimal charge = invoiceService.calculateUsageChargeForPeriod(subscription, periodStart, periodEnd);

        assertEquals(BigDecimal.ZERO, charge);
    }

    @Test
    void testCalculateUsageChargeForPeriod_ResetGraduated_RecalculatesAtBillingTime() {
        // Graduated tiers: 0-100 @ $1, 101+ @ $0.50
        // 150 units in period, per-event costAmount set to ZERO (simulating the old bug)
        // Expected: graduatedCost(150) = 100*$1 + 50*$0.50 = $125.00
        Account account = new Account();
        account.setId(UUID.randomUUID());

        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setAccount(account);

        Plan plan = new Plan();
        plan.setId(UUID.randomUUID());

        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setCustomer(customer);
        subscription.setPlan(plan);
        subscription.setIsActive(true);

        Feature feature = new Feature();
        feature.setId(UUID.randomUUID());
        feature.setKey("api.calls");

        // Graduated pricing with default (reset) mode — no reset_mode field
        Map<String, Object> pricingValue = new HashMap<>();
        pricingValue.put("model", "graduated");
        pricingValue.put("tiers", List.of(
                Map.of("up_to", 100, "price_per_unit", 1.0),
                Map.of("up_to", "inf", "price_per_unit", 0.5)
        ));

        PlanFeatureRule rule = new PlanFeatureRule();
        rule.setFeature(feature);
        rule.setValue(pricingValue);

        Instant periodStart = Instant.parse("2025-02-01T00:00:00Z");
        Instant periodEnd = Instant.parse("2025-03-01T00:00:00Z");

        // Events with revenueAmount = ZERO (simulating the old bug where graduated events got $0)
        Event event1 = new Event();
        event1.setFeatureId(feature.getId());
        event1.setUsageUnits(new BigDecimal("100"));
        event1.setRevenueAmount(BigDecimal.ZERO);

        Event event2 = new Event();
        event2.setFeatureId(feature.getId());
        event2.setUsageUnits(new BigDecimal("50"));
        event2.setRevenueAmount(BigDecimal.ZERO);

        when(eventRepository.findEventsForBillingBySubscription(
                eq(customer.getId()), eq(subscription.getId()), any(), eq(periodStart), eq(periodEnd)))
                .thenReturn(List.of(event1, event2));
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId()))
                .thenReturn(List.of(subscription));
        when(eventRepository.findEventsForBillingUntagged(eq(customer.getId()), any(), eq(periodStart), eq(periodEnd)))
                .thenReturn(List.of());
        when(planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(any())).thenReturn(List.of(rule));

        BigDecimal charge = invoiceService.calculateUsageChargeForPeriod(subscription, periodStart, periodEnd);

        // graduatedCost(150) = 100*1 + 50*0.5 = 125.00
        assertEquals(new BigDecimal("125.00"), charge);
    }

    @Test
    void testCalculateUsageChargeForPeriod_ResetSimpleUsage_SumsPerEventCosts() {
        // Simple usage @ $0.10/unit in reset mode
        // Events have pre-calculated costAmount values that should be summed
        Account account = new Account();
        account.setId(UUID.randomUUID());

        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setAccount(account);

        Plan plan = new Plan();
        plan.setId(UUID.randomUUID());

        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setCustomer(customer);
        subscription.setPlan(plan);
        subscription.setIsActive(true);

        Feature feature = new Feature();
        feature.setId(UUID.randomUUID());
        feature.setKey("messages.sent");

        Map<String, Object> pricingValue = new HashMap<>();
        pricingValue.put("model", "usage");
        pricingValue.put("price_per_unit", 0.10);

        PlanFeatureRule rule = new PlanFeatureRule();
        rule.setFeature(feature);
        rule.setValue(pricingValue);

        Instant periodStart = Instant.parse("2025-02-01T00:00:00Z");
        Instant periodEnd = Instant.parse("2025-03-01T00:00:00Z");

        Event event1 = new Event();
        event1.setFeatureId(feature.getId());
        event1.setUsageUnits(new BigDecimal("100"));
        event1.setRevenueAmount(new BigDecimal("10.00"));

        Event event2 = new Event();
        event2.setFeatureId(feature.getId());
        event2.setUsageUnits(new BigDecimal("50"));
        event2.setRevenueAmount(new BigDecimal("5.00"));

        when(eventRepository.findEventsForBillingBySubscription(
                eq(customer.getId()), eq(subscription.getId()), any(), eq(periodStart), eq(periodEnd)))
                .thenReturn(List.of(event1, event2));
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId()))
                .thenReturn(List.of(subscription));
        when(eventRepository.findEventsForBillingUntagged(eq(customer.getId()), any(), eq(periodStart), eq(periodEnd)))
                .thenReturn(List.of());
        when(planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(any())).thenReturn(List.of(rule));

        BigDecimal charge = invoiceService.calculateUsageChargeForPeriod(subscription, periodStart, periodEnd);

        // Sum of per-event costs: 10.00 + 5.00 = 15.00
        assertEquals(new BigDecimal("15.00"), charge);
    }

    // ── planHasAccumulateModeFeatures tests ──────────────────────────────────

    @Test
    void testPlanHasAccumulateModeFeatures_True() {
        Plan plan = new Plan();
        plan.setId(UUID.randomUUID());

        Feature feature = new Feature();
        feature.setId(UUID.randomUUID());
        feature.setKey("api.calls");

        Map<String, Object> pricingValue = new HashMap<>();
        pricingValue.put("model", "graduated");
        pricingValue.put("reset_mode", "accumulate");
        pricingValue.put("tiers", List.of(
                Map.of("up_to", 100, "price_per_unit", 1.0),
                Map.of("up_to", "inf", "price_per_unit", 0.5)
        ));

        PlanFeatureRule rule = new PlanFeatureRule();
        rule.setFeature(feature);
        rule.setValue(pricingValue);

        when(planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(List.of(plan))).thenReturn(List.of(rule));

        assertTrue(invoiceService.planHasAccumulateModeFeatures(plan));
    }

    @Test
    void testPlanHasAccumulateModeFeatures_False_ResetMode() {
        Plan plan = new Plan();
        plan.setId(UUID.randomUUID());

        Feature feature = new Feature();
        feature.setId(UUID.randomUUID());
        feature.setKey("api.calls");

        Map<String, Object> pricingValue = new HashMap<>();
        pricingValue.put("model", "usage");
        pricingValue.put("price_per_unit", 0.01);

        PlanFeatureRule rule = new PlanFeatureRule();
        rule.setFeature(feature);
        rule.setValue(pricingValue);

        when(planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(List.of(plan))).thenReturn(List.of(rule));

        assertFalse(invoiceService.planHasAccumulateModeFeatures(plan));
    }

    @Test
    void testPlanHasAccumulateModeFeatures_False_NoRules() {
        Plan plan = new Plan();
        plan.setId(UUID.randomUUID());

        when(planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(List.of(plan))).thenReturn(List.of());

        assertFalse(invoiceService.planHasAccumulateModeFeatures(plan));
    }

    private Subscription subscriptionOn(Customer customer, Account account, String planKey, BigDecimal price, boolean active) {
        Plan plan = new Plan();
        plan.setId(UUID.randomUUID());
        plan.setKey(planKey);
        plan.setPriceAmount(price);
        plan.setBillingTiming(com.tansoflow.tansocore.model.plan.BillingTiming.IN_ADVANCE.name());

        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setCustomer(customer);
        subscription.setAccount(account);
        subscription.setPlan(plan);
        subscription.setIsActive(active);
        subscription.setCurrentPeriodStart(Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS));
        subscription.setCurrentPeriodEnd(Instant.now().plus(29, java.time.temporal.ChronoUnit.DAYS));
        return subscription;
    }

    private Invoice initialInvoiceFor(Subscription subscription, BigDecimal amount) {
        Invoice invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setSubscription(subscription);
        invoice.setAmount(amount);
        invoice.setType(InvoiceType.IN_ADVANCE_INITIAL.name());
        return invoice;
    }

    // An agent signs up onto the free default plan, then pays for a real plan. Both used to stay active and the
    // free plan kept granting its own entitlements. Found by the agent-ready end-to-end run, 2026-09-17.
    @Test
    void payingForAPaidPlanRetiresTheFreePlanTheCustomerWasOn() {
        Account account = new Account();
        account.setId(UUID.randomUUID());
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setAccount(account);
        customer.setExternalClientCustomerId("agent_7f3a");

        Subscription free = subscriptionOn(customer, account, "developer_demo", BigDecimal.ZERO, true);
        Subscription paid = subscriptionOn(customer, account, "starter", new BigDecimal("149.00"), false);
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId())).thenReturn(List.of(free, paid));

        invoiceService.markInvoiceAsPaid(initialInvoiceFor(paid, new BigDecimal("149.00")));

        assertTrue(paid.getIsActive());
        assertFalse(free.getIsActive());
        assertEquals(com.tansoflow.tansocore.model.subscription.type.CancelModes.IMMEDIATE.name(), free.getCancelMode());
        verify(entitlementService).processEntitlementRevokeForSubscription(free);
        verify(creditService).clawBackPlanIncludedCredits(free.getId(), account.getId());
    }

    // Two paid subscriptions is a legitimate setup: a customer on two products. Cancelling one because the other
    // activated would throw away revenue the customer agreed to.
    @Test
    void payingForAPaidPlanLeavesAnotherPaidSubscriptionAlone() {
        Account account = new Account();
        account.setId(UUID.randomUUID());
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setAccount(account);
        customer.setExternalClientCustomerId("cust_42");

        Subscription otherPaid = subscriptionOn(customer, account, "analytics", new BigDecimal("49.00"), true);
        Subscription paid = subscriptionOn(customer, account, "starter", new BigDecimal("149.00"), false);
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId())).thenReturn(List.of(otherPaid, paid));

        invoiceService.markInvoiceAsPaid(initialInvoiceFor(paid, new BigDecimal("149.00")));

        assertTrue(otherPaid.getIsActive());
        verify(entitlementService, never()).processEntitlementRevokeForSubscription(otherPaid);
    }

    // The free default plan activating must not retire anything; it is what the agent starts on.
    @Test
    void activatingAFreePlanRetiresNothing() {
        Account account = new Account();
        account.setId(UUID.randomUUID());
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setAccount(account);

        Subscription free = subscriptionOn(customer, account, "developer_demo", BigDecimal.ZERO, false);
        Subscription otherFree = subscriptionOn(customer, account, "trial", BigDecimal.ZERO, true);

        invoiceService.markInvoiceAsPaid(initialInvoiceFor(free, BigDecimal.ZERO));

        assertTrue(otherFree.getIsActive());
        verify(subscriptionRepository, never()).findSubscriptionsByCustomer_Id(customer.getId());
    }

    // An upgrade an agent has not paid for waits on its adjustment invoice. Paying it is what swaps the plan.
    // Without this, nothing in pass-through mode ever completed a pending upgrade.
    @Test
    void payingTheAdjustmentInvoiceCompletesAPendingUpgrade() {
        Account account = new Account();
        account.setId(UUID.randomUUID());
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setAccount(account);

        Plan free = new Plan();
        free.setId(UUID.randomUUID());
        free.setKey("developer_demo");
        free.setPriceAmount(BigDecimal.ZERO);
        Plan starter = new Plan();
        starter.setId(UUID.randomUUID());
        starter.setKey("starter");
        starter.setPriceAmount(new BigDecimal("149.00"));

        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setCustomer(customer);
        subscription.setAccount(account);
        subscription.setPlan(free);
        subscription.setIsActive(true);

        Invoice adjustment = new Invoice();
        adjustment.setId(UUID.randomUUID());
        adjustment.setSubscription(subscription);
        adjustment.setAmount(new BigDecimal("74.50"));
        adjustment.setType(InvoiceType.ADJUSTMENT.name());

        com.tansoflow.tansocore.entity.SubscriptionScheduledChange pending =
                new com.tansoflow.tansocore.entity.SubscriptionScheduledChange();
        pending.setSubscription(subscription);
        pending.setFromPlan(free);
        pending.setToPlan(starter);
        pending.setAdjustmentInvoice(adjustment);
        UUID keyId = UUID.randomUUID();
        pending.setApiKeyId(keyId);
        pending.setStatus(com.tansoflow.tansocore.model.subscription.type.SubscriptionScheduledChangeStatus.PENDING.name());
        when(subscriptionScheduledChangeRepository.findPendingUpgradeByAdjustmentInvoice(adjustment))
                .thenReturn(java.util.Optional.of(pending));

        invoiceService.markInvoiceAsPaid(adjustment);

        assertEquals(starter, subscription.getPlan());
        assertEquals(com.tansoflow.tansocore.model.subscription.type.SubscriptionScheduledChangeStatus.COMPLETED.name(),
                pending.getStatus());
        verify(entitlementService).processEntitlementsForSubscription(subscription);
        // The upgrade's own credits: the period's grant was already made on the free plan.
        verify(creditService).grantUpgradeDelta(subscription, free, starter, pending.getId());
        // And the key that committed the money has its budget drawn down now that the money moved. A human paid the
        // invoice, so it is recorded as hosted and stays out of the mandate.
        verify(keyBudgetService).recordSpend(eq(account.getId()), eq(keyId),
                eq(com.tansoflow.tansocore.model.apikey.type.SpendKind.MONEY),
                eq(com.tansoflow.tansocore.model.apikey.type.SpendChannel.HOSTED), eq(new BigDecimal("74.50")),
                any(), any());
    }

    // Stripe reports one payment as invoice.paid and invoice.payment_succeeded, with different event ids, so both
    // reach markInvoiceAsPaid. The second must find the invoice PAID and do nothing; it used to fulfil the pending
    // upgrade and grant its credit delta a second time.
    @Test
    void theSecondPaymentReportForAnAdjustmentInvoiceGrantsNothing() {
        Account account = new Account();
        account.setId(UUID.randomUUID());
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setAccount(account);

        Plan free = new Plan();
        free.setId(UUID.randomUUID());
        free.setKey("developer_demo");
        free.setPriceAmount(BigDecimal.ZERO);
        Plan starter = new Plan();
        starter.setId(UUID.randomUUID());
        starter.setKey("starter");
        starter.setPriceAmount(new BigDecimal("149.00"));

        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setCustomer(customer);
        subscription.setAccount(account);
        subscription.setPlan(free);
        subscription.setIsActive(true);

        Invoice adjustment = new Invoice();
        adjustment.setId(UUID.randomUUID());
        adjustment.setSubscription(subscription);
        adjustment.setAmount(new BigDecimal("74.50"));
        adjustment.setType(InvoiceType.ADJUSTMENT.name());
        adjustment.setStatus(InvoiceStatus.DUE.name());

        com.tansoflow.tansocore.entity.SubscriptionScheduledChange pending =
                new com.tansoflow.tansocore.entity.SubscriptionScheduledChange();
        pending.setId(UUID.randomUUID());
        pending.setSubscription(subscription);
        pending.setFromPlan(free);
        pending.setToPlan(starter);
        pending.setAdjustmentInvoice(adjustment);
        pending.setStatus(com.tansoflow.tansocore.model.subscription.type.SubscriptionScheduledChangeStatus.PENDING.name());
        when(subscriptionScheduledChangeRepository.findPendingUpgradeByAdjustmentInvoice(adjustment))
                .thenReturn(java.util.Optional.of(pending));
        when(invoiceRepository.findByIdForUpdate(adjustment.getId())).thenReturn(java.util.Optional.of(adjustment));

        invoiceService.markInvoiceAsPaid(adjustment.getId().toString());
        invoiceService.markInvoiceAsPaid(adjustment.getId().toString());

        assertEquals(InvoiceStatus.PAID.name(), adjustment.getStatus());
        verify(invoiceRepository, times(2)).findByIdForUpdate(adjustment.getId());
        verify(invoiceRepository, never()).findById(any());
        verify(invoiceRepository, times(1)).save(adjustment);
        verify(creditService, times(1)).grantUpgradeDelta(subscription, free, starter, pending.getId());
        verify(keyBudgetService, times(1)).recordSpend(any(), any(), any(), any(), any(), any(), any());
    }

    // Paying a voided invoice must grant nothing: the change it belonged to is gone, and flipping it to PAID
    // would claim the customer and grant entitlements for an upgrade nobody is waiting on.
    @Test
    void payingAVoidedInvoiceGrantsNothing() {
        Account account = new Account();
        account.setId(UUID.randomUUID());
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setAccount(account);

        Plan plan = new Plan();
        plan.setId(UUID.randomUUID());
        plan.setPriceAmount(new BigDecimal("149.00"));

        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setCustomer(customer);
        subscription.setAccount(account);
        subscription.setPlan(plan);
        subscription.setIsActive(false);

        Invoice voided = new Invoice();
        voided.setId(UUID.randomUUID());
        voided.setSubscription(subscription);
        voided.setAmount(new BigDecimal("74.50"));
        voided.setType(InvoiceType.IN_ADVANCE_INITIAL.name());
        voided.setStatus(InvoiceStatus.VOID.name());

        invoiceService.markInvoiceAsPaid(voided);

        assertEquals(InvoiceStatus.VOID.name(), voided.getStatus());
        assertFalse(subscription.getIsActive());
        verify(entitlementService, never()).processEntitlementsForSubscription(any());
        verify(invoiceRepository, never()).save(any());
    }

    // The Stripe-integration path fulfils its own pending upgrade from the webhook, and a regular invoice must
    // not go looking for one at all.
    @Test
    void aRegularPaidInvoiceDoesNotLookForAPendingUpgrade() {
        Account account = new Account();
        account.setId(UUID.randomUUID());
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setAccount(account);

        Plan plan = new Plan();
        plan.setId(UUID.randomUUID());
        plan.setPriceAmount(new BigDecimal("149.00"));

        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setCustomer(customer);
        subscription.setAccount(account);
        subscription.setPlan(plan);
        subscription.setIsActive(true);

        Invoice regular = new Invoice();
        regular.setId(UUID.randomUUID());
        regular.setSubscription(subscription);
        regular.setAmount(new BigDecimal("149.00"));
        regular.setType(InvoiceType.REGULAR.name());

        invoiceService.markInvoiceAsPaid(regular);

        verify(subscriptionScheduledChangeRepository, never()).findPendingUpgradeByAdjustmentInvoice(any());
    }

    // An adjustment invoice pays for the rest of a period whose credits were already granted. Running the period
    // grant on it handed out the full plan allocation again whenever the period start had moved.
    @Test
    void payingAnAdjustmentInvoiceDoesNotRunThePeriodCreditGrant() {
        Account account = new Account();
        account.setId(UUID.randomUUID());
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setAccount(account);

        Subscription subscription = subscriptionOn(customer, account, "starter", new BigDecimal("149.00"), true);

        Invoice adjustment = new Invoice();
        adjustment.setId(UUID.randomUUID());
        adjustment.setSubscription(subscription);
        adjustment.setAmount(new BigDecimal("74.50"));
        adjustment.setType(InvoiceType.ADJUSTMENT.name());

        invoiceService.markInvoiceAsPaid(adjustment);

        assertEquals(InvoiceStatus.PAID.name(), adjustment.getStatus());
        verify(entitlementService).processEntitlementsForSubscription(subscription);
        verify(creditService, never()).processCreditGrantsForSubscription(any());
    }

    // A free subscription retired by a paid plan kept its outstanding invoices and pending upgrade. Paying the
    // leftover upgrade invoice later swapped the plan on the retired subscription and granted entitlements again.
    @Test
    void retiringAFreePlanVoidsItsOutstandingInvoicesAndCancelsItsScheduledChanges() {
        Account account = new Account();
        account.setId(UUID.randomUUID());
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setAccount(account);
        customer.setExternalClientCustomerId("agent_7f3a");

        Subscription free = subscriptionOn(customer, account, "developer_demo", BigDecimal.ZERO, true);
        Subscription paid = subscriptionOn(customer, account, "starter", new BigDecimal("149.00"), false);
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId())).thenReturn(List.of(free, paid));

        Invoice leftoverUpgrade = new Invoice();
        leftoverUpgrade.setId(UUID.randomUUID());
        leftoverUpgrade.setSubscription(free);
        leftoverUpgrade.setAmount(new BigDecimal("74.50"));
        leftoverUpgrade.setType(InvoiceType.ADJUSTMENT.name());
        leftoverUpgrade.setStatus(InvoiceStatus.DUE.name());
        when(invoiceRepository.findVoidableInvoicesBySubscription(eq(free), any())).thenReturn(List.of(leftoverUpgrade));

        // An adjustment invoice that has gone past due is still payable, whichever query surfaces it.
        Invoice pastDueUpgrade = new Invoice();
        pastDueUpgrade.setId(UUID.randomUUID());
        pastDueUpgrade.setSubscription(free);
        pastDueUpgrade.setType(InvoiceType.ADJUSTMENT.name());
        pastDueUpgrade.setStatus(InvoiceStatus.PAST_DUE.name());
        com.tansoflow.tansocore.entity.SubscriptionScheduledChange pending =
                new com.tansoflow.tansocore.entity.SubscriptionScheduledChange();
        pending.setSubscription(free);
        pending.setAdjustmentInvoice(pastDueUpgrade);
        when(subscriptionScheduledChangeRepository.findPendingUpgradeBySubscription(free))
                .thenReturn(java.util.Optional.of(pending));

        invoiceService.markInvoiceAsPaid(initialInvoiceFor(paid, new BigDecimal("149.00")));

        assertFalse(free.getIsActive());
        assertEquals(InvoiceStatus.VOID.name(), leftoverUpgrade.getStatus());
        assertEquals(InvoiceStatus.VOID.name(), pastDueUpgrade.getStatus());
        verify(subscriptionScheduledChangeRepository).cancelAllScheduledChanges(free);
        // The Stripe copies are voided off these events; without them the hosted invoices stay payable.
        List<UUID> voided = applicationEvents.stream(com.tansoflow.tansocore.model.event.service.InvoiceVoidedEvent.class)
                .map(com.tansoflow.tansocore.model.event.service.InvoiceVoidedEvent::invoiceId)
                .toList();
        assertTrue(voided.contains(leftoverUpgrade.getId()));
        assertTrue(voided.contains(pastDueUpgrade.getId()));
    }

    // Cancelling a subscription voided its invoices in Tanso only, so a hosted Stripe copy stayed payable.
    @Test
    void voidingOutstandingInvoicesTellsStripeToVoidItsCopies() {
        Account account = new Account();
        account.setId(UUID.randomUUID());
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setAccount(account);

        Subscription subscription = subscriptionOn(customer, account, "starter", new BigDecimal("149.00"), true);
        Invoice due = new Invoice();
        due.setId(UUID.randomUUID());
        due.setSubscription(subscription);
        due.setStatus(InvoiceStatus.DUE.name());
        when(invoiceRepository.findVoidableInvoicesBySubscription(eq(subscription), any())).thenReturn(List.of(due));

        invoiceService.voidOutstandingInvoicesForSubscription(subscription);

        assertEquals(InvoiceStatus.VOID.name(), due.getStatus());
        assertTrue(applicationEvents.stream(com.tansoflow.tansocore.model.event.service.InvoiceVoidedEvent.class)
                .anyMatch(e -> e.invoiceId().equals(due.getId()) && e.accountId().equals(account.getId())));
    }
}
