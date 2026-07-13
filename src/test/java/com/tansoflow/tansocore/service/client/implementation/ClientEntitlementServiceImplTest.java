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
package com.tansoflow.tansocore.service.client.implementation;

import com.tansoflow.tansocore.entity.CreditModel;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.Entitlement;
import com.tansoflow.tansocore.entity.Feature;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.entity.PlanFeatureRule;
import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.model.credit.CreditPoolDto;
import com.tansoflow.tansocore.model.entitlement.api.EntitlementEvaluationRequest;
import com.tansoflow.tansocore.model.entitlement.api.UsageContext;
import com.tansoflow.tansocore.model.entitlement.response.CustomerEntitlementsResponse;
import com.tansoflow.tansocore.model.entitlement.response.EntitlementResponse;
import com.tansoflow.tansocore.model.event.events.EventDto;
import com.tansoflow.tansocore.repository.EntitlementRepository;
import com.tansoflow.tansocore.repository.EventRepository;
import com.tansoflow.tansocore.repository.FeatureRepository;
import com.tansoflow.tansocore.repository.PlanFeatureRuleRepository;
import com.tansoflow.tansocore.repository.SubscriptionRepository;
import com.tansoflow.tansocore.service.internal.account.CustomerService;
import com.tansoflow.tansocore.service.internal.data.EventService;
import com.tansoflow.tansocore.service.internal.monetization.CreditService;
import com.tansoflow.tansocore.service.internal.monetization.EntitlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientEntitlementServiceImplTest {

    @Mock
    private EntitlementService entitlementService;
    @Mock
    private CreditService creditService;
    @Mock
    private CustomerService customerService;
    @Mock
    private EventService eventService;
    @Mock
    private FeatureRepository featureRepository;
    @Mock
    private EntitlementRepository entitlementRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private PlanFeatureRuleRepository planFeatureRuleRepository;
    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private ClientEntitlementServiceImpl clientEntitlementService;

    private String accountUuid;
    private String referenceCustomerId;
    private String featureKey;
    private Customer customer;
    private Feature feature;
    private Entitlement entitlement;

    @BeforeEach
    void setUp() {
        accountUuid = UUID.randomUUID().toString();
        referenceCustomerId = "cust_123";
        featureKey = "test.feature";

        customer = new Customer();
        customer.setId(UUID.randomUUID());

        feature = new Feature();
        feature.setId(UUID.randomUUID());
        feature.setKey(featureKey);

        entitlement = new Entitlement();
        entitlement.setId(UUID.randomUUID());
        entitlement.setFeatureKey(featureKey);
        entitlement.setCustomer(customer);
    }

    @Test
    void testCheckEntitlement_StoresIdsInEvent() {
        // Setup
        when(customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(referenceCustomerId, accountUuid))
                .thenReturn(customer);
        when(entitlementService.isEntitled(featureKey, customer)).thenReturn(true);
        when(featureRepository.findByKeyAndAccountId(eq(featureKey), any(UUID.class)))
                .thenReturn(Optional.of(feature));
        when(entitlementRepository.findFirstByCustomerAndFeatureKeyAndRevokedAtIsNullOrderByCreatedAtDesc(customer, featureKey))
                .thenReturn(Optional.of(entitlement));

        // Execute
        EntitlementResponse response = clientEntitlementService.checkEntitlement(referenceCustomerId, accountUuid, featureKey);

        // Verify
        assertTrue(response.isAllowed());

        ArgumentCaptor<EventDto> eventCaptor = ArgumentCaptor.forClass(EventDto.class);
        verify(eventService).createEvent(eventCaptor.capture());

        EventDto eventDto = eventCaptor.getValue();
        assertEquals(entitlement.getId(), eventDto.getEntitlementId());
        assertEquals(feature.getId(), eventDto.getFeatureId());
        Map<String, Object> properties = eventDto.getProperties();
        assertEquals(featureKey, properties.get("featureKey"));
        assertEquals(true, properties.get("isEntitled"));
    }

    @Test
    void testCheckAndTrackEntitlement_StoresIdsInEvent() {
        // Setup
        EntitlementEvaluationRequest request = new EntitlementEvaluationRequest();
        request.setCustomerReferenceId(referenceCustomerId);
        request.setFeatureKey(featureKey);

        when(customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(referenceCustomerId, accountUuid))
                .thenReturn(customer);
        when(entitlementService.isEntitled(featureKey, customer)).thenReturn(true);
        when(featureRepository.findByKeyAndAccountId(eq(featureKey), any(UUID.class)))
                .thenReturn(Optional.of(feature));
        when(entitlementRepository.findFirstByCustomerAndFeatureKeyAndRevokedAtIsNullOrderByCreatedAtDesc(customer, featureKey))
                .thenReturn(Optional.of(entitlement));

        // Execute
        EntitlementResponse response = clientEntitlementService.evaluateEntitlement(accountUuid, request);

        // Verify
        assertTrue(response.isAllowed());

        ArgumentCaptor<EventDto> eventCaptor = ArgumentCaptor.forClass(EventDto.class);
        verify(eventService).createEvent(eventCaptor.capture());

        EventDto eventDto = eventCaptor.getValue();
        assertEquals(entitlement.getId(), eventDto.getEntitlementId());
        assertEquals(feature.getId(), eventDto.getFeatureId());
        // Verify event records zero usage (dry-run)
        assertEquals(0, BigDecimal.ZERO.compareTo(eventDto.getUsageUnits()));
    }

    @Test
    void testCheckAndTrackEntitlement_SimulationEvent_HasZeroUsageAndNoCost() {
        // Setup: usage context with usageUnits=50
        Subscription subscription = createActiveSubscription();
        PlanFeatureRule rule = createRuleWithMaxUsage(subscription, new BigDecimal("100"));

        EntitlementEvaluationRequest request = new EntitlementEvaluationRequest();
        request.setCustomerReferenceId(referenceCustomerId);
        request.setFeatureKey(featureKey);
        UsageContext usage = new UsageContext();
        usage.setUsageUnits(new BigDecimal("50"));
        request.setUsage(usage);

        when(customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(referenceCustomerId, accountUuid))
                .thenReturn(customer);
        when(entitlementService.isEntitled(featureKey, customer)).thenReturn(true);
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId()))
                .thenReturn(List.of(subscription));
        when(featureRepository.findByKeyAndAccountId(eq(featureKey), any(UUID.class)))
                .thenReturn(Optional.of(feature));
        when(planFeatureRuleRepository.findPlanFeatureRuleByPlan_IdAndFeature_Id(
                subscription.getPlan().getId(), feature.getId()))
                .thenReturn(rule);
        when(eventRepository.sumUsageUnitsByCustomerAndFeatureIdSince(
                eq(customer.getId()), eq(feature.getId()), isNull()))
                .thenReturn(new BigDecimal("20"));
        when(entitlementRepository.findFirstByCustomerAndFeatureKeyAndRevokedAtIsNullOrderByCreatedAtDesc(customer, featureKey))
                .thenReturn(Optional.of(entitlement));

        // Execute
        clientEntitlementService.evaluateEntitlement(accountUuid, request);

        // Verify the emitted event is billing-inert
        ArgumentCaptor<EventDto> eventCaptor = ArgumentCaptor.forClass(EventDto.class);
        verify(eventService).createEvent(eventCaptor.capture());
        EventDto eventDto = eventCaptor.getValue();

        // usageUnits must be ZERO — simulation events must not contribute to cumulative billing
        assertEquals(0, BigDecimal.ZERO.compareTo(eventDto.getUsageUnits()));
        // costAmount and revenueAmount must be null — the request's cost context must not leak into the event
        assertNull(eventDto.getCostAmount());
        assertNull(eventDto.getRevenueAmount());
        // Simulation flag must be set
        assertEquals(true, eventDto.getContext().get("sys_simulation"));
    }

    @Test
    void testCheckEntitlement_NotEntitled_NoEntitlementId() {
        // Setup
        when(customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(referenceCustomerId, accountUuid))
                .thenReturn(customer);
        when(entitlementService.isEntitled(featureKey, customer)).thenReturn(false);
        when(featureRepository.findByKeyAndAccountId(eq(featureKey), any(UUID.class)))
                .thenReturn(Optional.of(feature));

        // Execute
        EntitlementResponse response = clientEntitlementService.checkEntitlement(referenceCustomerId, accountUuid, featureKey);

        // Verify
        assertFalse(response.isAllowed());

        ArgumentCaptor<EventDto> eventCaptor = ArgumentCaptor.forClass(EventDto.class);
        verify(eventService).createEvent(eventCaptor.capture());

        EventDto eventDto = eventCaptor.getValue();
        assertNull(eventDto.getEntitlementId());
        assertEquals(feature.getId(), eventDto.getFeatureId());
        Map<String, Object> properties = eventDto.getProperties();
        assertEquals(false, properties.get("isEntitled"));

        verify(entitlementRepository, never()).findFirstByCustomerAndFeatureKeyAndRevokedAtIsNullOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void testCheckEntitlement_WithUsageLimit_ReturnsUsageInfo() {
        // Setup: maxUsage=100, cumulative=40 → allowed with remaining=60
        Subscription subscription = createActiveSubscription();
        PlanFeatureRule rule = createRuleWithMaxUsage(subscription, new BigDecimal("100"));

        when(customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(referenceCustomerId, accountUuid))
                .thenReturn(customer);
        when(entitlementService.isEntitled(featureKey, customer)).thenReturn(true);
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId()))
                .thenReturn(List.of(subscription));
        when(featureRepository.findByKeyAndAccountId(eq(featureKey), any(UUID.class)))
                .thenReturn(Optional.of(feature));
        when(planFeatureRuleRepository.findPlanFeatureRuleByPlan_IdAndFeature_Id(
                subscription.getPlan().getId(), feature.getId()))
                .thenReturn(rule);
        when(eventRepository.sumUsageUnitsByCustomerAndFeatureIdSince(
                eq(customer.getId()), eq(feature.getId()), isNull()))
                .thenReturn(new BigDecimal("40"));
        when(entitlementRepository.findFirstByCustomerAndFeatureKeyAndRevokedAtIsNullOrderByCreatedAtDesc(customer, featureKey))
                .thenReturn(Optional.of(entitlement));

        // Execute
        EntitlementResponse response = clientEntitlementService.checkEntitlement(referenceCustomerId, accountUuid, featureKey);

        // Verify
        assertTrue(response.isAllowed());
        assertNotNull(response.getUsage());
        assertEquals(0, new BigDecimal("40").compareTo(response.getUsage().getUsed()));
        assertEquals(0, new BigDecimal("100").compareTo(response.getUsage().getLimit()));
        assertEquals(0, new BigDecimal("60").compareTo(response.getUsage().getRemaining()));
    }

    @Test
    void testCheckEntitlement_UsageLimitExceeded_DeniedWithZeroRemaining() {
        // Setup: maxUsage=100, cumulative=100 → denied, remaining=0
        Subscription subscription = createActiveSubscription();
        PlanFeatureRule rule = createRuleWithMaxUsage(subscription, new BigDecimal("100"));

        when(customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(referenceCustomerId, accountUuid))
                .thenReturn(customer);
        when(entitlementService.isEntitled(featureKey, customer)).thenReturn(true);
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId()))
                .thenReturn(List.of(subscription));
        when(featureRepository.findByKeyAndAccountId(eq(featureKey), any(UUID.class)))
                .thenReturn(Optional.of(feature));
        when(planFeatureRuleRepository.findPlanFeatureRuleByPlan_IdAndFeature_Id(
                subscription.getPlan().getId(), feature.getId()))
                .thenReturn(rule);
        when(eventRepository.sumUsageUnitsByCustomerAndFeatureIdSince(
                eq(customer.getId()), eq(feature.getId()), isNull()))
                .thenReturn(new BigDecimal("100"));

        // Execute
        EntitlementResponse response = clientEntitlementService.checkEntitlement(referenceCustomerId, accountUuid, featureKey);

        // Verify
        assertFalse(response.isAllowed());
        assertEquals("Usage limit exceeded", response.getMeta().getReason().getDescription());
        assertNotNull(response.getUsage());
        assertEquals(0, new BigDecimal("100").compareTo(response.getUsage().getUsed()));
        assertEquals(0, new BigDecimal("100").compareTo(response.getUsage().getLimit()));
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getUsage().getRemaining()));
    }

    @Test
    void testCheckEntitlement_NoUsageLimit_UsageIsNull() {
        // Setup: no active subscription → no usage limit → usage field is null
        when(customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(referenceCustomerId, accountUuid))
                .thenReturn(customer);
        when(entitlementService.isEntitled(featureKey, customer)).thenReturn(true);
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId()))
                .thenReturn(List.of());
        when(featureRepository.findByKeyAndAccountId(eq(featureKey), any(UUID.class)))
                .thenReturn(Optional.of(feature));
        when(entitlementRepository.findFirstByCustomerAndFeatureKeyAndRevokedAtIsNullOrderByCreatedAtDesc(customer, featureKey))
                .thenReturn(Optional.of(entitlement));

        // Execute
        EntitlementResponse response = clientEntitlementService.checkEntitlement(referenceCustomerId, accountUuid, featureKey);

        // Verify
        assertTrue(response.isAllowed());
        assertNull(response.getUsage());
    }

    @Test
    void testCheckAndTrackEntitlement_WithUsageLimit_ReturnsSimulation() {
        // Setup: maxUsage=100, cumulative=40, proposed=50 → allowed, projected=90, remaining=10
        Subscription subscription = createActiveSubscription();
        PlanFeatureRule rule = createRuleWithMaxUsage(subscription, new BigDecimal("100"));

        EntitlementEvaluationRequest request = new EntitlementEvaluationRequest();
        request.setCustomerReferenceId(referenceCustomerId);
        request.setFeatureKey(featureKey);
        UsageContext usage = new UsageContext();
        usage.setUsageUnits(new BigDecimal("50"));
        request.setUsage(usage);

        when(customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(referenceCustomerId, accountUuid))
                .thenReturn(customer);
        when(entitlementService.isEntitled(featureKey, customer)).thenReturn(true);
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId()))
                .thenReturn(List.of(subscription));
        when(featureRepository.findByKeyAndAccountId(eq(featureKey), any(UUID.class)))
                .thenReturn(Optional.of(feature));
        when(planFeatureRuleRepository.findPlanFeatureRuleByPlan_IdAndFeature_Id(
                subscription.getPlan().getId(), feature.getId()))
                .thenReturn(rule);
        when(eventRepository.sumUsageUnitsByCustomerAndFeatureIdSince(
                eq(customer.getId()), eq(feature.getId()), isNull()))
                .thenReturn(new BigDecimal("40"));
        when(entitlementRepository.findFirstByCustomerAndFeatureKeyAndRevokedAtIsNullOrderByCreatedAtDesc(customer, featureKey))
                .thenReturn(Optional.of(entitlement));

        // Execute
        EntitlementResponse response = clientEntitlementService.evaluateEntitlement(accountUuid, request);

        // Verify entitlement
        assertTrue(response.isAllowed());
        assertNotNull(response.getUsage());
        assertEquals(0, new BigDecimal("40").compareTo(response.getUsage().getUsed()));
        assertEquals(0, new BigDecimal("100").compareTo(response.getUsage().getLimit()));
        assertEquals(0, new BigDecimal("60").compareTo(response.getUsage().getRemaining()));

        // Verify simulation
        assertNotNull(response.getSimulation());
        assertEquals(0, new BigDecimal("50").compareTo(response.getSimulation().getRequestedUsage()));
        assertEquals(0, new BigDecimal("90").compareTo(response.getSimulation().getProjectedUsage()));
        assertEquals(0, new BigDecimal("10").compareTo(response.getSimulation().getProjectedRemaining()));
        assertFalse(response.getSimulation().isWouldExceedLimit());

        // Verify event records zero usage and has simulation context
        ArgumentCaptor<EventDto> eventCaptor = ArgumentCaptor.forClass(EventDto.class);
        verify(eventService).createEvent(eventCaptor.capture());
        EventDto eventDto = eventCaptor.getValue();
        assertEquals(0, BigDecimal.ZERO.compareTo(eventDto.getUsageUnits()));
        assertEquals(true, eventDto.getContext().get("sys_simulation"));
        assertEquals(new BigDecimal("50"), eventDto.getContext().get("sys_simulated_usage"));
        assertEquals(false, eventDto.getContext().get("sys_simulated_would_exceed"));
    }

    @Test
    void testCheckAndTrackEntitlement_ProposedUsageExceedsLimit_DeniedWithSimulation() {
        // Setup: maxUsage=100, cumulative=40, proposed=70 → denied, projected=110, would exceed
        Subscription subscription = createActiveSubscription();
        PlanFeatureRule rule = createRuleWithMaxUsage(subscription, new BigDecimal("100"));

        EntitlementEvaluationRequest request = new EntitlementEvaluationRequest();
        request.setCustomerReferenceId(referenceCustomerId);
        request.setFeatureKey(featureKey);
        UsageContext usage = new UsageContext();
        usage.setUsageUnits(new BigDecimal("70"));
        request.setUsage(usage);

        when(customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(referenceCustomerId, accountUuid))
                .thenReturn(customer);
        when(entitlementService.isEntitled(featureKey, customer)).thenReturn(true);
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId()))
                .thenReturn(List.of(subscription));
        when(featureRepository.findByKeyAndAccountId(eq(featureKey), any(UUID.class)))
                .thenReturn(Optional.of(feature));
        when(planFeatureRuleRepository.findPlanFeatureRuleByPlan_IdAndFeature_Id(
                subscription.getPlan().getId(), feature.getId()))
                .thenReturn(rule);
        when(eventRepository.sumUsageUnitsByCustomerAndFeatureIdSince(
                eq(customer.getId()), eq(feature.getId()), isNull()))
                .thenReturn(new BigDecimal("40"));

        // Execute
        EntitlementResponse response = clientEntitlementService.evaluateEntitlement(accountUuid, request);

        // Verify denied
        assertFalse(response.isAllowed());
        assertEquals("Proposed usage would exceed limit", response.getMeta().getReason().getDescription());

        // Verify simulation
        assertNotNull(response.getSimulation());
        assertEquals(0, new BigDecimal("70").compareTo(response.getSimulation().getRequestedUsage()));
        assertEquals(0, new BigDecimal("110").compareTo(response.getSimulation().getProjectedUsage()));
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getSimulation().getProjectedRemaining()));
        assertTrue(response.getSimulation().isWouldExceedLimit());

        // Verify event records zero usage and simulation context
        ArgumentCaptor<EventDto> eventCaptor = ArgumentCaptor.forClass(EventDto.class);
        verify(eventService).createEvent(eventCaptor.capture());
        EventDto eventDto = eventCaptor.getValue();
        assertEquals(0, BigDecimal.ZERO.compareTo(eventDto.getUsageUnits()));
        assertEquals(true, eventDto.getContext().get("sys_simulation"));
        assertEquals(true, eventDto.getContext().get("sys_simulated_would_exceed"));
    }

    @Test
    void testCheckAndTrackEntitlement_NoUsageContext_NoSimulation() {
        // Setup: no usage context → behaves like simple check, no simulation in response
        EntitlementEvaluationRequest request = new EntitlementEvaluationRequest();
        request.setCustomerReferenceId(referenceCustomerId);
        request.setFeatureKey(featureKey);
        // No usage context set

        when(customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(referenceCustomerId, accountUuid))
                .thenReturn(customer);
        when(entitlementService.isEntitled(featureKey, customer)).thenReturn(true);
        when(featureRepository.findByKeyAndAccountId(eq(featureKey), any(UUID.class)))
                .thenReturn(Optional.of(feature));
        when(entitlementRepository.findFirstByCustomerAndFeatureKeyAndRevokedAtIsNullOrderByCreatedAtDesc(customer, featureKey))
                .thenReturn(Optional.of(entitlement));

        // Execute
        EntitlementResponse response = clientEntitlementService.evaluateEntitlement(accountUuid, request);

        // Verify
        assertTrue(response.isAllowed());
        assertNull(response.getSimulation());

        // Verify event still records zero usage
        ArgumentCaptor<EventDto> eventCaptor = ArgumentCaptor.forClass(EventDto.class);
        verify(eventService).createEvent(eventCaptor.capture());
        EventDto eventDto = eventCaptor.getValue();
        assertEquals(0, BigDecimal.ZERO.compareTo(eventDto.getUsageUnits()));
        // No simulation context keys
        assertNull(eventDto.getContext().get("sys_simulation"));
    }

    @Test
    void testCheckAndTrackEntitlement_UsageContextButNoUsageLimit_NoSimulation() {
        // Setup: usage context provided but no usage limit on feature → no simulation
        EntitlementEvaluationRequest request = new EntitlementEvaluationRequest();
        request.setCustomerReferenceId(referenceCustomerId);
        request.setFeatureKey(featureKey);
        UsageContext usage = new UsageContext();
        usage.setUsageUnits(new BigDecimal("50"));
        request.setUsage(usage);

        when(customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(referenceCustomerId, accountUuid))
                .thenReturn(customer);
        when(entitlementService.isEntitled(featureKey, customer)).thenReturn(true);
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId()))
                .thenReturn(List.of());
        when(featureRepository.findByKeyAndAccountId(eq(featureKey), any(UUID.class)))
                .thenReturn(Optional.of(feature));
        when(entitlementRepository.findFirstByCustomerAndFeatureKeyAndRevokedAtIsNullOrderByCreatedAtDesc(customer, featureKey))
                .thenReturn(Optional.of(entitlement));

        // Execute
        EntitlementResponse response = clientEntitlementService.evaluateEntitlement(accountUuid, request);

        // Verify
        assertTrue(response.isAllowed());
        assertNull(response.getSimulation());
        assertNull(response.getUsage());
    }

    @Test
    void testCheckEntitlement_WithCreditModel_ReturnsCreditInfo() {
        // Setup: feature has a credit model, customer has matching credit pools
        Subscription subscription = createActiveSubscription();
        CreditModel creditModel = createCreditModel("TOKENS", true);
        PlanFeatureRule rule = createRuleWithCreditModel(subscription, creditModel);

        CreditPoolDto pool = new CreditPoolDto();
        pool.setDenomination("TOKENS");
        pool.setBalance(new BigDecimal("500"));
        pool.setTotalGranted(new BigDecimal("1000"));
        pool.setTotalConsumed(new BigDecimal("500"));

        when(customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(referenceCustomerId, accountUuid))
                .thenReturn(customer);
        when(entitlementService.isEntitled(featureKey, customer)).thenReturn(true);
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId()))
                .thenReturn(List.of(subscription));
        when(featureRepository.findByKeyAndAccountId(eq(featureKey), any(UUID.class)))
                .thenReturn(Optional.of(feature));
        when(planFeatureRuleRepository.findPlanFeatureRuleByPlan_IdAndFeature_Id(
                subscription.getPlan().getId(), feature.getId()))
                .thenReturn(rule);
        when(creditService.getCreditPoolsByCustomer(customer.getId().toString(), accountUuid))
                .thenReturn(List.of(pool));
        when(creditService.checkHardLimitForSubscription(
                eq(subscription.getId()), any(UUID.class), eq("TOKENS"), any(BigDecimal.class)))
                .thenReturn(true);
        when(entitlementRepository.findFirstByCustomerAndFeatureKeyAndRevokedAtIsNullOrderByCreatedAtDesc(customer, featureKey))
                .thenReturn(Optional.of(entitlement));

        // Execute
        EntitlementResponse response = clientEntitlementService.checkEntitlement(referenceCustomerId, accountUuid, featureKey);

        // Verify
        assertTrue(response.isAllowed());
        assertNotNull(response.getCredit());
        assertEquals("TOKENS", response.getCredit().getDenomination());
        assertEquals(0, new BigDecimal("500").compareTo(response.getCredit().getBalance()));
        assertEquals(0, new BigDecimal("1000").compareTo(response.getCredit().getTotalGranted()));
        assertEquals(0, new BigDecimal("500").compareTo(response.getCredit().getTotalConsumed()));
        assertTrue(response.getCredit().getHardLimit());
    }

    @Test
    void testCheckEntitlement_NoCreditModel_CreditIsNull() {
        // Setup: feature has no credit model
        Subscription subscription = createActiveSubscription();
        PlanFeatureRule rule = createRuleWithMaxUsage(subscription, new BigDecimal("100"));
        // rule has no creditModel set

        when(customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(referenceCustomerId, accountUuid))
                .thenReturn(customer);
        when(entitlementService.isEntitled(featureKey, customer)).thenReturn(true);
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId()))
                .thenReturn(List.of(subscription));
        when(featureRepository.findByKeyAndAccountId(eq(featureKey), any(UUID.class)))
                .thenReturn(Optional.of(feature));
        when(planFeatureRuleRepository.findPlanFeatureRuleByPlan_IdAndFeature_Id(
                subscription.getPlan().getId(), feature.getId()))
                .thenReturn(rule);
        when(eventRepository.sumUsageUnitsByCustomerAndFeatureIdSince(
                eq(customer.getId()), eq(feature.getId()), isNull()))
                .thenReturn(new BigDecimal("40"));
        when(entitlementRepository.findFirstByCustomerAndFeatureKeyAndRevokedAtIsNullOrderByCreatedAtDesc(customer, featureKey))
                .thenReturn(Optional.of(entitlement));

        // Execute
        EntitlementResponse response = clientEntitlementService.checkEntitlement(referenceCustomerId, accountUuid, featureKey);

        // Verify
        assertTrue(response.isAllowed());
        assertNull(response.getCredit());
    }

    @Test
    void testCheckEntitlement_CreditHardLimitReached_DeniesAccess() {
        // Setup: feature has hard-limit credit model, credit check returns false (limit reached)
        Subscription subscription = createActiveSubscription();
        CreditModel creditModel = createCreditModel("TOKENS", true);
        PlanFeatureRule rule = createRuleWithCreditModel(subscription, creditModel);

        CreditPoolDto pool = new CreditPoolDto();
        pool.setDenomination("TOKENS");
        pool.setBalance(BigDecimal.ZERO);
        pool.setTotalGranted(new BigDecimal("1000"));
        pool.setTotalConsumed(new BigDecimal("1000"));

        when(customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(referenceCustomerId, accountUuid))
                .thenReturn(customer);
        when(entitlementService.isEntitled(featureKey, customer)).thenReturn(true);
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId()))
                .thenReturn(List.of(subscription));
        when(featureRepository.findByKeyAndAccountId(eq(featureKey), any(UUID.class)))
                .thenReturn(Optional.of(feature));
        when(planFeatureRuleRepository.findPlanFeatureRuleByPlan_IdAndFeature_Id(
                subscription.getPlan().getId(), feature.getId()))
                .thenReturn(rule);
        // Hard limit check returns false = limit reached
        when(creditService.checkHardLimitForSubscription(
                eq(subscription.getId()), any(UUID.class), eq("TOKENS"), any(BigDecimal.class)))
                .thenReturn(false);
        when(creditService.getCreditPoolsByCustomer(customer.getId().toString(), accountUuid))
                .thenReturn(List.of(pool));

        // Execute
        EntitlementResponse response = clientEntitlementService.checkEntitlement(referenceCustomerId, accountUuid, featureKey);

        // Verify
        assertFalse(response.isAllowed());
        assertEquals("Credit limit reached", response.getMeta().getReason().getDescription());
        assertNotNull(response.getCredit());
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getCredit().getBalance()));
    }

    @Test
    void testCheckEntitlement_UsageBasedNoMaxUsage_ReturnsUsedOnly() {
        // Setup: usage-based feature with no max_usage → usage.used populated, limit/remaining null
        Subscription subscription = createActiveSubscription();
        PlanFeatureRule rule = createRuleWithUsageModelNoMax(subscription);

        when(customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(referenceCustomerId, accountUuid))
                .thenReturn(customer);
        when(entitlementService.isEntitled(featureKey, customer)).thenReturn(true);
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId()))
                .thenReturn(List.of(subscription));
        when(featureRepository.findByKeyAndAccountId(eq(featureKey), any(UUID.class)))
                .thenReturn(Optional.of(feature));
        when(planFeatureRuleRepository.findPlanFeatureRuleByPlan_IdAndFeature_Id(
                subscription.getPlan().getId(), feature.getId()))
                .thenReturn(rule);
        when(eventRepository.sumUsageUnitsByCustomerAndFeatureIdSince(
                eq(customer.getId()), eq(feature.getId()), isNull()))
                .thenReturn(new BigDecimal("75"));
        when(entitlementRepository.findFirstByCustomerAndFeatureKeyAndRevokedAtIsNullOrderByCreatedAtDesc(customer, featureKey))
                .thenReturn(Optional.of(entitlement));

        // Execute
        EntitlementResponse response = clientEntitlementService.checkEntitlement(referenceCustomerId, accountUuid, featureKey);

        // Verify: allowed, usage.used is set, limit and remaining are null
        assertTrue(response.isAllowed());
        assertNotNull(response.getUsage());
        assertEquals(0, new BigDecimal("75").compareTo(response.getUsage().getUsed()));
        assertNull(response.getUsage().getLimit());
        assertNull(response.getUsage().getRemaining());
    }

    @Test
    void testEvaluateEntitlement_UsageBasedNoMaxUsage_NoSimulation() {
        // Setup: usage-based feature with no max_usage + usage context → simulation null, usage.used populated
        Subscription subscription = createActiveSubscription();
        PlanFeatureRule rule = createRuleWithUsageModelNoMax(subscription);

        EntitlementEvaluationRequest request = new EntitlementEvaluationRequest();
        request.setCustomerReferenceId(referenceCustomerId);
        request.setFeatureKey(featureKey);
        UsageContext usage = new UsageContext();
        usage.setUsageUnits(new BigDecimal("50"));
        request.setUsage(usage);

        when(customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(referenceCustomerId, accountUuid))
                .thenReturn(customer);
        when(entitlementService.isEntitled(featureKey, customer)).thenReturn(true);
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId()))
                .thenReturn(List.of(subscription));
        when(featureRepository.findByKeyAndAccountId(eq(featureKey), any(UUID.class)))
                .thenReturn(Optional.of(feature));
        when(planFeatureRuleRepository.findPlanFeatureRuleByPlan_IdAndFeature_Id(
                subscription.getPlan().getId(), feature.getId()))
                .thenReturn(rule);
        when(eventRepository.sumUsageUnitsByCustomerAndFeatureIdSince(
                eq(customer.getId()), eq(feature.getId()), isNull()))
                .thenReturn(new BigDecimal("30"));
        when(entitlementRepository.findFirstByCustomerAndFeatureKeyAndRevokedAtIsNullOrderByCreatedAtDesc(customer, featureKey))
                .thenReturn(Optional.of(entitlement));

        // Execute
        EntitlementResponse response = clientEntitlementService.evaluateEntitlement(accountUuid, request);

        // Verify: allowed, no simulation (can't project without a limit), usage.used is populated
        assertTrue(response.isAllowed());
        assertNull(response.getSimulation());
        assertNotNull(response.getUsage());
        assertEquals(0, new BigDecimal("30").compareTo(response.getUsage().getUsed()));
        assertNull(response.getUsage().getLimit());
        assertNull(response.getUsage().getRemaining());
    }

    @Test
    void testCheckEntitlement_ResetMode_UsesCurrentPeriodStart() throws Exception {
        // Setup: default reset mode → usage should be summed since currentPeriodStart
        Instant createdAt = Instant.now().minus(90, ChronoUnit.DAYS);
        Instant periodStart = Instant.now().minus(5, ChronoUnit.DAYS);

        Subscription subscription = createActiveSubscription();
        setSubscriptionCreatedAt(subscription, createdAt);
        subscription.setCurrentPeriodStart(periodStart);

        PlanFeatureRule rule = createRuleWithMaxUsage(subscription, new BigDecimal("100"));

        when(customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(referenceCustomerId, accountUuid))
                .thenReturn(customer);
        when(entitlementService.isEntitled(featureKey, customer)).thenReturn(true);
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId()))
                .thenReturn(List.of(subscription));
        when(featureRepository.findByKeyAndAccountId(eq(featureKey), any(UUID.class)))
                .thenReturn(Optional.of(feature));
        when(planFeatureRuleRepository.findPlanFeatureRuleByPlan_IdAndFeature_Id(
                subscription.getPlan().getId(), feature.getId()))
                .thenReturn(rule);
        when(eventRepository.sumUsageUnitsByCustomerAndFeatureIdSince(
                eq(customer.getId()), eq(feature.getId()), eq(periodStart)))
                .thenReturn(new BigDecimal("25"));
        when(entitlementRepository.findFirstByCustomerAndFeatureKeyAndRevokedAtIsNullOrderByCreatedAtDesc(customer, featureKey))
                .thenReturn(Optional.of(entitlement));

        // Execute
        EntitlementResponse response = clientEntitlementService.checkEntitlement(referenceCustomerId, accountUuid, featureKey);

        // Verify: called with currentPeriodStart, not createdAt
        assertTrue(response.isAllowed());
        verify(eventRepository).sumUsageUnitsByCustomerAndFeatureIdSince(
                eq(customer.getId()), eq(feature.getId()), eq(periodStart));
        assertEquals(0, new BigDecimal("25").compareTo(response.getUsage().getUsed()));
    }

    @Test
    void testCheckEntitlement_AccumulateMode_UsesCreatedAt() throws Exception {
        // Setup: accumulate mode → usage should be summed since subscription createdAt
        Instant createdAt = Instant.now().minus(90, ChronoUnit.DAYS);
        Instant periodStart = Instant.now().minus(5, ChronoUnit.DAYS);

        Subscription subscription = createActiveSubscription();
        setSubscriptionCreatedAt(subscription, createdAt);
        subscription.setCurrentPeriodStart(periodStart);

        PlanFeatureRule rule = createRuleWithMaxUsageAndResetMode(subscription, new BigDecimal("1000"), "accumulate");

        when(customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(referenceCustomerId, accountUuid))
                .thenReturn(customer);
        when(entitlementService.isEntitled(featureKey, customer)).thenReturn(true);
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId()))
                .thenReturn(List.of(subscription));
        when(featureRepository.findByKeyAndAccountId(eq(featureKey), any(UUID.class)))
                .thenReturn(Optional.of(feature));
        when(planFeatureRuleRepository.findPlanFeatureRuleByPlan_IdAndFeature_Id(
                subscription.getPlan().getId(), feature.getId()))
                .thenReturn(rule);
        when(eventRepository.sumUsageUnitsByCustomerAndFeatureIdSince(
                eq(customer.getId()), eq(feature.getId()), eq(createdAt)))
                .thenReturn(new BigDecimal("500"));
        when(entitlementRepository.findFirstByCustomerAndFeatureKeyAndRevokedAtIsNullOrderByCreatedAtDesc(customer, featureKey))
                .thenReturn(Optional.of(entitlement));

        // Execute
        EntitlementResponse response = clientEntitlementService.checkEntitlement(referenceCustomerId, accountUuid, featureKey);

        // Verify: called with createdAt, not currentPeriodStart
        assertTrue(response.isAllowed());
        verify(eventRepository).sumUsageUnitsByCustomerAndFeatureIdSince(
                eq(customer.getId()), eq(feature.getId()), eq(createdAt));
        assertEquals(0, new BigDecimal("500").compareTo(response.getUsage().getUsed()));
    }

    // --- getCustomerEntitlements tests ---

    @Test
    void testGetCustomerEntitlements_MultipleSubscriptions_GroupedCorrectly() {
        Subscription sub1 = createActiveSubscription();
        Subscription sub2 = createActiveSubscription();

        Entitlement ent1 = new Entitlement();
        ent1.setId(UUID.randomUUID());
        ent1.setFeatureKey("premium_reports");
        ent1.setCustomer(customer);
        ent1.setSubscription(sub1);

        Entitlement ent2 = new Entitlement();
        ent2.setId(UUID.randomUUID());
        ent2.setFeatureKey("api_access");
        ent2.setCustomer(customer);
        ent2.setSubscription(sub1);

        Entitlement ent3 = new Entitlement();
        ent3.setId(UUID.randomUUID());
        ent3.setFeatureKey("analytics");
        ent3.setCustomer(customer);
        ent3.setSubscription(sub2);

        when(customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(referenceCustomerId, accountUuid))
                .thenReturn(customer);
        when(entitlementRepository.findAllByCustomerAndRevokedAtIsNull(customer))
                .thenReturn(List.of(ent1, ent2, ent3));

        CustomerEntitlementsResponse response = clientEntitlementService.getCustomerEntitlements(referenceCustomerId, accountUuid);

        assertEquals(referenceCustomerId, response.getReferenceCustomerId());
        assertEquals(2, response.getSubscriptions().size());

        CustomerEntitlementsResponse.SubscriptionEntitlements group1 = response.getSubscriptions().stream()
                .filter(s -> sub1.getId().equals(s.getSubscriptionId()))
                .findFirst().orElseThrow();
        assertEquals(2, group1.getEntitlements().size());
        assertTrue(group1.getEntitlements().stream().allMatch(CustomerEntitlementsResponse.EntitlementSummary::isAllowed));

        CustomerEntitlementsResponse.SubscriptionEntitlements group2 = response.getSubscriptions().stream()
                .filter(s -> sub2.getId().equals(s.getSubscriptionId()))
                .findFirst().orElseThrow();
        assertEquals(1, group2.getEntitlements().size());
        assertEquals("analytics", group2.getEntitlements().get(0).getFeatureKey());
    }

    @Test
    void testGetCustomerEntitlements_NoEntitlements_EmptySubscriptionsList() {
        when(customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(referenceCustomerId, accountUuid))
                .thenReturn(customer);
        when(entitlementRepository.findAllByCustomerAndRevokedAtIsNull(customer))
                .thenReturn(List.of());

        CustomerEntitlementsResponse response = clientEntitlementService.getCustomerEntitlements(referenceCustomerId, accountUuid);

        assertEquals(referenceCustomerId, response.getReferenceCustomerId());
        assertNotNull(response.getSubscriptions());
        assertTrue(response.getSubscriptions().isEmpty());
    }

    @Test
    void testGetCustomerEntitlements_NullSubscription_GroupedUnderNullId() {
        Entitlement ent1 = new Entitlement();
        ent1.setId(UUID.randomUUID());
        ent1.setFeatureKey("standalone_feature");
        ent1.setCustomer(customer);
        // subscription is null

        when(customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(referenceCustomerId, accountUuid))
                .thenReturn(customer);
        when(entitlementRepository.findAllByCustomerAndRevokedAtIsNull(customer))
                .thenReturn(List.of(ent1));

        CustomerEntitlementsResponse response = clientEntitlementService.getCustomerEntitlements(referenceCustomerId, accountUuid);

        assertEquals(1, response.getSubscriptions().size());
        assertNull(response.getSubscriptions().get(0).getSubscriptionId());
        assertEquals(1, response.getSubscriptions().get(0).getEntitlements().size());
        assertEquals("standalone_feature", response.getSubscriptions().get(0).getEntitlements().get(0).getFeatureKey());
        assertTrue(response.getSubscriptions().get(0).getEntitlements().get(0).isAllowed());
    }

    // --- Helper methods ---

    private Subscription createActiveSubscription() {
        Plan plan = new Plan();
        plan.setId(UUID.randomUUID());

        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setIsActive(true);
        subscription.setPlan(plan);
        subscription.setCustomer(customer);
        return subscription;
    }

    private PlanFeatureRule createRuleWithMaxUsage(Subscription subscription, BigDecimal maxUsage) {
        Map<String, Object> value = new HashMap<>();
        value.put("model", "usage");
        value.put("price_per_unit", 1);
        value.put("max_usage", maxUsage);

        PlanFeatureRule rule = new PlanFeatureRule();
        rule.setId(UUID.randomUUID());
        rule.setPlan(subscription.getPlan());
        rule.setFeature(feature);
        rule.setValue(value);
        return rule;
    }

    private PlanFeatureRule createRuleWithMaxUsageAndResetMode(Subscription subscription, BigDecimal maxUsage, String resetMode) {
        Map<String, Object> value = new HashMap<>();
        value.put("model", "usage");
        value.put("price_per_unit", 1);
        value.put("max_usage", maxUsage);
        value.put("reset_mode", resetMode);

        PlanFeatureRule rule = new PlanFeatureRule();
        rule.setId(UUID.randomUUID());
        rule.setPlan(subscription.getPlan());
        rule.setFeature(feature);
        rule.setValue(value);
        return rule;
    }

    private PlanFeatureRule createRuleWithUsageModelNoMax(Subscription subscription) {
        Map<String, Object> value = new HashMap<>();
        value.put("model", "usage");
        value.put("price_per_unit", 1);

        PlanFeatureRule rule = new PlanFeatureRule();
        rule.setId(UUID.randomUUID());
        rule.setPlan(subscription.getPlan());
        rule.setFeature(feature);
        rule.setValue(value);
        return rule;
    }

    private CreditModel createCreditModel(String denomination, Boolean hardLimit) {
        CreditModel creditModel = new CreditModel();
        creditModel.setId(UUID.randomUUID());
        creditModel.setDenomination(denomination);
        creditModel.setHardLimit(hardLimit);
        return creditModel;
    }

    private void setSubscriptionCreatedAt(Subscription subscription, Instant createdAt) throws Exception {
        Field field = Subscription.class.getDeclaredField("createdAt");
        field.setAccessible(true);
        field.set(subscription, createdAt);
    }

    private PlanFeatureRule createRuleWithCreditModel(Subscription subscription, CreditModel creditModel) {
        PlanFeatureRule rule = new PlanFeatureRule();
        rule.setId(UUID.randomUUID());
        rule.setPlan(subscription.getPlan());
        rule.setFeature(feature);
        rule.setCreditModel(creditModel);
        return rule;
    }
}
