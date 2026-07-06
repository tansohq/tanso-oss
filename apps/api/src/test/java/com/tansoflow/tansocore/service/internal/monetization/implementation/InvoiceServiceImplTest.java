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
class InvoiceServiceImplTest {

    @Autowired
    private InvoiceServiceImpl invoiceService;

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

    @Test
    void testCreateNewInvoice_ExcludesCreditCoveredUsage() {
        // event1's usage was fully paid by prepaid credits at ingestion; only event2 should be billed.
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
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("credit_deducted", new BigDecimal("100")); // all 100 units covered by credits
        event1.setContext(ctx);

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
                subscription, LocalDate.now(), InvoiceStatus.DUE, start, end,
                BigDecimal.ZERO, "USD", InvoiceType.REGULAR);

        Invoice result = invoiceService.createNewInvoice(params);

        // 1.50 total usage - 1.00 covered by credits = 0.50 billed (not 1.50 double-charge)
        assertEquals(0, new BigDecimal("0.50").compareTo(result.getAmount()));
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
}