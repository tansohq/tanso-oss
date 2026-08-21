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

    @InjectMocks
    private SubscriptionServiceImpl subscriptionService;

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
}
