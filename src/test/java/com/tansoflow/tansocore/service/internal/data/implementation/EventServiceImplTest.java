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
package com.tansoflow.tansocore.service.internal.data.implementation;

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.Event;
import com.tansoflow.tansocore.entity.Feature;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.entity.PlanFeatureRule;
import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.integration.stripe.StripeSyncService;
import com.tansoflow.tansocore.mapper.event.EventMapper;
import com.tansoflow.tansocore.model.event.events.EventDto;
import com.tansoflow.tansocore.model.event.events.type.CostUnit;
import com.tansoflow.tansocore.model.event.events.type.EventType;
import com.tansoflow.tansocore.model.exception.CreditLimitExceededException;
import com.tansoflow.tansocore.property.AppProperty;
import com.tansoflow.tansocore.repository.AccountRepository;
import com.tansoflow.tansocore.repository.AccountSettingRepository;
import com.tansoflow.tansocore.repository.CreditPoolSubscriptionRepository;
import com.tansoflow.tansocore.repository.CustomerRepository;
import com.tansoflow.tansocore.repository.EntitlementRepository;
import com.tansoflow.tansocore.repository.EventRepository;
import com.tansoflow.tansocore.repository.FeatureRepository;
import com.tansoflow.tansocore.repository.InvoiceRepository;
import com.tansoflow.tansocore.repository.PlanFeatureRuleRepository;
import com.tansoflow.tansocore.repository.SubscriptionRepository;
import com.tansoflow.tansocore.service.client.ClientEntitlementService;
import com.tansoflow.tansocore.service.internal.monetization.CreditService;
import com.tansoflow.tansocore.util.ModelPricingResolver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class EventServiceImplTest {

    @Autowired
    private EventServiceImpl eventService;

    @MockitoBean
    private EventRepository eventRepository;

    @MockitoBean
    private AccountRepository accountRepository;

    @MockitoBean
    private CustomerRepository customerRepository;

    @MockitoBean
    private EventMapper eventMapper;

    @MockitoBean
    private FeatureRepository featureRepository;

    @MockitoBean
    private SubscriptionRepository subscriptionRepository;

    @MockitoBean
    private PlanFeatureRuleRepository planFeatureRuleRepository;

    @MockitoBean
    private AccountSettingRepository accountSettingRepository;

    @MockitoBean
    private StripeSyncService stripeSyncService;

    @MockitoBean
    private com.tansoflow.tansocore.integration.stripe.StripeWebhook stripeWebhook;

    @MockitoBean
    private CreditService creditService;

    @MockitoBean
    private CreditPoolSubscriptionRepository creditPoolSubscriptionRepository;

    @MockitoBean
    private AppProperty appProperty;

    @MockitoBean
    private ClientEntitlementService clientEntitlementService;

    @MockitoBean
    private EntitlementRepository entitlementRepository;

    @MockitoBean
    private ModelPricingResolver modelPricingResolver;

    @MockitoBean
    private InvoiceRepository invoiceRepository;

    @Test
    void testCreateEvent_Success() {
        UUID accountId = UUID.randomUUID();
        EventDto eventDto = new EventDto();
        eventDto.setAccountId(accountId);
        eventDto.setEventName("test.event");

        Account account = new Account();
        account.setId(accountId);

        Event eventEntity = new Event();
        eventEntity.setEventName("test.event");

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(eventMapper.eventDtoToEventEntity(eventDto)).thenReturn(eventEntity);

        eventService.createEvent(eventDto);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).saveAndFlush(eventCaptor.capture());

        Event savedEvent = eventCaptor.getValue();
        assertEquals(account, savedEvent.getAccount());
    }

    @Test
    void testCreateEvent_AccountNotFound() {
        UUID accountId = UUID.randomUUID();
        EventDto eventDto = new EventDto();
        eventDto.setAccountId(accountId);

        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> eventService.createEvent(eventDto));
    }

    @Test
    void testCreateEvent_CalculateCost_Success() {
        UUID accountId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        EventDto eventDto = new EventDto();
        eventDto.setAccountId(accountId);
        eventDto.setCustomerId(customerId);
        eventDto.setEventName("ai_tokens");
        eventDto.setFeatureKey("ai_tokens");
        eventDto.setUsageUnits(new BigDecimal("1000"));

        Account account = new Account();
        account.setId(accountId);

        Feature feature = new Feature();
        feature.setId(featureId);
        feature.setKey("ai_tokens");

        Plan plan = new Plan();
        plan.setId(planId);

        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setAccount(account);
        subscription.setPlan(plan);
        subscription.setIsActive(true);
        Customer customer = new Customer();
        customer.setId(customerId);
        customer.setAccount(account);
        subscription.setCustomer(customer);

        PlanFeatureRule rule = new PlanFeatureRule();
        rule.setId(UUID.randomUUID());
        rule.setValue(Map.of("cost_rate", 0.00005));

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(featureRepository.findByKeyAndAccountId("ai_tokens", accountId)).thenReturn(Optional.of(feature));
        when(customerRepository.existsByIdAndAccountId(customerId, accountId)).thenReturn(true);
        when(featureRepository.findByIdAndAccount(featureId, account)).thenReturn(Optional.of(feature));
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customerId)).thenReturn(List.of(subscription));
        when(planFeatureRuleRepository.findPlanFeatureRuleByPlan_IdAndFeature_Id(planId, featureId)).thenReturn(rule);

        // Mock mapper to correctly convert String to Enum for entity verification
        when(eventMapper.eventDtoToEventEntity(any(EventDto.class))).thenAnswer(invocation -> {
            EventDto dto = invocation.getArgument(0);
            Event entity = new Event();
            entity.setUsageUnitType(dto.getUsageUnitType());
            if (dto.getCostUnit() != null) {
                entity.setCostUnit(CostUnit.valueOf(dto.getCostUnit()));
            }
            entity.setCostAmount(dto.getCostAmount());
            entity.setRevenueAmount(dto.getRevenueAmount());
            if (dto.getRevenueUnit() != null) {
                entity.setRevenueUnit(CostUnit.valueOf(dto.getRevenueUnit()));
            }
            return entity;
        });

        eventService.createEvent(eventDto);

        // Verify native ID resolution
        assertTrue(eventDto.getCustomerIsNative());
        assertTrue(eventDto.getFeatureIsNative());
        // Verify resolved fields from rule — revenue fallback unit is CURRENCY
        assertEquals("CURRENCY", eventDto.getRevenueUnit());
        assertEquals(rule.getId().toString(), eventDto.getContext().get("sys_applied_rule_id"));
        verify(eventRepository).saveAndFlush(any(Event.class));
    }

    @Test
    void testCreateEvent_ResolveFromRule_Success() {
        UUID accountId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        EventDto eventDto = new EventDto();
        eventDto.setAccountId(accountId);
        eventDto.setCustomerId(customerId);
        eventDto.setEventName("ai_tokens");
        eventDto.setFeatureKey("ai_tokens");
        eventDto.setUsageUnits(new BigDecimal("1000"));

        Account account = new Account();
        account.setId(accountId);

        Feature feature = new Feature();
        feature.setId(featureId);
        feature.setKey("ai_tokens");

        Plan plan = new Plan();
        plan.setId(planId);

        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setAccount(account);
        subscription.setPlan(plan);
        subscription.setIsActive(true);
        Customer customer = new Customer();
        customer.setId(customerId);
        customer.setAccount(account);
        subscription.setCustomer(customer);

        PlanFeatureRule rule = new PlanFeatureRule();
        rule.setId(UUID.randomUUID());
        rule.setValue(Map.of(
                "cost_rate", 0.00005,
                "usage_unit_type", "tokens",
                "cost_unit", "CREDITS"
        ));

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(featureRepository.findByKeyAndAccountId("ai_tokens", accountId)).thenReturn(Optional.of(feature));
        when(customerRepository.existsByIdAndAccountId(customerId, accountId)).thenReturn(true);
        when(featureRepository.findByIdAndAccount(featureId, account)).thenReturn(Optional.of(feature));
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customerId)).thenReturn(List.of(subscription));
        when(planFeatureRuleRepository.findPlanFeatureRuleByPlan_IdAndFeature_Id(planId, featureId)).thenReturn(rule);

        // Mock mapper to correctly convert String to Enum for entity verification
        when(eventMapper.eventDtoToEventEntity(any(EventDto.class))).thenAnswer(invocation -> {
            EventDto dto = invocation.getArgument(0);
            Event entity = new Event();
            entity.setUsageUnitType(dto.getUsageUnitType());
            if (dto.getCostUnit() != null) {
                entity.setCostUnit(CostUnit.valueOf(dto.getCostUnit()));
            }
            entity.setCostAmount(dto.getCostAmount());
            entity.setRevenueAmount(dto.getRevenueAmount());
            if (dto.getRevenueUnit() != null) {
                entity.setRevenueUnit(CostUnit.valueOf(dto.getRevenueUnit()));
            }
            return entity;
        });

        eventService.createEvent(eventDto);

        // Verify resolved fields from rule
        assertEquals("tokens", eventDto.getUsageUnitType());
        assertEquals("CREDITS", eventDto.getRevenueUnit());

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).saveAndFlush(eventCaptor.capture());
        Event savedEvent = eventCaptor.getValue();

        assertEquals("tokens", savedEvent.getUsageUnitType());
        assertEquals(CostUnit.CREDITS, savedEvent.getRevenueUnit());

        // Verify calculated revenue: 1000 * 0.00005 = 0.05 (from PricingModel, no CostModel)
        assertEquals(new BigDecimal("0.05"), eventDto.getRevenueAmount());
        assertEquals(new BigDecimal("0.05"), savedEvent.getRevenueAmount());
        // No CostModel configured, so costAmount should be null
        assertEquals(null, eventDto.getCostAmount());
        // No CostModel → no sys_cost_model or sys_cost_params in context
        assertFalse(eventDto.getContext().containsKey("sys_cost_model"));
        assertFalse(eventDto.getContext().containsKey("sys_cost_params"));
    }

    @Test
    void testCreateEvent_CalculateCost_ExplicitCostModel() {
        UUID accountId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        EventDto eventDto = new EventDto();
        eventDto.setAccountId(accountId);
        eventDto.setCustomerId(customerId);
        eventDto.setEventName("ai_tokens");
        eventDto.setFeatureKey("ai_tokens");
        eventDto.setUsageUnits(new BigDecimal("1000"));

        Account account = new Account();
        account.setId(accountId);

        Feature feature = new Feature();
        feature.setId(featureId);
        feature.setKey("ai_tokens");

        Plan plan = new Plan();
        plan.setId(planId);

        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setAccount(account);
        subscription.setPlan(plan);
        subscription.setIsActive(true);
        Customer customer = new Customer();
        customer.setId(customerId);
        customer.setAccount(account);
        subscription.setCustomer(customer);

        // Pricing model is $0.10, but Cost model is $0.03
        PlanFeatureRule rule = new PlanFeatureRule();
        rule.setId(UUID.randomUUID());
        rule.setValue(Map.of(
                "model", "usage",
                "price_per_unit", 0.10,
                "usage_unit_type", "tokens",
                "cost_model", "simple",
                "cost_per_unit", 0.03,
                "cost_unit", "CURRENCY"
        ));

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(featureRepository.findByKeyAndAccountId("ai_tokens", accountId)).thenReturn(Optional.of(feature));
        when(customerRepository.existsByIdAndAccountId(customerId, accountId)).thenReturn(true);
        when(featureRepository.findByIdAndAccount(featureId, account)).thenReturn(Optional.of(feature));
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customerId)).thenReturn(List.of(subscription));
        when(planFeatureRuleRepository.findPlanFeatureRuleByPlan_IdAndFeature_Id(planId, featureId)).thenReturn(rule);

        when(eventMapper.eventDtoToEventEntity(any(EventDto.class))).thenAnswer(invocation -> {
            EventDto dto = invocation.getArgument(0);
            Event entity = new Event();
            entity.setUsageUnitType(dto.getUsageUnitType());
            if (dto.getCostUnit() != null) {
                entity.setCostUnit(CostUnit.valueOf(dto.getCostUnit()));
            }
            entity.setCostAmount(dto.getCostAmount());
            entity.setRevenueAmount(dto.getRevenueAmount());
            if (dto.getRevenueUnit() != null) {
                entity.setRevenueUnit(CostUnit.valueOf(dto.getRevenueUnit()));
            }
            return entity;
        });

        eventService.createEvent(eventDto);

        // Verify COGS from CostModel: 1000 * 0.03 = 30.00
        assertEquals(0, new BigDecimal("30.00").compareTo(eventDto.getCostAmount()));
        assertEquals("CURRENCY", eventDto.getCostUnit());
        // Verify revenue from PricingModel: 1000 * 0.10 = 100.00
        assertEquals(new BigDecimal("100.00"), eventDto.getRevenueAmount());
        assertEquals("CURRENCY", eventDto.getRevenueUnit());

        // Verify sys_cost_model and sys_cost_params context tracking
        assertEquals("simple", eventDto.getContext().get("sys_cost_model"));
        @SuppressWarnings("unchecked")
        Map<String, Object> costParams = (Map<String, Object>) eventDto.getContext().get("sys_cost_params");
        assertEquals(new BigDecimal("0.03"), costParams.get("cost_per_unit"));
        assertEquals("CURRENCY", costParams.get("cost_unit"));
    }

    @Test
    void testExistsByEventIdempotencyKey() {
        UUID accountId = UUID.randomUUID();
        String key = "key123";

        when(eventRepository.existsByAccountIdAndEventIdempotencyKey(accountId, key)).thenReturn(true);

        boolean exists = eventService.existsByEventIdempotencyKey(accountId, key);

        assertTrue(exists);
        verify(eventRepository).existsByAccountIdAndEventIdempotencyKey(accountId, key);
    }

    @Test
    void testCreateEvent_ResolveCustomerReferenceId() {
        UUID accountId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String referenceId = "user_123";

        EventDto eventDto = new EventDto();
        eventDto.setAccountId(accountId);
        eventDto.setCustomerReferenceId(referenceId);
        eventDto.setEventName("test.event");

        Account account = new Account();
        account.setId(accountId);

        Customer customer = new Customer();
        customer.setId(customerId);

        Event eventEntity = new Event();

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(customerRepository.getCustomerByReferenceIdAndAccountId(referenceId, accountId)).thenReturn(Optional.of(customer));
        when(eventMapper.eventDtoToEventEntity(any(EventDto.class))).thenReturn(eventEntity);

        eventService.createEvent(eventDto);

        assertEquals(customerId, eventDto.getCustomerId());
        verify(customerRepository).getCustomerByReferenceIdAndAccountId(referenceId, accountId);
        verify(eventRepository).saveAndFlush(any(Event.class));
    }

    @Test
    void testCreateEvent_ZeroUsageEntitlementChecked_NoStripeForwarding() throws Exception {
        // Setup: ENTITLEMENT_CHECKED event with zero usage on a STRIPE_INTEGRATION account
        UUID accountId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();

        EventDto eventDto = new EventDto();
        eventDto.setAccountId(accountId);
        eventDto.setCustomerId(customerId);
        eventDto.setFeatureId(featureId);
        eventDto.setEventName("test.feature");
        eventDto.setEventType(EventType.ENTITLEMENT_CHECKED);
        eventDto.setUsageUnits(BigDecimal.ZERO);
        eventDto.setOccurredAt(Instant.now());
        eventDto.setEventIdempotencyKey(UUID.randomUUID().toString());
        eventDto.setContext(new HashMap<>(Map.of("sys_simulation", true)));

        Account account = new Account();
        account.setId(accountId);

        Event eventEntity = new Event();

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(eventMapper.eventDtoToEventEntity(any(EventDto.class))).thenReturn(eventEntity);

        eventService.createEvent(eventDto);

        verify(eventRepository).saveAndFlush(any(Event.class));
        // Zero usage must prevent Stripe meter forwarding — the guard returns early
        verify(stripeSyncService, never()).forwardUsageToStripeMeter(any(), any(), any(), any(), any());
    }

    @Test
    void testCreateEvent_ZeroUsageEntitlementChecked_NoCreditDeduction() {
        // Setup: ENTITLEMENT_CHECKED event with zero usage, subscription and feature with credit model
        UUID accountId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        EventDto eventDto = new EventDto();
        eventDto.setAccountId(accountId);
        eventDto.setCustomerId(customerId);
        eventDto.setFeatureId(featureId);
        eventDto.setSubscriptionId(subscriptionId);
        eventDto.setEventName("test.feature");
        eventDto.setEventType(EventType.ENTITLEMENT_CHECKED);
        eventDto.setUsageUnits(BigDecimal.ZERO);
        eventDto.setOccurredAt(Instant.now());
        eventDto.setEventIdempotencyKey(UUID.randomUUID().toString());
        eventDto.setContext(new HashMap<>(Map.of("sys_simulation", true)));

        Account account = new Account();
        account.setId(accountId);

        Plan plan = new Plan();
        plan.setId(planId);

        Subscription subscription = new Subscription();
        subscription.setId(subscriptionId);
        subscription.setPlan(plan);
        subscription.setIsActive(true);
        subscription.setAccount(account);
        Customer customer = new Customer();
        customer.setId(customerId);
        customer.setAccount(account);
        subscription.setCustomer(customer);

        // Rule with a credit model so credit deduction path would normally fire
        com.tansoflow.tansocore.entity.CreditModel creditModel = new com.tansoflow.tansocore.entity.CreditModel();
        creditModel.setDenomination("tokens");

        PlanFeatureRule rule = new PlanFeatureRule();
        rule.setId(UUID.randomUUID());
        rule.setCreditModel(creditModel);

        Event eventEntity = new Event();

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(subscriptionRepository.findSubscriptionByUuidAndAccountId(subscriptionId, accountId)).thenReturn(subscription);
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customerId)).thenReturn(List.of(subscription));
        when(planFeatureRuleRepository.findPlanFeatureRuleByPlan_IdAndFeature_Id(planId, featureId)).thenReturn(rule);
        when(eventMapper.eventDtoToEventEntity(any(EventDto.class))).thenReturn(eventEntity);

        eventService.createEvent(eventDto);

        verify(eventRepository).saveAndFlush(any(Event.class));
        // Zero usage must prevent credit deduction — the guard returns early
        verify(creditService, never()).deductCredits(any(), any());
    }

    @Test
    void testCreateEvent_DepletedHardLimit_RejectsBeforePersistence() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        EventDto eventDto = new EventDto();
        eventDto.setAccountId(accountId);
        eventDto.setCustomerId(customerId);
        eventDto.setFeatureId(featureId);
        eventDto.setSubscriptionId(subscriptionId);
        eventDto.setEventName("test.feature");
        eventDto.setUsageUnits(BigDecimal.TEN);

        Account account = new Account();
        account.setId(accountId);

        Feature feature = new Feature();
        feature.setId(featureId);

        Plan plan = new Plan();
        plan.setId(planId);

        Subscription subscription = new Subscription();
        subscription.setId(subscriptionId);
        subscription.setAccount(account);
        subscription.setPlan(plan);
        subscription.setIsActive(true);
        Customer customer = new Customer();
        customer.setId(customerId);
        customer.setAccount(account);
        subscription.setCustomer(customer);

        com.tansoflow.tansocore.entity.CreditModel creditModel = new com.tansoflow.tansocore.entity.CreditModel();
        creditModel.setDenomination("tokens");

        PlanFeatureRule rule = new PlanFeatureRule();
        rule.setId(UUID.randomUUID());
        rule.setCreditModel(creditModel);
        rule.setValue(Map.of("model", "usage", "price_per_unit", 1));

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(customerRepository.existsByIdAndAccountId(customerId, accountId)).thenReturn(true);
        when(featureRepository.findByIdAndAccount(featureId, account)).thenReturn(Optional.of(feature));
        when(subscriptionRepository.findSubscriptionByUuidAndAccountId(subscriptionId, accountId)).thenReturn(subscription);
        when(planFeatureRuleRepository.findPlanFeatureRuleByPlan_IdAndFeature_Id(planId, featureId)).thenReturn(rule);
        when(creditService.checkHardLimitForSubscription(subscriptionId, accountId, "tokens", BigDecimal.TEN))
                .thenReturn(false);

        assertThrows(CreditLimitExceededException.class, () -> eventService.createEvent(eventDto));

        verify(eventRepository, never()).saveAndFlush(any(Event.class));
        verify(creditService, never()).deductCredits(any(), any());
        verify(stripeSyncService, never()).forwardUsageToStripeMeter(any(), any(), any(), any(), any());
    }

    @Test
    void testCreateEvent_SubscriptionFromAnotherCustomer_UsesScopedCustomerSubscription() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID requestedSubscriptionId = UUID.randomUUID();
        UUID resolvedSubscriptionId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();

        Account account = new Account();
        account.setId(accountId);

        Customer eventCustomer = new Customer();
        eventCustomer.setId(customerId);
        eventCustomer.setAccount(account);

        Customer otherCustomer = new Customer();
        otherCustomer.setId(UUID.randomUUID());
        otherCustomer.setAccount(account);

        Plan requestedPlan = new Plan();
        requestedPlan.setId(UUID.randomUUID());
        Plan resolvedPlan = new Plan();
        resolvedPlan.setId(UUID.randomUUID());

        Subscription requestedSubscription = new Subscription();
        requestedSubscription.setId(requestedSubscriptionId);
        requestedSubscription.setAccount(account);
        requestedSubscription.setCustomer(otherCustomer);
        requestedSubscription.setPlan(requestedPlan);
        requestedSubscription.setIsActive(true);

        Subscription resolvedSubscription = new Subscription();
        resolvedSubscription.setId(resolvedSubscriptionId);
        resolvedSubscription.setAccount(account);
        resolvedSubscription.setCustomer(eventCustomer);
        resolvedSubscription.setPlan(resolvedPlan);
        resolvedSubscription.setIsActive(true);

        Feature feature = new Feature();
        feature.setId(featureId);

        PlanFeatureRule rule = new PlanFeatureRule();
        rule.setId(UUID.randomUUID());
        rule.setValue(Map.of("model", "usage", "price_per_unit", 1));

        EventDto eventDto = new EventDto();
        eventDto.setAccountId(accountId);
        eventDto.setCustomerId(customerId);
        eventDto.setFeatureId(featureId);
        eventDto.setSubscriptionId(requestedSubscriptionId);
        eventDto.setEventName("test.feature");
        eventDto.setUsageUnits(BigDecimal.ONE);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(customerRepository.existsByIdAndAccountId(customerId, accountId)).thenReturn(true);
        when(featureRepository.findByIdAndAccount(featureId, account)).thenReturn(Optional.of(feature));
        when(subscriptionRepository.findSubscriptionByUuidAndAccountId(requestedSubscriptionId, accountId))
                .thenReturn(requestedSubscription);
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customerId))
                .thenReturn(List.of(resolvedSubscription));
        when(planFeatureRuleRepository.findPlanFeatureRuleByPlan_IdAndFeature_Id(
                resolvedPlan.getId(), featureId)).thenReturn(rule);
        when(eventMapper.eventDtoToEventEntity(eventDto)).thenReturn(new Event());

        eventService.createEvent(eventDto);

        assertEquals(resolvedSubscriptionId, eventDto.getSubscriptionId());
        assertTrue(eventDto.getSubscriptionIsNative());
        verify(planFeatureRuleRepository, never()).findPlanFeatureRuleByPlan_IdAndFeature_Id(
                requestedPlan.getId(), featureId);
        verify(eventRepository).saveAndFlush(any(Event.class));
    }

    @Test
    void testCreateEvent_NonNativeCustomer_BillingSkipped() {
        UUID accountId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();

        EventDto eventDto = new EventDto();
        eventDto.setAccountId(accountId);
        eventDto.setCustomerId(customerId);
        eventDto.setFeatureId(featureId);
        eventDto.setEventName("ai_tokens");
        eventDto.setUsageUnits(new BigDecimal("1000"));

        Account account = new Account();
        account.setId(accountId);

        Feature feature = new Feature();
        feature.setId(featureId);

        Event eventEntity = new Event();

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        // Customer does NOT belong to this account
        when(customerRepository.existsByIdAndAccountId(customerId, accountId)).thenReturn(false);
        when(featureRepository.findByIdAndAccount(featureId, account)).thenReturn(Optional.of(feature));
        when(eventMapper.eventDtoToEventEntity(any(EventDto.class))).thenReturn(eventEntity);

        eventService.createEvent(eventDto);

        // Native flags: customer is external, feature is native
        assertFalse(eventDto.getCustomerIsNative());
        assertTrue(eventDto.getFeatureIsNative());
        // Billing should be skipped — no cost calculation
        assertEquals(null, eventDto.getCostAmount());
        assertEquals(null, eventDto.getRevenueAmount());
        // Event is still saved
        verify(eventRepository).saveAndFlush(any(Event.class));
    }

    @Test
    void testCreateEvent_NonNativeFeature_BillingSkipped() {
        UUID accountId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();

        EventDto eventDto = new EventDto();
        eventDto.setAccountId(accountId);
        eventDto.setCustomerId(customerId);
        eventDto.setFeatureId(featureId);
        eventDto.setEventName("ai_tokens");
        eventDto.setUsageUnits(new BigDecimal("1000"));

        Account account = new Account();
        account.setId(accountId);

        Event eventEntity = new Event();

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(customerRepository.existsByIdAndAccountId(customerId, accountId)).thenReturn(true);
        // Feature does NOT belong to this account
        when(featureRepository.findByIdAndAccount(featureId, account)).thenReturn(Optional.empty());
        when(eventMapper.eventDtoToEventEntity(any(EventDto.class))).thenReturn(eventEntity);

        eventService.createEvent(eventDto);

        assertTrue(eventDto.getCustomerIsNative());
        assertFalse(eventDto.getFeatureIsNative());
        // Billing should be skipped
        assertEquals(null, eventDto.getCostAmount());
        verify(eventRepository).saveAndFlush(any(Event.class));
    }

    @Test
    void testCreateEvent_NoCustomerId_BillingSkipped() {
        UUID accountId = UUID.randomUUID();

        EventDto eventDto = new EventDto();
        eventDto.setAccountId(accountId);
        eventDto.setEventName("anonymous.event");
        eventDto.setUsageUnits(new BigDecimal("100"));

        Account account = new Account();
        account.setId(accountId);

        Event eventEntity = new Event();

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(eventMapper.eventDtoToEventEntity(any(EventDto.class))).thenReturn(eventEntity);

        eventService.createEvent(eventDto);

        // No customer or feature → native flags stay null → billing skipped
        assertEquals(null, eventDto.getCustomerIsNative());
        assertEquals(null, eventDto.getFeatureIsNative());
        assertEquals(null, eventDto.getCostAmount());
        verify(eventRepository).saveAndFlush(any(Event.class));
    }
}
