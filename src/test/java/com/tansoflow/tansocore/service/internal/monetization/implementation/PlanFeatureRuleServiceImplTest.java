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

import com.tansoflow.tansocore.application.orchestrator.EntitlementOrchestrator;
import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.Feature;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.entity.PlanFeatureRule;
import com.tansoflow.tansocore.mapper.monetization.FeatureMapper;
import com.tansoflow.tansocore.mapper.monetization.PlanFeatureRuleMapper;
import com.tansoflow.tansocore.model.exception.InvalidRuleValueException;
import com.tansoflow.tansocore.model.feature.FeatureDto;
import com.tansoflow.tansocore.model.monetization.PlanFeatureRuleDto;
import com.tansoflow.tansocore.model.monetization.request.PlanFeatureLinkedDiffRequest;
import com.tansoflow.tansocore.model.monetization.request.PlanFeatureRuleRequest;
import com.tansoflow.tansocore.repository.CreditModelRepository;
import com.tansoflow.tansocore.repository.PlanFeatureRuleRepository;
import com.tansoflow.tansocore.service.internal.account.AccountService;
import com.tansoflow.tansocore.service.internal.monetization.FeatureService;
import com.tansoflow.tansocore.service.internal.monetization.PlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PlanFeatureRuleServiceImplTest {

    @Mock
    private PlanFeatureRuleRepository planFeatureRuleRepository;
    @Mock
    private PlanFeatureRuleMapper planFeatureRuleMapper;
    @Mock
    private FeatureService featureService;
    @Mock
    private PlanService planService;
    @Mock
    private AccountService accountService;
    @Mock
    private FeatureMapper featureMapper;
    @Mock
    private EntitlementOrchestrator entitlementOrchestrator;
    @Mock
    private CreditModelRepository creditModelRepository;

    @InjectMocks
    private PlanFeatureRuleServiceImpl planFeatureRuleService;

    private UUID accountId;
    private UUID planId;
    private Plan plan;
    private Account account;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        planId = UUID.randomUUID();
        account = new Account();
        account.setId(accountId);
        plan = new Plan();
        plan.setId(planId);
        plan.setAccount(account);
    }

    @Test
    void testAddRemovePlanFeatureRulesByDiff_UpdatesExistingRule() {
        // Given
        UUID existingFeatureId = UUID.randomUUID();
        Feature existingFeature = new Feature();
        existingFeature.setId(existingFeatureId);
        existingFeature.setKey("existing-feature");

        FeatureDto existingFeatureDto = new FeatureDto();
        existingFeatureDto.setId(existingFeatureId);

        PlanFeatureRule existingRule = new PlanFeatureRule();
        existingRule.setFeature(existingFeature);
        existingRule.setPlan(plan);
        existingRule.setIsEnabled(true);
        existingRule.setType("BASE");
        existingRule.setValue(Map.of("key", "old-value"));

        when(planService.retrievePlan(accountId, planId)).thenReturn(plan);
        when(featureService.retrieveFeaturesLinkedToPlan(plan)).thenReturn(List.of(existingFeatureDto));

        PlanFeatureLinkedDiffRequest.LinkFeature linkFeature = new PlanFeatureLinkedDiffRequest.LinkFeature();
        linkFeature.setFeatureId(existingFeatureId);
        linkFeature.setType("BASE");
        linkFeature.setIsEnabled(false);
        linkFeature.setValue(Map.of("model", "usage", "usage_unit_type", "api_calls", "price_per_unit", 0.05));

        PlanFeatureLinkedDiffRequest request = new PlanFeatureLinkedDiffRequest();
        request.setFeatures(List.of(linkFeature));

        when(planFeatureRuleRepository.findPlanFeatureRuleByPlan_IdAndFeature_Id(planId, existingFeatureId)).thenReturn(existingRule);

        // When
        planFeatureRuleService.addRemovePlanFeatureRulesByDiff(accountId.toString(), request, planId.toString());

        // Then
        assertEquals("BASE", existingRule.getType(), "Type should be updated");
        assertEquals(false, existingRule.getIsEnabled(), "IsEnabled should be updated");
        // validateAndDefaultRuleValue normalises flat input to nested format {"pricing": {...}}.
        // cost_unit is NOT defaulted — it is only persisted when explicitly provided.
        // Verify the rule value was normalised to the nested format.
        @SuppressWarnings("unchecked")
        Map<String, Object> pricing = (Map<String, Object>) existingRule.getValue().get("pricing");
        assertEquals("usage", pricing.get("model"), "Normalised value should have pricing.model=usage");
        assertEquals("api_calls", pricing.get("usage_unit_type"), "Normalised value should carry usage_unit_type");
        verify(planFeatureRuleRepository, atLeastOnce()).save(existingRule);
    }

    @Test
    void testCreatePlanFeatureRule() {
        // Given
        UUID featureId = UUID.randomUUID();
        Feature feature = new Feature();
        feature.setId(featureId);
        
        PlanFeatureRuleRequest request = new PlanFeatureRuleRequest();
        request.setPlanId(planId.toString());
        request.setFeatureId(featureId.toString());
        request.setType("BASE");
        request.setIsEnabled(true);
        request.setValue(Map.of("model", "usage", "usage_unit_type", "api_calls", "price_per_unit", 0.05));

        PlanFeatureRule rule = new PlanFeatureRule();
        rule.setPlan(plan);
        rule.setFeature(feature);

        when(accountService.retrieveAccount(accountId.toString())).thenReturn(account);
        when(planService.retrievePlan(account, planId)).thenReturn(plan);
        when(featureService.retrieveFeature(account, featureId)).thenReturn(feature);
        when(planFeatureRuleMapper.planFeatureRuleRequestToPlanFeatureRuleEntity(request)).thenReturn(rule);
        when(planFeatureRuleRepository.save(rule)).thenReturn(rule);
        when(planFeatureRuleMapper.planFeatureRuleEntityToPlanFeatureRuleDto(rule)).thenReturn(new PlanFeatureRuleDto());

        // When
        planFeatureRuleService.createPlanFeatureRule(accountId.toString(), request);

        // Then
        verify(planFeatureRuleRepository).save(rule);
        assertEquals("BASE", rule.getType());
    }

    @Test
    void testUpdatePlanFeatureRule() {
        // Given
        UUID featureId = UUID.randomUUID();
        Feature feature = new Feature();
        feature.setId(featureId);
        
        PlanFeatureRuleRequest request = new PlanFeatureRuleRequest();
        request.setPlanId(planId.toString());
        request.setFeatureId(featureId.toString());
        request.setType("BASE");
        
        PlanFeatureRule existingRule = new PlanFeatureRule();
        existingRule.setPlan(plan);
        existingRule.setFeature(feature);

        when(featureService.isOwner(accountId.toString(), featureId.toString())).thenReturn(true);
        when(planService.isOwner(accountId.toString(), planId.toString())).thenReturn(true);
        when(planFeatureRuleRepository.findPlanFeatureRuleByPlan_IdAndFeature_Id(planId, featureId)).thenReturn(existingRule);
        when(planFeatureRuleRepository.save(existingRule)).thenReturn(existingRule);

        // When
        planFeatureRuleService.updatePlanFeatureRule(accountId.toString(), request);

        // Then
        verify(planFeatureRuleMapper).updatePlanFeatureRuleEntity(request, existingRule);
        verify(planFeatureRuleRepository).save(existingRule);
    }

    @Test
    void testDeletePlanFeatureRule() {
        // Given
        UUID featureId = UUID.randomUUID();
        Feature feature = new Feature();
        feature.setId(featureId);
        
        PlanFeatureRule existingRule = new PlanFeatureRule();
        existingRule.setPlan(plan);
        existingRule.setFeature(feature);

        when(planFeatureRuleRepository.findPlanFeatureRuleByPlan_IdAndFeature_Id(planId, featureId)).thenReturn(existingRule);
        when(featureService.isOwner(accountId.toString(), featureId.toString())).thenReturn(true);
        when(planService.isOwner(accountId.toString(), planId.toString())).thenReturn(true);

        // When
        planFeatureRuleService.deletePlanFeatureRule(accountId.toString(), featureId.toString(), planId.toString());

        // Then
        verify(planFeatureRuleRepository).delete(existingRule);
        verify(entitlementOrchestrator).enqueue(accountId, planId, featureId);
    }

    @Test
    void testGetPlanFeatureRule() {
        // Given
        UUID featureId = UUID.randomUUID();
        Feature feature = new Feature();
        feature.setId(featureId);
        
        PlanFeatureRule existingRule = new PlanFeatureRule();
        existingRule.setPlan(plan);
        existingRule.setFeature(feature);

        when(planFeatureRuleRepository.findPlanFeatureRuleByPlan_IdAndFeature_Id(planId, featureId)).thenReturn(existingRule);
        when(featureService.isOwner(accountId.toString(), featureId.toString())).thenReturn(true);
        when(planService.isOwner(accountId.toString(), planId.toString())).thenReturn(true);
        when(planFeatureRuleMapper.planFeatureRuleEntityToPlanFeatureRuleDto(existingRule)).thenReturn(new PlanFeatureRuleDto());

        // When
        planFeatureRuleService.getPlanFeatureRule(accountId.toString(), featureId.toString(), planId.toString());

        // Then
        verify(planFeatureRuleMapper).planFeatureRuleEntityToPlanFeatureRuleDto(existingRule);
    }

    @Test
    void testAddRemovePlanFeatureRulesByDiff_AddsNewRule() {
        // Given
        UUID newFeatureId = UUID.randomUUID();
        Feature newFeature = new Feature();
        newFeature.setId(newFeatureId);

        when(planService.retrievePlan(accountId, planId)).thenReturn(plan);
        when(featureService.retrieveFeaturesLinkedToPlan(plan)).thenReturn(List.of());
        when(featureService.retrieveFeature(any(), eq(newFeatureId))).thenReturn(newFeature);
        when(planFeatureRuleRepository.save(any(PlanFeatureRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(featureMapper.featureEntityToFeatureDto(any())).thenReturn(new FeatureDto());

        PlanFeatureLinkedDiffRequest.LinkFeature linkFeature = new PlanFeatureLinkedDiffRequest.LinkFeature();
        linkFeature.setFeatureId(newFeatureId);
        linkFeature.setType("BASE");
        linkFeature.setIsEnabled(true);
        linkFeature.setValue(Map.of("model", "usage", "usage_unit_type", "api_calls", "price_per_unit", 0.05));

        PlanFeatureLinkedDiffRequest request = new PlanFeatureLinkedDiffRequest();
        request.setFeatures(List.of(linkFeature));

        // When
        planFeatureRuleService.addRemovePlanFeatureRulesByDiff(accountId.toString(), request, planId.toString());

        // Then
        verify(planFeatureRuleRepository, times(1)).save(any(PlanFeatureRule.class));
        verify(entitlementOrchestrator, times(1)).enqueue(eq(accountId), eq(planId), eq(newFeatureId));
    }

    @Test
    void testAddRemovePlanFeatureRulesByDiff_RemovesRule() {
        // Given
        UUID featureToRemoveId = UUID.randomUUID();
        FeatureDto featureToRemoveDto = new FeatureDto();
        featureToRemoveDto.setId(featureToRemoveId);

        Feature featureToRemove = new Feature();
        featureToRemove.setId(featureToRemoveId);

        PlanFeatureRule ruleToRemove = new PlanFeatureRule();
        ruleToRemove.setFeature(featureToRemove);
        ruleToRemove.setPlan(plan);

        when(planService.retrievePlan(accountId, planId)).thenReturn(plan);
        when(featureService.retrieveFeaturesLinkedToPlan(plan)).thenReturn(List.of(featureToRemoveDto));
        when(planFeatureRuleRepository.findPlanFeatureRuleByPlan_IdAndFeature_Id(planId, featureToRemoveId)).thenReturn(ruleToRemove);

        PlanFeatureLinkedDiffRequest request = new PlanFeatureLinkedDiffRequest();
        request.setFeatures(List.of()); // Empty means remove existing

        // When
        planFeatureRuleService.addRemovePlanFeatureRulesByDiff(accountId.toString(), request, planId.toString());

        // Then
        verify(planFeatureRuleRepository, times(1)).delete(ruleToRemove);
        verify(entitlementOrchestrator, times(1)).enqueue(eq(accountId), eq(planId), eq(featureToRemoveId));
    }

    @Test
    void testAddRemovePlanFeatureRulesByDiff_ValidationFailure_MissingUnitType() {
        // Given
        UUID newFeatureId = UUID.randomUUID();
        Feature newFeature = new Feature();
        newFeature.setId(newFeatureId);

        when(planService.retrievePlan(accountId, planId)).thenReturn(plan);
        when(featureService.retrieveFeaturesLinkedToPlan(plan)).thenReturn(List.of());
        when(featureService.retrieveFeature(any(), eq(newFeatureId))).thenReturn(newFeature);

        PlanFeatureLinkedDiffRequest.LinkFeature linkFeature = new PlanFeatureLinkedDiffRequest.LinkFeature();
        linkFeature.setFeatureId(newFeatureId);
        linkFeature.setType("BASE");
        linkFeature.setIsEnabled(true);
        linkFeature.setValue(Map.of("model", "usage")); // Missing usage_unit_type

        PlanFeatureLinkedDiffRequest request = new PlanFeatureLinkedDiffRequest();
        request.setFeatures(List.of(linkFeature));

        // When & Then
        assertThrows(InvalidRuleValueException.class, () -> 
            planFeatureRuleService.addRemovePlanFeatureRulesByDiff(accountId.toString(), request, planId.toString()));
    }

    @Test
    void testAddRemovePlanFeatureRulesByDiff_ValidationFailure_InvalidCostUnit() {
        // Given
        UUID newFeatureId = UUID.randomUUID();
        Feature newFeature = new Feature();
        newFeature.setId(newFeatureId);

        when(planService.retrievePlan(accountId, planId)).thenReturn(plan);
        when(featureService.retrieveFeaturesLinkedToPlan(plan)).thenReturn(List.of());
        when(featureService.retrieveFeature(any(), eq(newFeatureId))).thenReturn(newFeature);

        PlanFeatureLinkedDiffRequest.LinkFeature linkFeature = new PlanFeatureLinkedDiffRequest.LinkFeature();
        linkFeature.setFeatureId(newFeatureId);
        linkFeature.setType("BASE");
        linkFeature.setIsEnabled(true);
        linkFeature.setValue(Map.of(
                "model", "usage",
                "usage_unit_type", "tokens",
                "price_per_unit", 0.1,
                "cost_unit", "INVALID"
        ));

        PlanFeatureLinkedDiffRequest request = new PlanFeatureLinkedDiffRequest();
        request.setFeatures(List.of(linkFeature));

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
            planFeatureRuleService.addRemovePlanFeatureRulesByDiff(accountId.toString(), request, planId.toString()));
    }

    @Test
    void testValidateRuleValue_NegativeCostPerUnit() {
        // Given
        UUID newFeatureId = UUID.randomUUID();
        Feature newFeature = new Feature();
        newFeature.setId(newFeatureId);

        when(planService.retrievePlan(accountId, planId)).thenReturn(plan);
        when(featureService.retrieveFeaturesLinkedToPlan(plan)).thenReturn(List.of());
        when(featureService.retrieveFeature(any(), eq(newFeatureId))).thenReturn(newFeature);

        PlanFeatureLinkedDiffRequest.LinkFeature linkFeature = new PlanFeatureLinkedDiffRequest.LinkFeature();
        linkFeature.setFeatureId(newFeatureId);
        linkFeature.setType("BASE");
        linkFeature.setIsEnabled(true);
        linkFeature.setValue(Map.of(
                "model", "usage",
                "usage_unit_type", "tokens",
                "price_per_unit", 0.1,
                "cost_model", "simple",
                "cost_per_unit", -0.01
        ));

        PlanFeatureLinkedDiffRequest request = new PlanFeatureLinkedDiffRequest();
        request.setFeatures(List.of(linkFeature));

        // When & Then
        InvalidRuleValueException exception = assertThrows(InvalidRuleValueException.class, () ->
                planFeatureRuleService.addRemovePlanFeatureRulesByDiff(accountId.toString(), request, planId.toString()));
        assertEquals("cost_per_unit cannot be negative", exception.getMessage());
    }

    @Test
    void testValidateRuleValue_NormalisesToNestedFormatWithoutDefaultingCostUnit() {
        // Given
        // The production code was refactored to normalise flat input maps to a nested
        // {"pricing": {...}, "cost": {...}} format on write.  cost_unit is NOT injected
        // as a default — it is only present in the output when explicitly supplied.
        UUID newFeatureId = UUID.randomUUID();
        Feature newFeature = new Feature();
        newFeature.setId(newFeatureId);

        when(planService.retrievePlan(accountId, planId)).thenReturn(plan);
        when(featureService.retrieveFeaturesLinkedToPlan(plan)).thenReturn(List.of());
        when(featureService.retrieveFeature(any(), eq(newFeatureId))).thenReturn(newFeature);
        when(planFeatureRuleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(featureMapper.featureEntityToFeatureDto(any())).thenReturn(new FeatureDto());

        Map<String, Object> value = new java.util.HashMap<>();
        value.put("model", "usage");
        value.put("usage_unit_type", "tokens");
        value.put("price_per_unit", 0.1);

        PlanFeatureLinkedDiffRequest.LinkFeature linkFeature = new PlanFeatureLinkedDiffRequest.LinkFeature();
        linkFeature.setFeatureId(newFeatureId);
        linkFeature.setType("BASE");
        linkFeature.setIsEnabled(true);
        linkFeature.setValue(value);

        PlanFeatureLinkedDiffRequest request = new PlanFeatureLinkedDiffRequest();
        request.setFeatures(List.of(linkFeature));

        // When
        planFeatureRuleService.addRemovePlanFeatureRulesByDiff(accountId.toString(), request, planId.toString());

        // Then: the saved rule should contain the normalised nested format
        verify(planFeatureRuleRepository).save(org.mockito.ArgumentMatchers.argThat(rule -> {
            if (rule.getValue() == null) return false;
            @SuppressWarnings("unchecked")
            Map<String, Object> pricing = (Map<String, Object>) rule.getValue().get("pricing");
            if (pricing == null) return false;
            // cost_unit must NOT be injected as a default — it should be absent
            return "usage".equals(pricing.get("model"))
                    && "tokens".equals(pricing.get("usage_unit_type"))
                    && !pricing.containsKey("cost_unit");
        }));
    }

    @Test
    void testValidateRuleValue_EmptyMapReturnsNull() {
        // Given
        UUID newFeatureId = UUID.randomUUID();
        Feature newFeature = new Feature();
        newFeature.setId(newFeatureId);

        when(planService.retrievePlan(accountId, planId)).thenReturn(plan);
        when(featureService.retrieveFeaturesLinkedToPlan(plan)).thenReturn(List.of());
        when(featureService.retrieveFeature(any(), eq(newFeatureId))).thenReturn(newFeature);
        when(planFeatureRuleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(featureMapper.featureEntityToFeatureDto(any())).thenReturn(new FeatureDto());

        PlanFeatureLinkedDiffRequest.LinkFeature linkFeature = new PlanFeatureLinkedDiffRequest.LinkFeature();
        linkFeature.setFeatureId(newFeatureId);
        linkFeature.setType("BASE");
        linkFeature.setIsEnabled(true);
        linkFeature.setValue(new java.util.HashMap<>()); // Empty mutable map

        PlanFeatureLinkedDiffRequest request = new PlanFeatureLinkedDiffRequest();
        request.setFeatures(List.of(linkFeature));

        // When
        planFeatureRuleService.addRemovePlanFeatureRulesByDiff(accountId.toString(), request, planId.toString());

        // Then
        // The value should be set to null by validateAndDefaultRuleValue
        verify(planFeatureRuleRepository).save(org.mockito.ArgumentMatchers.argThat(rule -> rule.getValue() == null));
    }

    @Test
    void testValidateRuleValue_NullMapReturnsNull() {
        // Given
        UUID newFeatureId = UUID.randomUUID();
        Feature newFeature = new Feature();
        newFeature.setId(newFeatureId);

        when(planService.retrievePlan(accountId, planId)).thenReturn(plan);
        when(featureService.retrieveFeaturesLinkedToPlan(plan)).thenReturn(List.of());
        when(featureService.retrieveFeature(any(), eq(newFeatureId))).thenReturn(newFeature);
        when(planFeatureRuleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(featureMapper.featureEntityToFeatureDto(any())).thenReturn(new FeatureDto());

        PlanFeatureLinkedDiffRequest.LinkFeature linkFeature = new PlanFeatureLinkedDiffRequest.LinkFeature();
        linkFeature.setFeatureId(newFeatureId);
        linkFeature.setType("BASE");
        linkFeature.setIsEnabled(true);
        linkFeature.setValue(null); // Null map

        PlanFeatureLinkedDiffRequest request = new PlanFeatureLinkedDiffRequest();
        request.setFeatures(List.of(linkFeature));

        // When
        planFeatureRuleService.addRemovePlanFeatureRulesByDiff(accountId.toString(), request, planId.toString());

        // Then
        verify(planFeatureRuleRepository).save(org.mockito.ArgumentMatchers.argThat(rule -> rule.getValue() == null));
    }

    // --- Graduated pricing validation tests ---

    private PlanFeatureLinkedDiffRequest buildGraduatedDiffRequest(UUID featureId, Map<String, Object> value) {
        PlanFeatureLinkedDiffRequest.LinkFeature linkFeature = new PlanFeatureLinkedDiffRequest.LinkFeature();
        linkFeature.setFeatureId(featureId);
        linkFeature.setType("BASE");
        linkFeature.setIsEnabled(true);
        linkFeature.setValue(value);

        PlanFeatureLinkedDiffRequest request = new PlanFeatureLinkedDiffRequest();
        request.setFeatures(List.of(linkFeature));
        return request;
    }

    private void stubForNewFeature(UUID featureId) {
        Feature feature = new Feature();
        feature.setId(featureId);
        when(planService.retrievePlan(accountId, planId)).thenReturn(plan);
        when(featureService.retrieveFeaturesLinkedToPlan(plan)).thenReturn(List.of());
        when(featureService.retrieveFeature(any(), eq(featureId))).thenReturn(feature);
    }

    @Test
    void testValidateGraduatedPricing_ValidTiers_Succeeds() {
        // Given
        UUID featureId = UUID.randomUUID();
        stubForNewFeature(featureId);
        when(planFeatureRuleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(featureMapper.featureEntityToFeatureDto(any())).thenReturn(new FeatureDto());

        Map<String, Object> tier1 = new HashMap<>();
        tier1.put("up_to", 1000);
        tier1.put("price_per_unit", 0.00);
        tier1.put("flat_fee", 10.00);

        Map<String, Object> tier2 = new HashMap<>();
        tier2.put("up_to", "inf");
        tier2.put("price_per_unit", 0.001);
        tier2.put("flat_fee", 0);

        Map<String, Object> value = new HashMap<>();
        value.put("model", "graduated");
        value.put("usage_unit_type", "tokens");
        value.put("tiers", List.of(tier1, tier2));

        PlanFeatureLinkedDiffRequest request = buildGraduatedDiffRequest(featureId, value);

        // When
        planFeatureRuleService.addRemovePlanFeatureRulesByDiff(accountId.toString(), request, planId.toString());

        // Then
        verify(planFeatureRuleRepository, times(1)).save(any(PlanFeatureRule.class));
    }

    @Test
    void testValidateGraduatedPricing_MissingTiers_ThrowsException() {
        // Given
        UUID featureId = UUID.randomUUID();
        stubForNewFeature(featureId);

        Map<String, Object> value = new HashMap<>();
        value.put("model", "graduated");
        value.put("usage_unit_type", "tokens");
        // No tiers

        PlanFeatureLinkedDiffRequest request = buildGraduatedDiffRequest(featureId, value);

        // When & Then
        InvalidRuleValueException ex = assertThrows(InvalidRuleValueException.class, () ->
                planFeatureRuleService.addRemovePlanFeatureRulesByDiff(accountId.toString(), request, planId.toString()));
        assertEquals("Tiers are required for graduated pricing model", ex.getMessage());
    }

    @Test
    void testValidateGraduatedPricing_EmptyTiers_ThrowsException() {
        // Given
        UUID featureId = UUID.randomUUID();
        stubForNewFeature(featureId);

        Map<String, Object> value = new HashMap<>();
        value.put("model", "graduated");
        value.put("usage_unit_type", "tokens");
        value.put("tiers", List.of());

        PlanFeatureLinkedDiffRequest request = buildGraduatedDiffRequest(featureId, value);

        // When & Then
        InvalidRuleValueException ex = assertThrows(InvalidRuleValueException.class, () ->
                planFeatureRuleService.addRemovePlanFeatureRulesByDiff(accountId.toString(), request, planId.toString()));
        assertEquals("Tiers are required for graduated pricing model", ex.getMessage());
    }

    @Test
    void testValidateGraduatedPricing_NegativePricePerUnit_ThrowsException() {
        // Given
        UUID featureId = UUID.randomUUID();
        stubForNewFeature(featureId);

        Map<String, Object> tier1 = new HashMap<>();
        tier1.put("up_to", "inf");
        tier1.put("price_per_unit", -0.01);

        Map<String, Object> value = new HashMap<>();
        value.put("model", "graduated");
        value.put("usage_unit_type", "tokens");
        value.put("tiers", List.of(tier1));

        PlanFeatureLinkedDiffRequest request = buildGraduatedDiffRequest(featureId, value);

        // When & Then
        InvalidRuleValueException ex = assertThrows(InvalidRuleValueException.class, () ->
                planFeatureRuleService.addRemovePlanFeatureRulesByDiff(accountId.toString(), request, planId.toString()));
        assertEquals("price_per_unit cannot be negative in tiers", ex.getMessage());
    }

    @Test
    void testValidateGraduatedPricing_MissingPricePerUnit_ThrowsException() {
        // Given
        UUID featureId = UUID.randomUUID();
        stubForNewFeature(featureId);

        Map<String, Object> tier1 = new HashMap<>();
        tier1.put("up_to", "inf");
        // No price_per_unit

        Map<String, Object> value = new HashMap<>();
        value.put("model", "graduated");
        value.put("usage_unit_type", "tokens");
        value.put("tiers", List.of(tier1));

        PlanFeatureLinkedDiffRequest request = buildGraduatedDiffRequest(featureId, value);

        // When & Then
        InvalidRuleValueException ex = assertThrows(InvalidRuleValueException.class, () ->
                planFeatureRuleService.addRemovePlanFeatureRulesByDiff(accountId.toString(), request, planId.toString()));
        assertEquals("pricePerUnit is required for each tier", ex.getMessage());
    }

    @Test
    void testValidateGraduatedPricing_MissingUpTo_ThrowsException() {
        // Given
        UUID featureId = UUID.randomUUID();
        stubForNewFeature(featureId);

        Map<String, Object> tier1 = new HashMap<>();
        tier1.put("price_per_unit", 0.01);
        // No up_to

        Map<String, Object> value = new HashMap<>();
        value.put("model", "graduated");
        value.put("usage_unit_type", "tokens");
        value.put("tiers", List.of(tier1));

        PlanFeatureLinkedDiffRequest request = buildGraduatedDiffRequest(featureId, value);

        // When & Then
        InvalidRuleValueException ex = assertThrows(InvalidRuleValueException.class, () ->
                planFeatureRuleService.addRemovePlanFeatureRulesByDiff(accountId.toString(), request, planId.toString()));
        assertEquals("upTo is required for each tier", ex.getMessage());
    }

    @Test
    void testValidateGraduatedPricing_NegativeFlatFee_ThrowsException() {
        // Given
        UUID featureId = UUID.randomUUID();
        stubForNewFeature(featureId);

        Map<String, Object> tier1 = new HashMap<>();
        tier1.put("up_to", "inf");
        tier1.put("price_per_unit", 0.01);
        tier1.put("flat_fee", -5.00);

        Map<String, Object> value = new HashMap<>();
        value.put("model", "graduated");
        value.put("usage_unit_type", "tokens");
        value.put("tiers", List.of(tier1));

        PlanFeatureLinkedDiffRequest request = buildGraduatedDiffRequest(featureId, value);

        // When & Then
        InvalidRuleValueException ex = assertThrows(InvalidRuleValueException.class, () ->
                planFeatureRuleService.addRemovePlanFeatureRulesByDiff(accountId.toString(), request, planId.toString()));
        assertEquals("flat_fee cannot be negative in tiers", ex.getMessage());
    }

    @Test
    void testValidateGraduatedPricing_NullFlatFee_Succeeds() {
        // Given — flat_fee is optional, null should be accepted
        UUID featureId = UUID.randomUUID();
        stubForNewFeature(featureId);
        when(planFeatureRuleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(featureMapper.featureEntityToFeatureDto(any())).thenReturn(new FeatureDto());

        Map<String, Object> tier1 = new HashMap<>();
        tier1.put("up_to", "inf");
        tier1.put("price_per_unit", 0.01);
        // No flat_fee key at all

        Map<String, Object> value = new HashMap<>();
        value.put("model", "graduated");
        value.put("usage_unit_type", "tokens");
        value.put("tiers", List.of(tier1));

        PlanFeatureLinkedDiffRequest request = buildGraduatedDiffRequest(featureId, value);

        // When
        planFeatureRuleService.addRemovePlanFeatureRulesByDiff(accountId.toString(), request, planId.toString());

        // Then
        verify(planFeatureRuleRepository, times(1)).save(any(PlanFeatureRule.class));
    }

    @Test
    void testValidateGraduatedPricing_MissingUsageUnitType_ThrowsException() {
        // Given
        UUID featureId = UUID.randomUUID();
        stubForNewFeature(featureId);

        Map<String, Object> tier1 = new HashMap<>();
        tier1.put("up_to", "inf");
        tier1.put("price_per_unit", 0.01);

        Map<String, Object> value = new HashMap<>();
        value.put("model", "graduated");
        // No usage_unit_type
        value.put("tiers", List.of(tier1));

        PlanFeatureLinkedDiffRequest request = buildGraduatedDiffRequest(featureId, value);

        // When & Then
        assertThrows(InvalidRuleValueException.class, () ->
                planFeatureRuleService.addRemovePlanFeatureRulesByDiff(accountId.toString(), request, planId.toString()));
    }
}
