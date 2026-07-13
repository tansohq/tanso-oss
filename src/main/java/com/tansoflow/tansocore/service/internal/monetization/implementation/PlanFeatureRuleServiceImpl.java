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
import com.tansoflow.tansocore.entity.CreditModel;
import com.tansoflow.tansocore.entity.Feature;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.entity.PlanFeatureRule;
import com.tansoflow.tansocore.mapper.monetization.FeatureMapper;
import com.tansoflow.tansocore.mapper.monetization.PlanFeatureRuleMapper;
import com.tansoflow.tansocore.model.event.events.type.CostUnit;
import com.tansoflow.tansocore.model.exception.InvalidRuleValueException;
import com.tansoflow.tansocore.model.feature.FeatureDto;
import com.tansoflow.tansocore.model.monetization.PlanFeatureRuleDto;
import com.tansoflow.tansocore.model.monetization.RuleConfig;
import com.tansoflow.tansocore.model.monetization.cost.CostModel;
import com.tansoflow.tansocore.model.monetization.cost.ModelAwareCostModel;
import com.tansoflow.tansocore.model.monetization.cost.SimpleCostModel;
import com.tansoflow.tansocore.model.monetization.pricing.GraduatedPricingModel;
import com.tansoflow.tansocore.model.monetization.pricing.PricingModel;
import com.tansoflow.tansocore.model.monetization.pricing.SimpleUsageModel;
import com.tansoflow.tansocore.model.monetization.request.PlanFeatureLinkedDiffRequest;
import com.tansoflow.tansocore.model.monetization.request.PlanFeatureRuleRequest;
import com.tansoflow.tansocore.model.monetization.response.PlanFeatureLinkedDiffResponse;
import com.tansoflow.tansocore.model.monetization.types.PlanFeatureRuleType;
import com.tansoflow.tansocore.repository.CreditModelRepository;
import com.tansoflow.tansocore.repository.EntitlementRepository;
import com.tansoflow.tansocore.repository.PlanFeatureRuleRepository;
import com.tansoflow.tansocore.service.internal.account.AccountService;
import com.tansoflow.tansocore.service.internal.monetization.FeatureService;
import com.tansoflow.tansocore.service.internal.monetization.PlanFeatureRuleService;
import com.tansoflow.tansocore.service.internal.monetization.PlanService;
import com.tansoflow.tansocore.util.monetization.RuleCalculationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PlanFeatureRuleServiceImpl implements PlanFeatureRuleService {
    private final PlanFeatureRuleRepository planFeatureRuleRepository;
    private final PlanFeatureRuleMapper planFeatureRuleMapper;
    private final FeatureService featureService;
    private final PlanService planService;
    private final AccountService accountService;
    private final FeatureMapper featureMapper;
    private final EntitlementRepository entitlementRepository;
    private final EntitlementOrchestrator entitlementOrchestrator;
    private final CreditModelRepository creditModelRepository;

    @Override
    public PlanFeatureRuleDto createPlanFeatureRule(String accountId, PlanFeatureRuleRequest planFeatureRuleRequest) {
        PlanFeatureRuleType type;
        try {
            type = PlanFeatureRuleType.valueOf(planFeatureRuleRequest.getType());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Invalid rule type: " + planFeatureRuleRequest.getType());
        }

        Account account = accountService.retrieveAccount(accountId);

        Plan plan = planService.retrievePlan(account, UUID.fromString(planFeatureRuleRequest.getPlanId()));
        Feature feature = featureService.retrieveFeature(account, UUID.fromString(planFeatureRuleRequest.getFeatureId()));

        planFeatureRuleRequest.setValue(validateAndDefaultRuleValue(planFeatureRuleRequest.getValue()));

        PlanFeatureRule planFeatureRule = planFeatureRuleMapper.planFeatureRuleRequestToPlanFeatureRuleEntity(planFeatureRuleRequest);
        planFeatureRule.setPlan(plan);
        planFeatureRule.setFeature(feature);
        planFeatureRule.setType(type.name());
        planFeatureRule.setIsEnabled(planFeatureRuleRequest.getIsEnabled() != null ? planFeatureRuleRequest.getIsEnabled() : true);
        resolveCreditModel(planFeatureRuleRequest.getCreditModelId(), UUID.fromString(accountId), planFeatureRule);

        PlanFeatureRule savedRule = planFeatureRuleRepository.save(planFeatureRule);
        entitlementOrchestrator.enqueue(UUID.fromString(accountId), plan.getId(), feature.getId());
        return planFeatureRuleMapper.planFeatureRuleEntityToPlanFeatureRuleDto(savedRule);
    }

    @Override
    public PlanFeatureRuleDto updatePlanFeatureRule(String accountId, PlanFeatureRuleRequest planFeatureRuleRequest) {
        checkOwnership(accountId, planFeatureRuleRequest);
        PlanFeatureRule planFeatureRule = retrievePlanFeatureRule(planFeatureRuleRequest.getPlanId(), planFeatureRuleRequest.getFeatureId());

        planFeatureRuleRequest.setValue(validateAndDefaultRuleValue(planFeatureRuleRequest.getValue()));

        planFeatureRuleMapper.updatePlanFeatureRuleEntity(planFeatureRuleRequest, planFeatureRule);
        resolveCreditModel(planFeatureRuleRequest.getCreditModelId(), UUID.fromString(accountId), planFeatureRule);

        PlanFeatureRule updatedRule = planFeatureRuleRepository.save(planFeatureRule);
        entitlementOrchestrator.enqueue(UUID.fromString(accountId), updatedRule.getPlan().getId(), updatedRule.getFeature().getId());
        return planFeatureRuleMapper.planFeatureRuleEntityToPlanFeatureRuleDto(updatedRule);
    }

    private Map<String, Object> validateAndDefaultRuleValue(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        // Parse both nested and flat formats via RuleConfig
        RuleConfig config = RuleCalculationUtil.extractRuleConfig(new java.util.HashMap<>(value));
        PricingModel pricingModel = config != null ? config.getPricing() : null;
        CostModel costModel = config != null ? config.getCost() : null;

        if (pricingModel == null) {
            throw new InvalidRuleValueException("Invalid pricing model configuration",
                    "The 'model' field is missing or invalid. Supported models are: 'usage', 'graduated'. " +
                    "Use nested format: {\"pricing\": {\"model\": \"usage\", ...}, \"cost\": {\"model\": \"simple\", ...}}");
        }

        if (pricingModel.getUsageUnitType() == null || pricingModel.getUsageUnitType().isBlank()) {
            throw new InvalidRuleValueException("usage_unit_type is required",
                    "Please provide a 'usage_unit_type' string (e.g., 'api_calls', 'gb', 'seats').");
        }

        // Validate reset_mode
        if (pricingModel.getResetMode() != null
                && !"reset".equalsIgnoreCase(pricingModel.getResetMode())
                && !"accumulate".equalsIgnoreCase(pricingModel.getResetMode())) {
            throw new InvalidRuleValueException("Invalid reset_mode",
                    "Allowed values for 'reset_mode' are: 'reset', 'accumulate'. Got: '" + pricingModel.getResetMode() + "'.");
        }

        // Validate max_usage
        if (pricingModel.getMaxUsage() != null && pricingModel.getMaxUsage().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new InvalidRuleValueException("max_usage cannot be negative",
                    "Please provide a non-negative value for 'max_usage'.");
        }

        if (pricingModel instanceof SimpleUsageModel simpleModel) {
            if (simpleModel.getPricePerUnit() == null && simpleModel.getCostRate() == null && simpleModel.getCostPerUnit() == null) {
                throw new InvalidRuleValueException("Pricing parameters missing",
                        "For 'usage' model, at least one of 'price_per_unit', 'cost_rate', or 'cost_per_unit' must be provided. " +
                                "Example: {\"pricing\": {\"model\": \"usage\", \"usage_unit_type\": \"api_calls\", \"price_per_unit\": 0.05}}");
            }
            if (simpleModel.getPricePerUnit() != null && simpleModel.getPricePerUnit().compareTo(java.math.BigDecimal.ZERO) < 0) {
                throw new InvalidRuleValueException("price_per_unit cannot be negative", "Please provide a non-negative value for 'price_per_unit'.");
            }
            if (simpleModel.getCostRate() != null && simpleModel.getCostRate().compareTo(java.math.BigDecimal.ZERO) < 0) {
                throw new InvalidRuleValueException("cost_rate cannot be negative", "Please provide a non-negative value for 'cost_rate'.");
            }
            if (simpleModel.getCostPerUnit() != null && simpleModel.getCostPerUnit().compareTo(java.math.BigDecimal.ZERO) < 0) {
                throw new InvalidRuleValueException("cost_per_unit cannot be negative", "Please provide a non-negative value for 'cost_per_unit'.");
            }
            if (simpleModel.getCostUnit() != null) {
                validateCostUnit(simpleModel.getCostUnit());
            }
        } else if (pricingModel instanceof GraduatedPricingModel graduatedModel) {
            if (graduatedModel.getTiers() == null || graduatedModel.getTiers().isEmpty()) {
                throw new InvalidRuleValueException("Tiers are required for graduated pricing model",
                        "Example: {\"pricing\": {\"model\": \"graduated\", \"usage_unit_type\": \"tokens\", \"tiers\": [{\"up_to\": 1000, \"price_per_unit\": 0}, {\"up_to\": \"inf\", \"price_per_unit\": 0.1}]}}");
            }
            for (GraduatedPricingModel.PriceTier tier : graduatedModel.getTiers()) {
                if (tier.getPricePerUnit() == null) {
                    throw new InvalidRuleValueException("pricePerUnit is required for each tier",
                            "Each tier in 'graduated' model must have a 'price_per_unit'.");
                }
                if (tier.getPricePerUnit().compareTo(java.math.BigDecimal.ZERO) < 0) {
                    throw new InvalidRuleValueException("price_per_unit cannot be negative in tiers", "Please provide a non-negative value for 'price_per_unit' in all tiers.");
                }
                if (tier.getUpTo() == null) {
                    throw new InvalidRuleValueException("upTo is required for each tier",
                            "Each tier in 'graduated' model must have an 'up_to' value (number or 'inf').");
                }
                if (tier.getFlatFee() != null && tier.getFlatFee().compareTo(java.math.BigDecimal.ZERO) < 0) {
                    throw new InvalidRuleValueException("flat_fee cannot be negative in tiers", "Please provide a non-negative value for 'flat_fee' in all tiers.");
                }
            }
        }

        // Validate Cost Model if present
        if (costModel != null) {
            if (costModel instanceof SimpleCostModel simpleCostModel) {
                if (simpleCostModel.getCostPerUnit() == null) {
                    throw new InvalidRuleValueException("cost_per_unit is required for simple cost model",
                            "Example: {\"cost\": {\"model\": \"simple\", \"cost_per_unit\": 0.01}}");
                }
                if (simpleCostModel.getCostPerUnit().compareTo(java.math.BigDecimal.ZERO) < 0) {
                    throw new InvalidRuleValueException("cost_per_unit cannot be negative",
                            "Please provide a non-negative value for 'cost_per_unit'.");
                }
                if (simpleCostModel.getCostUnit() != null) {
                    validateCostUnit(simpleCostModel.getCostUnit());
                }
            } else if (costModel instanceof ModelAwareCostModel mac) {
                if (mac.getDefaultInputCostPerUnit() == null) {
                    throw new InvalidRuleValueException("default_input_cost_per_unit is required for model_aware cost model",
                            "Example: {\"cost\": {\"model\": \"model_aware\", \"default_input_cost_per_unit\": 0.00003, \"model_costs\": {\"gpt-4\": {\"input\": 0.00006, \"output\": 0.00012}}}}");
                }
                if (mac.getDefaultInputCostPerUnit().compareTo(java.math.BigDecimal.ZERO) < 0) {
                    throw new InvalidRuleValueException("default_input_cost_per_unit cannot be negative",
                            "Please provide a non-negative value for 'default_input_cost_per_unit'.");
                }
                if (mac.getDefaultOutputCostPerUnit() != null && mac.getDefaultOutputCostPerUnit().compareTo(java.math.BigDecimal.ZERO) < 0) {
                    throw new InvalidRuleValueException("default_output_cost_per_unit cannot be negative",
                            "Please provide a non-negative value for 'default_output_cost_per_unit'.");
                }
                if (mac.getCostUnit() != null) {
                    validateCostUnit(mac.getCostUnit());
                }
            }
        }

        // Normalize to nested format on write
        return normalizeToNestedFormat(pricingModel, costModel);
    }

    /**
     * Always write back in nested format: { "pricing": {...}, "cost": {...} }
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeToNestedFormat(PricingModel pricingModel, CostModel costModel) {
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Map<String, Object> normalized = new java.util.HashMap<>();

        Map<String, Object> pricingMap = objectMapper.convertValue(pricingModel, Map.class);
        // Remove null values for cleaner output
        pricingMap.values().removeIf(java.util.Objects::isNull);
        // Remove legacy cost_model fields that may have leaked into pricing
        pricingMap.remove("cost_model");
        normalized.put("pricing", pricingMap);

        if (costModel != null) {
            Map<String, Object> costMap = objectMapper.convertValue(costModel, Map.class);
            costMap.values().removeIf(java.util.Objects::isNull);
            // Remove legacy fields from cost output
            costMap.remove("cost_model");
            // Remove legacy meta indirection fields
            costMap.remove("meta_model_key");
            costMap.remove("meta_cost_units_key");
            normalized.put("cost", costMap);
        }

        return normalized;
    }

    private void validateCostUnit(String costUnit) {
        try {
            CostUnit.valueOf(costUnit.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid cost_unit: " + costUnit +
                    ". Allowed values: " + Arrays.toString(CostUnit.values()));
        }
    }

    @Override
    public void deletePlanFeatureRule(String accountId, String featureUuid, String planUuid) {
        PlanFeatureRule planFeatureRule = retrievePlanFeatureRule(planUuid, featureUuid);

        if (!featureService.isOwner(accountId, planFeatureRule.getFeature().getId().toString()) || !planService.isOwner(accountId, planFeatureRule.getPlan().getId().toString())) {
            throw new IllegalArgumentException("Feature or Plan does not belong to the account");
        }

        planFeatureRuleRepository.delete(planFeatureRule);
        entitlementOrchestrator.enqueue(UUID.fromString(accountId), UUID.fromString(planUuid), UUID.fromString(featureUuid));

    }

    @Transactional(readOnly = true)
    @Override
    public PlanFeatureRuleDto getPlanFeatureRule(String accountId, String featureUuid, String planUuid) {
        PlanFeatureRule planFeatureRule = retrievePlanFeatureRule(planUuid, featureUuid);

        if (!featureService.isOwner(accountId, planFeatureRule.getFeature().getId().toString()) || !planService.isOwner(accountId, planFeatureRule.getPlan().getId().toString())) {
            throw new IllegalArgumentException("Feature or Plan does not belong to the account");
        }

        return planFeatureRuleMapper.planFeatureRuleEntityToPlanFeatureRuleDto(planFeatureRule);
    }

    @Transactional
    @Override
    public PlanFeatureLinkedDiffResponse addRemovePlanFeatureRulesByDiff(String accountId, PlanFeatureLinkedDiffRequest planFeatureLinkedDiffRequest, String planUuid) {
        PlanFeatureLinkedDiffResponse planFeatureLinkedDiffResponse = new PlanFeatureLinkedDiffResponse();
        planFeatureLinkedDiffResponse.setAddedFeatures(new ArrayList<>());
        planFeatureLinkedDiffResponse.setRemovedFeatures(new ArrayList<>());

        Account account = accountService.retrieveAccount(accountId);

        Plan plan = planService.retrievePlan(UUID.fromString(accountId), UUID.fromString(planUuid));
        Map<UUID, FeatureDto> featureUuidMap = featureService.retrieveFeaturesLinkedToPlan(plan)
                .stream()
                .collect(Collectors.toMap(FeatureDto::getId, featureDto -> featureDto));

        Map<UUID, List<PlanFeatureLinkedDiffRequest.LinkFeature>> requestMap = planFeatureLinkedDiffRequest
                .getFeatures()
                .stream()
                .collect(Collectors
                        .groupingBy(PlanFeatureLinkedDiffRequest.LinkFeature::getFeatureId));

        Set<UUID> uuidsToAdd = new HashSet<>(requestMap.keySet());
        uuidsToAdd.removeAll(featureUuidMap.keySet());

        Set<UUID> uuidsToRemove = new HashSet<>(featureUuidMap.keySet());
        uuidsToRemove.removeAll(requestMap.keySet());

        Set<UUID> uuidsToUpdate = new HashSet<>(requestMap.keySet());
        uuidsToUpdate.retainAll(featureUuidMap.keySet());

        for (UUID featureUuid : uuidsToAdd) {
            PlanFeatureRule planFeatureRule = new PlanFeatureRule();
            Feature feature = featureService.retrieveFeature(account, featureUuid);
            planFeatureRule.setPlan(plan);
            planFeatureRule.setFeature(feature);
            PlanFeatureLinkedDiffRequest.LinkFeature link = requestMap.get(featureUuid).getLast();

            link.setValue(validateAndDefaultRuleValue(link.getValue()));

            planFeatureRule.setType(PlanFeatureRuleType.valueOf(link.getType()).name());
            planFeatureRule.setIsEnabled(link.getIsEnabled() != null ? link.getIsEnabled() : true);
            planFeatureRule.setValue(link.getValue());
            resolveCreditModel(link.getCreditModelId(), UUID.fromString(accountId), planFeatureRule);
            PlanFeatureRule addedRule = planFeatureRuleRepository.save(planFeatureRule);
            planFeatureLinkedDiffResponse.getAddedFeatures()
                    .add(featureMapper.featureEntityToFeatureDto(addedRule.getFeature()));
            entitlementOrchestrator.enqueue(UUID.fromString(accountId), UUID.fromString(planUuid), UUID.fromString(featureUuid.toString()));
        }

        for (UUID featureUuid : uuidsToUpdate) {
            PlanFeatureRule planFeatureRule = retrievePlanFeatureRule(planUuid, featureUuid.toString());
            if (planFeatureRule == null) {
                continue;
            }
            PlanFeatureLinkedDiffRequest.LinkFeature link = requestMap.get(featureUuid).getLast();

            link.setValue(validateAndDefaultRuleValue(link.getValue()));

            boolean changed = false;
            if (link.getType() != null && !link.getType().equals(planFeatureRule.getType())) {
                planFeatureRule.setType(PlanFeatureRuleType.valueOf(link.getType()).name());
                changed = true;
            }
            if (link.getIsEnabled() != null && !link.getIsEnabled().equals(planFeatureRule.getIsEnabled())) {
                planFeatureRule.setIsEnabled(link.getIsEnabled());
                changed = true;
            }
            if (link.getValue() != null && !link.getValue().equals(planFeatureRule.getValue())) {
                planFeatureRule.setValue(link.getValue());
                changed = true;
            }

            UUID existingCreditModelId = planFeatureRule.getCreditModel() != null ? planFeatureRule.getCreditModel().getId() : null;
            if (link.getCreditModelId() != null && !link.getCreditModelId().equals(existingCreditModelId)) {
                resolveCreditModel(link.getCreditModelId(), UUID.fromString(accountId), planFeatureRule);
                changed = true;
            } else if (link.getCreditModelId() == null && existingCreditModelId != null) {
                planFeatureRule.setCreditModel(null);
                changed = true;
            }

            if (changed) {
                planFeatureRuleRepository.save(planFeatureRule);
                entitlementOrchestrator.enqueue(UUID.fromString(accountId), UUID.fromString(planUuid), UUID.fromString(featureUuid.toString()));
            }
        }

        for (UUID featureUuid : uuidsToRemove) {
            PlanFeatureRule planFeatureRule = retrievePlanFeatureRule(planUuid, featureUuid.toString());

            if (planFeatureRule == null) {
                continue;
            }

            planFeatureRuleRepository.delete(planFeatureRule);
            planFeatureLinkedDiffResponse.getRemovedFeatures().add(featureUuidMap.get(featureUuid));
            entitlementOrchestrator.enqueue(UUID.fromString(accountId), UUID.fromString(planUuid), UUID.fromString(featureUuid.toString()));
        }

        return planFeatureLinkedDiffResponse;
    }

    @Transactional
    @Override
    public void reconcilePlanFeature(UUID accountId,
                                     UUID planId,
                                     UUID featureId,
                                     UUID entitlementMetaId) {

        boolean planFeatureRuleExists = planFeatureRuleRepository.existsByPlanIdAndFeatureIdAndDeletedAtIsNull(planId, featureId);

        int changed;
        if (planFeatureRuleExists) {
            changed = entitlementRepository.insertMissingPlanRuleEntitlements(
                    accountId, planId, featureId, entitlementMetaId
            );
        } else {
            changed = entitlementRepository.deletePlanRuleEntitlements(
                    accountId, planId, featureId
            );
        }

        log.info("Reconciled plan feature {} for account {} with {} entitlements", featureId, accountId, changed);
    }

    private void resolveCreditModel(Object creditModelIdRaw, UUID accountId, PlanFeatureRule rule) {
        if (creditModelIdRaw == null) {
            return;
        }
        UUID creditModelId = creditModelIdRaw instanceof UUID ? (UUID) creditModelIdRaw : UUID.fromString(creditModelIdRaw.toString());
        CreditModel creditModel = creditModelRepository.findByIdAndAccountId(creditModelId, accountId)
                .orElseThrow(() -> new IllegalArgumentException("Credit model not found: " + creditModelId));
        rule.setCreditModel(creditModel);
    }

    private PlanFeatureRule retrievePlanFeatureRule(String planUuid, String featureUuid) {
        return planFeatureRuleRepository.findPlanFeatureRuleByPlan_IdAndFeature_Id(UUID.fromString(planUuid), UUID.fromString(featureUuid));
    }

    private void checkOwnership(String accountId, PlanFeatureRuleRequest planFeatureRuleRequest) {
        if (!featureService.isOwner(accountId, planFeatureRuleRequest.getFeatureId()) || !planService.isOwner(accountId, planFeatureRuleRequest.getPlanId())) {
            throw new IllegalArgumentException("Feature or Plan does not belong to the account");
        }
    }

}
