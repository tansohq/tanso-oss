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
import com.tansoflow.tansocore.model.client.ClientFeaturePricingDto;
import com.tansoflow.tansocore.model.client.ClientPlanFeatureLinkedDto;
import com.tansoflow.tansocore.model.client.ClientPriceTierDto;
import com.tansoflow.tansocore.model.monetization.pricing.GraduatedPricingModel;
import com.tansoflow.tansocore.model.monetization.pricing.PricingModel;
import com.tansoflow.tansocore.model.monetization.pricing.SimpleUsageModel;
import com.tansoflow.tansocore.repository.PlanFeatureRuleRepository;
import com.tansoflow.tansocore.repository.PlanRepository;
import com.tansoflow.tansocore.model.credit.PlanCreditAllocationDto;
import com.tansoflow.tansocore.service.client.ClientPlanService;
import com.tansoflow.tansocore.service.internal.account.AccountService;
import com.tansoflow.tansocore.service.internal.monetization.CreditService;
import com.tansoflow.tansocore.util.monetization.RuleCalculationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ClientPlanServiceImpl implements ClientPlanService {
    private final AccountService accountService;
    private final PlanRepository planRepository;
    private final PlanFeatureRuleRepository planFeatureRuleRepository;
    private final ClientPlanMapper clientPlanMapper;
    private final CreditService creditService;

    @Override
    public List<ClientPlanFeatureLinkedDto> retrieveActivePlansWithPricing(String accountId) {
        Account account = accountService.retrieveAccount(accountId);
        List<Plan> activePlans = planRepository.findAllByAccountAndStatus(account, "ACTIVE");

        if (activePlans.isEmpty()) {
            return Collections.emptyList();
        }

        List<PlanFeatureRule> allRules = planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(activePlans);
        Map<UUID, List<PlanFeatureRule>> rulesByPlanId = allRules.stream()
                .collect(Collectors.groupingBy(rule -> rule.getPlan().getId()));

        List<ClientPlanFeatureLinkedDto> result = new ArrayList<>();
        for (Plan plan : activePlans) {
            ClientPlanFeatureLinkedDto linked = new ClientPlanFeatureLinkedDto();
            linked.setPlan(clientPlanMapper.planToClientPlanDto(plan));

            List<PlanFeatureRule> planRules = rulesByPlanId.getOrDefault(plan.getId(), Collections.emptyList());
            List<ClientFeatureDto> featureDtos = new ArrayList<>();

            for (PlanFeatureRule rule : planRules) {
                featureDtos.add(buildClientFeatureDto(rule));
            }

            linked.setFeatures(featureDtos);

            List<PlanCreditAllocationDto> allocations = creditService.getCreditAllocationsForPlan(plan.getId().toString(), accountId);
            if (allocations != null && !allocations.isEmpty()) {
                linked.setCreditAllocations(allocations);
            }

            result.add(linked);
        }

        return result;
    }

    private ClientFeatureDto buildClientFeatureDto(PlanFeatureRule rule) {
        Feature feature = rule.getFeature();
        PricingModel pricingModel = RuleCalculationUtil.extractPricingModel(rule);

        ClientFeatureDto dto = new ClientFeatureDto();
        dto.setId(feature.getId());
        dto.setName(feature.getName());
        dto.setKey(feature.getKey());
        dto.setDescription(feature.getDescription());

        if (pricingModel == null) {
            dto.setPricingType("included");
            dto.setPricing(null);
        } else if ("graduated".equals(pricingModel.getModel())) {
            dto.setPricingType("graduated");
            dto.setPricing(buildGraduatedPricing((GraduatedPricingModel) pricingModel));
        } else {
            dto.setPricingType("usage_based");
            dto.setPricing(buildUsagePricing(pricingModel));
        }

        return dto;
    }

    private ClientFeaturePricingDto buildUsagePricing(PricingModel pricingModel) {
        ClientFeaturePricingDto pricing = new ClientFeaturePricingDto();
        pricing.setModel(pricingModel.getModel());
        pricing.setUnitLabel(pricingModel.getUsageUnitType());
        pricing.setMaxUsage(pricingModel.getMaxUsage());
        pricing.setResetMode(pricingModel.getResetMode());

        if (pricingModel instanceof SimpleUsageModel simpleModel) {
            pricing.setPricePerUnit(simpleModel.getPricePerUnit());
        }

        return pricing;
    }

    private ClientFeaturePricingDto buildGraduatedPricing(GraduatedPricingModel graduatedModel) {
        ClientFeaturePricingDto pricing = new ClientFeaturePricingDto();
        pricing.setModel(graduatedModel.getModel());
        pricing.setUnitLabel(graduatedModel.getUsageUnitType());
        pricing.setMaxUsage(graduatedModel.getMaxUsage());
        pricing.setResetMode(graduatedModel.getResetMode());

        if (graduatedModel.getTiers() != null) {
            List<ClientPriceTierDto> tierDtos = graduatedModel.getTiers().stream()
                    .map(tier -> {
                        ClientPriceTierDto tierDto = new ClientPriceTierDto();
                        tierDto.setUpTo(tier.getUpTo());
                        tierDto.setPricePerUnit(tier.getPricePerUnit());
                        tierDto.setFlatFee(tier.getFlatFee());
                        return tierDto;
                    })
                    .collect(Collectors.toList());
            pricing.setTiers(tierDtos);
        }

        return pricing;
    }
}
