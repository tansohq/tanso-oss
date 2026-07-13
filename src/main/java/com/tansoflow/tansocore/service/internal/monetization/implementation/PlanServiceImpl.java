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
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.entity.PlanFeatureRule;
import com.tansoflow.tansocore.mapper.monetization.FeatureMapper;
import com.tansoflow.tansocore.mapper.monetization.PlanMapper;
import com.tansoflow.tansocore.model.credit.PlanCreditAllocationDto;
import com.tansoflow.tansocore.model.event.service.PlanCreatedEvent;
import com.tansoflow.tansocore.model.event.service.PlanUpdatedEvent;
import com.tansoflow.tansocore.model.feature.FeatureDto;
import com.tansoflow.tansocore.model.monetization.PlanFeatureLinkedDto;
import com.tansoflow.tansocore.model.monetization.request.UuidListRequest;
import com.tansoflow.tansocore.model.plan.BillingTiming;
import com.tansoflow.tansocore.model.plan.PlanDto;
import com.tansoflow.tansocore.model.plan.PlanStatus;
import com.tansoflow.tansocore.model.plan.request.PlanRequest;
import com.tansoflow.tansocore.repository.PlanFeatureRuleRepository;
import com.tansoflow.tansocore.repository.PlanRepository;
import com.tansoflow.tansocore.service.internal.account.AccountService;
import com.tansoflow.tansocore.service.internal.monetization.CreditService;
import com.tansoflow.tansocore.service.internal.monetization.FeatureService;
import com.tansoflow.tansocore.service.internal.monetization.PlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PlanServiceImpl implements PlanService {
    private final PlanMapper planMapper;
    private final PlanRepository planRepository;
    private final AccountService accountService;
    private final FeatureService featureService;
    private final PlanFeatureRuleRepository planFeatureRuleRepository;
    private final FeatureMapper featureMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final CreditService creditService;

    @Override
    public PlanDto createPlans(String accountId, PlanRequest planRequest) {
        Account account = accountService.retrieveAccount(accountId);
        Plan entity = planMapper.planRequestToPlanEntity(planRequest);

        if (entity.getKey() == null) {
            throw new IllegalArgumentException("Plan key cannot be null");
        }

        Boolean exists = planRepository.existsByKeyAndAccountId(entity.getKey(), account.getId());
        if (Boolean.TRUE.equals(exists)) {
            throw new IllegalArgumentException("Plan with key: " + entity.getKey() + " already exists");
        }

        String timing = planRequest.getBillingTiming();
        if (timing == null || timing.isBlank()) {
            timing = BillingTiming.IN_ADVANCE.name();
        }
        entity.setBillingTiming(timing);

        entity.setStatus(PlanStatus.DRAFT.name());
        entity.setAccount(account);

        Plan result = planRepository.save(entity);
        return planMapper.planEntityToPlanDto(result);
    }

    @Override
    @Transactional
    public PlanDto updatePlan(String accountId, String uuid, PlanRequest planRequest) {
        Account account = accountService.retrieveAccount(accountId);
        Plan plan = planRepository.findByIdAndAccount(UUID.fromString(uuid), account).orElseThrow(() -> new IllegalArgumentException("Plan not found with id: " + uuid));

        PlanStatus previousStatus = PlanStatus.valueOf(plan.getStatus());

        if (planRequest.getKey() != null && !planRequest.getKey().equals(plan.getKey())) {
            Boolean exists = planRepository.existsByKeyAndAccountId(planRequest.getKey(), account.getId());
            if (Boolean.TRUE.equals(exists)) {
                throw new IllegalArgumentException("Plan with key: " + planRequest.getKey() + " already exists");
            }
        }

        enforceFieldImmutability(plan, planRequest);

        planMapper.updatePlanFromPlanRequest(planRequest, plan);

        // Explicitly apply status (activate / archive / restore)
        if (planRequest.getStatus() != null) {
            PlanStatus requestedStatus;
            try {
                requestedStatus = PlanStatus.valueOf(planRequest.getStatus());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid plan status: " + planRequest.getStatus()
                        + ". Valid values: DRAFT, ACTIVE, ARCHIVED");
            }

            PlanStatus currentStatus = PlanStatus.valueOf(plan.getStatus());
            validateStatusTransition(currentStatus, requestedStatus);
            if (requestedStatus == PlanStatus.ACTIVE) {
                validatePlanReadyForActivation(plan);
            }
            plan.setStatus(planRequest.getStatus());
        }

        Plan updatedPlan = planRepository.save(plan);

        // Publish events for Stripe sync
        PlanStatus newStatus = PlanStatus.valueOf(updatedPlan.getStatus());
        if (newStatus == PlanStatus.ACTIVE && previousStatus != PlanStatus.ACTIVE) {
            eventPublisher.publishEvent(new PlanCreatedEvent(account.getId(), updatedPlan.getId()));
        } else if (newStatus == PlanStatus.ACTIVE) {
            eventPublisher.publishEvent(new PlanUpdatedEvent(account.getId(), updatedPlan.getId()));
        }

        return planMapper.planEntityToPlanDto(updatedPlan);
    }

    @Override
    @Transactional
    public void deletePlan(String uuid) {
        List<PlanFeatureRule> planFeatureRules = planFeatureRuleRepository.findPlanFeatureRulesByPlanId(UUID.fromString(uuid));
        planFeatureRuleRepository.deleteAll(planFeatureRules);
        planRepository.deleteById(UUID.fromString(uuid));
    }

    @Override
    public void deletePlans(UuidListRequest uuids) {
        try {
            Collection<UUID> uuidCollection = uuids.getIds().stream().map(UUID::fromString).toList();
            for (UUID uuid : uuidCollection) {
                planRepository.deleteById(uuid);
            }
        } catch (Exception e) {
            log.error("Unable to delete plan records for: {}", uuids);
        }
    }

    @Override
    public List<PlanDto> getPlans(String accountId) {
        List<Plan> plans = retrievePlansByAccountUuid(accountId);
        return planMapper.planEntityListToPlanDtoList(plans);
    }

    @Override
    public Plan retrievePlan(UUID accountId, UUID planUuid) {
        return retrievePlan(accountService.retrieveAccount(accountId.toString()), planUuid);
    }

    @Override
    public Plan retrievePlan(Account account, UUID planUuid) {
        return planRepository.findByIdAndAccount(planUuid, account).orElseThrow(() -> new IllegalArgumentException("Plan not found with id: " + planUuid.toString()));
    }

    @Override
    public PlanFeatureLinkedDto retrievePlanFeatureLinkByPlanUuid(String planUuid, String accountId) {
        PlanFeatureLinkedDto planFeatureLinkedDto = new PlanFeatureLinkedDto();

        Plan plan = retrievePlan(UUID.fromString(accountId), UUID.fromString(planUuid));
        List<FeatureDto> features = featureService.retrieveFeaturesLinkedToPlan(plan);

        planFeatureLinkedDto.setPlan(planMapper.planEntityToPlanDto(plan));
        planFeatureLinkedDto.setFeatures(features);
        populateCreditAllocations(planFeatureLinkedDto, planUuid, accountId);

        return planFeatureLinkedDto;
    }

    @Override
    public List<PlanFeatureLinkedDto> retrievePlanFeaturesMapByAccount(String accountUuid) {
        List<Plan> plans = retrievePlansByAccountUuid(accountUuid);
        Map<PlanDto, List<FeatureDto>> planFeatureMap = new HashMap<>();
        List<PlanFeatureRule> planFeatureRules = planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(plans);

        for (PlanFeatureRule planFeatureRule : planFeatureRules) {
            FeatureDto featureDto = featureMapper.featureEntityToFeatureDto(planFeatureRule.getFeature());
            PlanDto planDto = planMapper.planEntityToPlanDto(planFeatureRule.getPlan());

            if (!planFeatureMap.containsKey(planDto)) {
                planFeatureMap.put(planDto, new ArrayList<>());
            }
            planFeatureMap.get(planDto).add(featureDto);
        }

        List<PlanFeatureLinkedDto> planFeatureLinkedDtoList = new ArrayList<>();

        for (PlanDto planDto : planFeatureMap.keySet()) {
            PlanFeatureLinkedDto planFeatureLinkedDto = new PlanFeatureLinkedDto();
            planFeatureLinkedDto.setPlan(planDto);
            planFeatureLinkedDto.setFeatures(planFeatureMap.get(planDto));
            populateCreditAllocations(planFeatureLinkedDto, planDto.getId().toString(), accountUuid);
            planFeatureLinkedDtoList.add(planFeatureLinkedDto);
        }

        return planFeatureLinkedDtoList;
    }

    @Override
    public boolean isOwner(String accountId, String planId) {
        Account account = accountService.retrieveAccount(accountId);
        Plan plan = retrievePlan(account, UUID.fromString(planId));
        return plan.getAccount().getId().equals(account.getId());
    }

    private void populateCreditAllocations(PlanFeatureLinkedDto dto, String planId, String accountId) {
        List<PlanCreditAllocationDto> allocations = creditService.getCreditAllocationsForPlan(planId, accountId);
        if (allocations != null && !allocations.isEmpty()) {
            dto.setCreditAllocations(allocations);
        }
    }

    private List<Plan> retrievePlansByAccountUuid(String accountUuid) {
        Account account = accountService.retrieveAccount(accountUuid);
        return planRepository.findAllByAccount(account);
    }

    private void validatePlanReadyForActivation(Plan plan) {
        if (plan.getName() == null || plan.getName().isBlank()) {
            throw new IllegalArgumentException("Plan must have a name before activation");
        }
        if (plan.getDescription() == null || plan.getDescription().isBlank()) {
            throw new IllegalArgumentException("Plan must have a description before activation");
        }
        if (plan.getIntervalMonths() == null || plan.getIntervalMonths() <= 0) {
            throw new IllegalArgumentException("Plan must have a positive intervalMonths before activation");
        }
        if (plan.getPriceAmount() == null) {
            throw new IllegalArgumentException("Plan must have a priceAmount before activation");
        }
        List<PlanFeatureRule> linkedFeatures = planFeatureRuleRepository.findPlanFeatureRulesByPlanId(plan.getId());
        if (linkedFeatures == null || linkedFeatures.isEmpty()) {
            throw new IllegalArgumentException("Plan must have at least one linked feature before activation");
        }
    }

    private void enforceFieldImmutability(Plan plan, PlanRequest planRequest) {
        PlanStatus currentStatus = PlanStatus.valueOf(plan.getStatus());

        if (currentStatus == PlanStatus.ARCHIVED) {
            // Allow status-only changes (e.g. restoring ARCHIVED -> ACTIVE)
            boolean hasFieldChanges = planRequest.getKey() != null
                    || planRequest.getName() != null
                    || planRequest.getDescription() != null
                    || planRequest.getIntervalMonths() != null
                    || planRequest.getPriceAmount() != null
                    || planRequest.getBillingTiming() != null
                    || planRequest.getMetadata() != null;
            if (hasFieldChanges) {
                throw new IllegalArgumentException("Cannot modify an ARCHIVED plan");
            }
            return;
        }

        if (currentStatus == PlanStatus.ACTIVE) {
            if (planRequest.getKey() != null && !planRequest.getKey().equals(plan.getKey())) {
                throw new IllegalArgumentException("Cannot change 'key' on an ACTIVE plan");
            }
            if (planRequest.getIntervalMonths() != null
                    && !planRequest.getIntervalMonths().equals(String.valueOf(plan.getIntervalMonths()))) {
                throw new IllegalArgumentException("Cannot change 'intervalMonths' on an ACTIVE plan");
            }
            if (planRequest.getPriceAmount() != null
                    && planRequest.getPriceAmount().compareTo(plan.getPriceAmount()) != 0) {
                throw new IllegalArgumentException("Cannot change 'priceAmount' on an ACTIVE plan");
            }
            if (planRequest.getBillingTiming() != null
                    && !planRequest.getBillingTiming().equals(plan.getBillingTiming())) {
                throw new IllegalArgumentException("Cannot change 'billingTiming' on an ACTIVE plan");
            }
        }
    }

    /**
     * Allowed transitions:
     *   DRAFT  -> ACTIVE
     *   ACTIVE -> ARCHIVED
     *   ARCHIVED -> ACTIVE
     */
    private void validateStatusTransition(PlanStatus current, PlanStatus requested) {
        if (current == requested) return;

        boolean allowed = switch (current) {
            case DRAFT -> requested == PlanStatus.ACTIVE;
            case ACTIVE -> requested == PlanStatus.ARCHIVED;
            case ARCHIVED -> requested == PlanStatus.ACTIVE;
        };

        if (!allowed) {
            throw new IllegalArgumentException(
                    "Cannot transition plan from " + current + " to " + requested);
        }
    }
}
