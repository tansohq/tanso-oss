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

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.Feature;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.entity.PlanFeatureRule;
import com.tansoflow.tansocore.mapper.client.ClientPlanMapper;
import com.tansoflow.tansocore.model.client.ClientFeatureDto;
import com.tansoflow.tansocore.model.client.ClientPlanDto;
import com.tansoflow.tansocore.model.client.ClientPlanFeatureLinkedDto;
import com.tansoflow.tansocore.repository.PlanFeatureRuleRepository;
import com.tansoflow.tansocore.repository.PlanRepository;
import com.tansoflow.tansocore.service.internal.account.AccountService;
import com.tansoflow.tansocore.service.internal.monetization.CreditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientPlanServiceImplTest {

    @Mock
    private AccountService accountService;
    @Mock
    private PlanRepository planRepository;
    @Mock
    private PlanFeatureRuleRepository planFeatureRuleRepository;
    @Mock
    private ClientPlanMapper clientPlanMapper;
    @Mock
    private CreditService creditService;

    @InjectMocks
    private ClientPlanServiceImpl clientPlanService;

    private Account account;
    private final String accountId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        account = new Account();
        account.setId(UUID.fromString(accountId));
        when(accountService.retrieveAccount(accountId)).thenReturn(account);
    }

    @Test
    void retrieveActivePlansWithPricing_noActivePlans_returnsEmptyList() {
        when(planRepository.findAllByAccountAndStatus(account, "ACTIVE")).thenReturn(Collections.emptyList());

        List<ClientPlanFeatureLinkedDto> result = clientPlanService.retrieveActivePlansWithPricing(accountId);

        assertTrue(result.isEmpty());
    }

    @Test
    void retrieveActivePlansWithPricing_includedFeature_returnsPricingTypeIncluded() {
        Plan plan = createPlan("basic", "Basic Plan");
        Feature feature = createFeature("sso", "SSO Auth");

        PlanFeatureRule rule = new PlanFeatureRule();
        rule.setId(UUID.randomUUID());
        rule.setPlan(plan);
        rule.setFeature(feature);
        rule.setValue(null); // null value = included

        when(planRepository.findAllByAccountAndStatus(account, "ACTIVE")).thenReturn(List.of(plan));
        when(planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(List.of(plan))).thenReturn(List.of(rule));
        when(clientPlanMapper.planToClientPlanDto(plan)).thenReturn(createClientPlanDto(plan));

        List<ClientPlanFeatureLinkedDto> result = clientPlanService.retrieveActivePlansWithPricing(accountId);

        assertEquals(1, result.size());
        assertEquals(1, result.getFirst().getFeatures().size());

        ClientFeatureDto featureDto = result.getFirst().getFeatures().getFirst();
        assertEquals("included", featureDto.getPricingType());
        assertNull(featureDto.getPricing());
    }

    @Test
    void retrieveActivePlansWithPricing_usageFeature_returnsPricingTypeUsageBased() {
        Plan plan = createPlan("pro", "Pro Plan");
        Feature feature = createFeature("ai_messages", "AI Messages");

        Map<String, Object> ruleValue = new HashMap<>();
        ruleValue.put("model", "usage");
        ruleValue.put("price_per_unit", 0.05);
        ruleValue.put("usage_unit_type", "messages");
        ruleValue.put("max_usage", 10000);
        ruleValue.put("reset_mode", "reset");

        PlanFeatureRule rule = new PlanFeatureRule();
        rule.setId(UUID.randomUUID());
        rule.setPlan(plan);
        rule.setFeature(feature);
        rule.setValue(ruleValue);

        when(planRepository.findAllByAccountAndStatus(account, "ACTIVE")).thenReturn(List.of(plan));
        when(planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(List.of(plan))).thenReturn(List.of(rule));
        when(clientPlanMapper.planToClientPlanDto(plan)).thenReturn(createClientPlanDto(plan));

        List<ClientPlanFeatureLinkedDto> result = clientPlanService.retrieveActivePlansWithPricing(accountId);

        assertEquals(1, result.size());
        ClientFeatureDto featureDto = result.getFirst().getFeatures().getFirst();
        assertEquals("usage_based", featureDto.getPricingType());
        assertNotNull(featureDto.getPricing());
        assertEquals("usage", featureDto.getPricing().getModel());
        assertEquals(new BigDecimal("0.05"), featureDto.getPricing().getPricePerUnit());
        assertEquals("messages", featureDto.getPricing().getUnitLabel());
        assertEquals(new BigDecimal("10000"), featureDto.getPricing().getMaxUsage());
        assertEquals("reset", featureDto.getPricing().getResetMode());
        assertNull(featureDto.getPricing().getTiers());
    }

    @Test
    void retrieveActivePlansWithPricing_graduatedFeature_returnsPricingTypeGraduated() {
        Plan plan = createPlan("enterprise", "Enterprise Plan");
        Feature feature = createFeature("storage_gb", "Storage");

        Map<String, Object> tier1 = Map.of("up_to", 10, "price_per_unit", 0, "flat_fee", 0);
        Map<String, Object> tier2 = Map.of("up_to", 100, "price_per_unit", 0.50, "flat_fee", 5.00);
        Map<String, Object> tier3 = Map.of("up_to", "inf", "price_per_unit", 0.25, "flat_fee", 10.00);

        Map<String, Object> ruleValue = new HashMap<>();
        ruleValue.put("model", "graduated");
        ruleValue.put("usage_unit_type", "gb");
        ruleValue.put("reset_mode", "accumulate");
        ruleValue.put("tiers", List.of(tier1, tier2, tier3));

        PlanFeatureRule rule = new PlanFeatureRule();
        rule.setId(UUID.randomUUID());
        rule.setPlan(plan);
        rule.setFeature(feature);
        rule.setValue(ruleValue);

        when(planRepository.findAllByAccountAndStatus(account, "ACTIVE")).thenReturn(List.of(plan));
        when(planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(List.of(plan))).thenReturn(List.of(rule));
        when(clientPlanMapper.planToClientPlanDto(plan)).thenReturn(createClientPlanDto(plan));

        List<ClientPlanFeatureLinkedDto> result = clientPlanService.retrieveActivePlansWithPricing(accountId);

        assertEquals(1, result.size());
        ClientFeatureDto featureDto = result.getFirst().getFeatures().getFirst();
        assertEquals("graduated", featureDto.getPricingType());
        assertNotNull(featureDto.getPricing());
        assertEquals("graduated", featureDto.getPricing().getModel());
        assertNotNull(featureDto.getPricing().getTiers());
        assertEquals(3, featureDto.getPricing().getTiers().size());
        assertEquals("inf", featureDto.getPricing().getTiers().get(2).getUpTo());
    }

    @Test
    void retrieveActivePlansWithPricing_costFieldsNotExposed() {
        Plan plan = createPlan("pro", "Pro Plan");
        Feature feature = createFeature("api_calls", "API Calls");

        Map<String, Object> ruleValue = new HashMap<>();
        ruleValue.put("model", "usage");
        ruleValue.put("price_per_unit", 0.10);
        ruleValue.put("usage_unit_type", "calls");
        ruleValue.put("reset_mode", "reset");
        // Internal cost fields that should NOT appear in the response
        ruleValue.put("cost_rate", 0.02);
        ruleValue.put("cost_per_unit", 0.01);
        ruleValue.put("cost_unit", "calls");

        PlanFeatureRule rule = new PlanFeatureRule();
        rule.setId(UUID.randomUUID());
        rule.setPlan(plan);
        rule.setFeature(feature);
        rule.setValue(ruleValue);

        when(planRepository.findAllByAccountAndStatus(account, "ACTIVE")).thenReturn(List.of(plan));
        when(planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(List.of(plan))).thenReturn(List.of(rule));
        when(clientPlanMapper.planToClientPlanDto(plan)).thenReturn(createClientPlanDto(plan));

        List<ClientPlanFeatureLinkedDto> result = clientPlanService.retrieveActivePlansWithPricing(accountId);

        ClientFeatureDto featureDto = result.getFirst().getFeatures().getFirst();
        assertNotNull(featureDto.getPricing());
        // ClientFeaturePricingDto does not have costRate, costPerUnit, costUnit fields at all
        assertEquals(0, new BigDecimal("0.10").compareTo(featureDto.getPricing().getPricePerUnit()));
        assertEquals("usage", featureDto.getPricing().getModel());
    }

    @Test
    void retrieveActivePlansWithPricing_currencyPopulatedFromPlan() {
        Plan plan = createPlan("pro", "Pro Plan");
        plan.setCurrency("EUR");

        when(planRepository.findAllByAccountAndStatus(account, "ACTIVE")).thenReturn(List.of(plan));
        when(planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(List.of(plan))).thenReturn(Collections.emptyList());

        ClientPlanDto clientPlanDto = createClientPlanDto(plan);
        clientPlanDto.setCurrency("EUR");
        when(clientPlanMapper.planToClientPlanDto(plan)).thenReturn(clientPlanDto);

        List<ClientPlanFeatureLinkedDto> result = clientPlanService.retrieveActivePlansWithPricing(accountId);

        assertEquals(1, result.size());
        assertEquals("EUR", result.getFirst().getPlan().getCurrency());
    }

    @Test
    void retrieveActivePlansWithPricing_multiplePlansWithMultipleFeatures() {
        Plan plan1 = createPlan("basic", "Basic Plan");
        Plan plan2 = createPlan("pro", "Pro Plan");

        Feature feature1 = createFeature("sso", "SSO Auth");
        Feature feature2 = createFeature("api_calls", "API Calls");
        Feature feature3 = createFeature("storage", "Storage");

        // Plan 1: feature1 (included)
        PlanFeatureRule rule1 = new PlanFeatureRule();
        rule1.setId(UUID.randomUUID());
        rule1.setPlan(plan1);
        rule1.setFeature(feature1);
        rule1.setValue(null);

        // Plan 2: feature2 (usage), feature3 (included)
        Map<String, Object> usageValue = new HashMap<>();
        usageValue.put("model", "usage");
        usageValue.put("price_per_unit", 0.05);
        usageValue.put("usage_unit_type", "calls");
        usageValue.put("reset_mode", "reset");

        PlanFeatureRule rule2 = new PlanFeatureRule();
        rule2.setId(UUID.randomUUID());
        rule2.setPlan(plan2);
        rule2.setFeature(feature2);
        rule2.setValue(usageValue);

        PlanFeatureRule rule3 = new PlanFeatureRule();
        rule3.setId(UUID.randomUUID());
        rule3.setPlan(plan2);
        rule3.setFeature(feature3);
        rule3.setValue(null);

        when(planRepository.findAllByAccountAndStatus(account, "ACTIVE")).thenReturn(List.of(plan1, plan2));
        when(planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(List.of(plan1, plan2)))
                .thenReturn(List.of(rule1, rule2, rule3));
        when(clientPlanMapper.planToClientPlanDto(plan1)).thenReturn(createClientPlanDto(plan1));
        when(clientPlanMapper.planToClientPlanDto(plan2)).thenReturn(createClientPlanDto(plan2));

        List<ClientPlanFeatureLinkedDto> result = clientPlanService.retrieveActivePlansWithPricing(accountId);

        assertEquals(2, result.size());

        // Plan 1 has 1 feature
        assertEquals(1, result.getFirst().getFeatures().size());
        assertEquals("included", result.getFirst().getFeatures().getFirst().getPricingType());

        // Plan 2 has 2 features
        assertEquals(2, result.get(1).getFeatures().size());
    }

    // --- Helper methods ---

    private Plan createPlan(String key, String name) {
        Plan plan = new Plan();
        plan.setId(UUID.randomUUID());
        plan.setKey(key);
        plan.setName(name);
        plan.setDescription("Test plan");
        plan.setPriceAmount(new BigDecimal("49.00"));
        plan.setCurrency("USD");
        plan.setIntervalMonths(1);
        plan.setStatus("ACTIVE");
        plan.setBillingTiming("IN_ARREARS");
        plan.setMetadata(new HashMap<>());
        plan.setAccount(account);
        return plan;
    }

    private Feature createFeature(String key, String name) {
        Feature feature = new Feature();
        feature.setId(UUID.randomUUID());
        feature.setKey(key);
        feature.setName(name);
        feature.setDescription("Test feature");
        feature.setIsEnabled(true);
        feature.setAccount(account);
        return feature;
    }

    private ClientPlanDto createClientPlanDto(Plan plan) {
        ClientPlanDto dto = new ClientPlanDto();
        dto.setId(plan.getId());
        dto.setKey(plan.getKey());
        dto.setName(plan.getName());
        dto.setDescription(plan.getDescription());
        dto.setPriceAmount(BigDecimal.valueOf(plan.getPriceAmount() != null ? plan.getPriceAmount().intValue() : null));
        dto.setCurrency(plan.getCurrency());
        dto.setIntervalMonths(plan.getIntervalMonths());
        dto.setBillingTiming(plan.getBillingTiming());
        dto.setMetadata(plan.getMetadata());
        return dto;
    }
}
