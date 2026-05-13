package com.tansoflow.tansocore.service.internal.monetization.implementation;

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.Feature;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.entity.PlanFeatureRule;
import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.model.event.events.type.EventType;
import com.tansoflow.tansocore.model.monetization.pricing.GraduatedPricingModel;
import com.tansoflow.tansocore.model.monetization.pricing.PricingModel;
import com.tansoflow.tansocore.model.plan.response.FeatureRevenueDto;
import com.tansoflow.tansocore.model.plan.response.PlanRevenuePagedResponse;
import com.tansoflow.tansocore.model.plan.response.PlanRevenueResponse;
import com.tansoflow.tansocore.model.plan.response.SubscriptionRevenueDto;
import com.tansoflow.tansocore.repository.EventRepository;
import com.tansoflow.tansocore.repository.PlanFeatureRuleRepository;
import com.tansoflow.tansocore.repository.PlanRepository;
import com.tansoflow.tansocore.repository.SubscriptionRepository;
import com.tansoflow.tansocore.service.internal.account.AccountService;
import com.tansoflow.tansocore.service.internal.monetization.PlanRevenueService;
import com.tansoflow.tansocore.util.monetization.RuleCalculationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PlanRevenueServiceImpl implements PlanRevenueService {
    private final AccountService accountService;
    private final PlanRepository planRepository;
    private final PlanFeatureRuleRepository planFeatureRuleRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final EventRepository eventRepository;

    private static final Set<EventType> BILLABLE_EVENT_TYPES = Set.of(
            EventType.CLIENT_TRACKED, EventType.ENTITLEMENT_CHECKED
    );

    @Override
    @Transactional(readOnly = true)
    public PlanRevenueResponse getPlanRevenue(String accountId, UUID planId, Instant periodStart, Instant periodEnd, int page, int size, UUID subscriptionId) {
        Account account = accountService.retrieveAccount(accountId);
        Plan plan = planRepository.findByIdAndAccount(planId, account)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found with id: " + planId));

        List<PlanFeatureRule> featureRules = planFeatureRuleRepository.findPlanFeatureRulesByPlanId(planId);

        Page<Subscription> subscriptionPage;
        if (subscriptionId != null) {
            subscriptionPage = subscriptionRepository.findSubscriptionActiveDuringPeriodByPlanAndId(
                    account.getId(), planId, subscriptionId, periodStart, periodEnd, PageRequest.of(page, size));
        } else {
            subscriptionPage = subscriptionRepository.findSubscriptionsActiveDuringPeriodByPlan(
                    account.getId(), planId, periodStart, periodEnd, PageRequest.of(page, size));
        }

        List<SubscriptionRevenueDto> subscriptionDtos;
        if (subscriptionPage.isEmpty() || featureRules.isEmpty()) {
            subscriptionDtos = List.of();
        } else {
            subscriptionDtos = buildSubscriptionRevenueDtos(subscriptionPage.getContent(), featureRules, periodStart, periodEnd);
        }

        // Compute aggregate totals across ALL subscriptions (not just current page)
        BigDecimal aggregateTotalUnits = BigDecimal.ZERO;
        BigDecimal aggregateTotalRevenue = BigDecimal.ZERO;

        if (!featureRules.isEmpty() && subscriptionPage.getTotalElements() > 0) {
            if (subscriptionPage.getTotalPages() <= 1) {
                // Only one page - aggregate from what we already computed
                for (SubscriptionRevenueDto dto : subscriptionDtos) {
                    aggregateTotalUnits = aggregateTotalUnits.add(dto.getTotalUnits());
                    aggregateTotalRevenue = aggregateTotalRevenue.add(dto.getTotalRevenue());
                }
            } else {
                // Multiple pages - compute aggregates across all subscriptions
                AggregateResult aggregate = computeAggregatesForAllSubscriptions(account.getId(), planId, featureRules, periodStart, periodEnd);
                aggregateTotalUnits = aggregate.totalUnits;
                aggregateTotalRevenue = aggregate.totalRevenue;
            }
        }

        PlanRevenuePagedResponse pagedResponse = PlanRevenuePagedResponse.fromPage(subscriptionPage, subscriptionDtos);

        return PlanRevenueResponse.builder()
                .planId(plan.getId())
                .planName(plan.getName())
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .aggregateTotalUnits(aggregateTotalUnits)
                .aggregateTotalRevenue(aggregateTotalRevenue)
                .subscriptions(pagedResponse)
                .build();
    }

    private List<SubscriptionRevenueDto> buildSubscriptionRevenueDtos(
            List<Subscription> subscriptions, List<PlanFeatureRule> featureRules,
            Instant periodStart, Instant periodEnd) {

        Set<UUID> subscriptionIds = subscriptions.stream()
                .map(Subscription::getId)
                .collect(Collectors.toSet());

        Set<UUID> featureIds = featureRules.stream()
                .map(r -> r.getFeature().getId())
                .collect(Collectors.toSet());

        // Batch query: usage grouped by subscription + feature for the period
        Map<String, BigDecimal> usageMap = buildUsageMap(subscriptionIds, featureIds, periodStart, periodEnd);

        // Check if any rules use accumulate mode with graduated pricing
        boolean hasAccumulateGraduated = featureRules.stream().anyMatch(rule -> {
            PricingModel pm = RuleCalculationUtil.extractPricingModel(rule);
            return pm != null && pm.isAccumulateMode() && pm instanceof GraduatedPricingModel;
        });

        // Build cumulative usage map if needed (from earliest subscription creation to period end)
        Map<String, BigDecimal> cumulativeUsageMap = Map.of();
        if (hasAccumulateGraduated) {
            Instant earliestCreatedAt = subscriptions.stream()
                    .map(Subscription::getCreatedAt)
                    .min(Instant::compareTo)
                    .orElse(periodStart);
            cumulativeUsageMap = buildUsageMap(subscriptionIds, featureIds, earliestCreatedAt, periodEnd);
        }

        List<SubscriptionRevenueDto> result = new ArrayList<>();
        for (Subscription subscription : subscriptions) {
            List<FeatureRevenueDto> featureDtos = new ArrayList<>();
            BigDecimal subTotalUnits = BigDecimal.ZERO;
            BigDecimal subTotalRevenue = BigDecimal.ZERO;

            for (PlanFeatureRule rule : featureRules) {
                Feature feature = rule.getFeature();
                String key = subscription.getId() + ":" + feature.getId();
                BigDecimal units = usageMap.getOrDefault(key, BigDecimal.ZERO);

                PricingModel pricingModel = RuleCalculationUtil.extractPricingModel(rule);
                BigDecimal revenue;
                String modelName;
                String resetMode = null;

                if (pricingModel != null) {
                    modelName = pricingModel.getModel();
                    resetMode = pricingModel.getResetMode();

                    if (pricingModel.isAccumulateMode() && pricingModel instanceof GraduatedPricingModel graduatedModel) {
                        BigDecimal cumulativeTotal = cumulativeUsageMap.getOrDefault(key, BigDecimal.ZERO);
                        revenue = graduatedModel.calculateIncrementalCost(cumulativeTotal, units);
                    } else {
                        revenue = pricingModel.calculateCost(units);
                    }
                } else {
                    revenue = BigDecimal.ZERO;
                    modelName = null;
                }

                featureDtos.add(FeatureRevenueDto.builder()
                        .featureId(feature.getId())
                        .featureKey(feature.getKey())
                        .featureName(feature.getName())
                        .periodStart(periodStart)
                        .periodEnd(periodEnd)
                        .units(units)
                        .revenue(revenue)
                        .model(modelName)
                        .resetMode(resetMode)
                        .build());

                subTotalUnits = subTotalUnits.add(units);
                subTotalRevenue = subTotalRevenue.add(revenue);
            }

            String customerName = subscription.getCustomer().getFirstName() + " " + subscription.getCustomer().getLastName();
            result.add(SubscriptionRevenueDto.builder()
                    .subscriptionId(subscription.getId())
                    .customerId(subscription.getCustomer().getId())
                    .customerName(customerName)
                    .features(featureDtos)
                    .totalUnits(subTotalUnits)
                    .totalRevenue(subTotalRevenue)
                    .build());
        }

        return result;
    }

    private Map<String, BigDecimal> buildUsageMap(Set<UUID> subscriptionIds, Set<UUID> featureIds,
                                                   Instant periodStart, Instant periodEnd) {
        List<Object[]> rows = eventRepository.sumUsageGroupedBySubscriptionAndFeatureIncludingUntagged(
                subscriptionIds, featureIds, BILLABLE_EVENT_TYPES, periodStart, periodEnd);

        Map<String, BigDecimal> usageMap = new HashMap<>();
        for (Object[] row : rows) {
            UUID subId = (UUID) row[0];
            UUID featId = (UUID) row[1];
            BigDecimal sum = (BigDecimal) row[2];
            usageMap.put(subId + ":" + featId, sum);
        }
        return usageMap;
    }

    private AggregateResult computeAggregatesForAllSubscriptions(
            UUID accountId, UUID planId, List<PlanFeatureRule> featureRules,
            Instant periodStart, Instant periodEnd) {

        // Fetch all subscription IDs for this plan in the period (unpaginated)
        Page<Subscription> allSubs = subscriptionRepository.findSubscriptionsActiveDuringPeriodByPlan(
                accountId, planId, periodStart, periodEnd, PageRequest.of(0, Integer.MAX_VALUE));

        Set<UUID> allSubIds = allSubs.getContent().stream()
                .map(Subscription::getId)
                .collect(Collectors.toSet());

        Set<UUID> featureIds = featureRules.stream()
                .map(r -> r.getFeature().getId())
                .collect(Collectors.toSet());

        Map<String, BigDecimal> usageMap = buildUsageMap(allSubIds, featureIds, periodStart, periodEnd);

        // Check if any rules use accumulate mode with graduated pricing
        boolean hasAccumulateGraduated = featureRules.stream().anyMatch(rule -> {
            PricingModel pm = RuleCalculationUtil.extractPricingModel(rule);
            return pm != null && pm.isAccumulateMode() && pm instanceof GraduatedPricingModel;
        });

        Map<String, BigDecimal> cumulativeUsageMap = Map.of();
        if (hasAccumulateGraduated) {
            Instant earliestCreatedAt = allSubs.getContent().stream()
                    .map(Subscription::getCreatedAt)
                    .min(Instant::compareTo)
                    .orElse(periodStart);
            cumulativeUsageMap = buildUsageMap(allSubIds, featureIds, earliestCreatedAt, periodEnd);
        }

        BigDecimal totalUnits = BigDecimal.ZERO;
        BigDecimal totalRevenue = BigDecimal.ZERO;

        for (Subscription sub : allSubs.getContent()) {
            for (PlanFeatureRule rule : featureRules) {
                String key = sub.getId() + ":" + rule.getFeature().getId();
                BigDecimal units = usageMap.getOrDefault(key, BigDecimal.ZERO);

                PricingModel pricingModel = RuleCalculationUtil.extractPricingModel(rule);
                BigDecimal revenue;
                if (pricingModel != null && pricingModel.isAccumulateMode() && pricingModel instanceof GraduatedPricingModel graduatedModel) {
                    BigDecimal cumulativeTotal = cumulativeUsageMap.getOrDefault(key, BigDecimal.ZERO);
                    revenue = graduatedModel.calculateIncrementalCost(cumulativeTotal, units);
                } else {
                    revenue = pricingModel != null ? pricingModel.calculateCost(units) : BigDecimal.ZERO;
                }

                totalUnits = totalUnits.add(units);
                totalRevenue = totalRevenue.add(revenue);
            }
        }

        return new AggregateResult(totalUnits, totalRevenue);
    }

    private record AggregateResult(BigDecimal totalUnits, BigDecimal totalRevenue) {}
}
