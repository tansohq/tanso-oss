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
package com.tansoflow.tansocore.service.internal.analytics.implementation;

import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.Feature;
import com.tansoflow.tansocore.entity.Entitlement;
import com.tansoflow.tansocore.entity.Invoice;
import com.tansoflow.tansocore.entity.InvoiceItem;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.entity.PlanFeatureRule;
import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.entity.SubscriptionScheduledChange;
import com.tansoflow.tansocore.model.analytics.AnalyticsResponseDto;
import com.tansoflow.tansocore.model.analytics.ChurnScoreDto;
import com.tansoflow.tansocore.model.analytics.CreditImpactDto;
import com.tansoflow.tansocore.model.analytics.CustomerAnalyticsDto;
import com.tansoflow.tansocore.model.analytics.FeatureProfitabilityDto;
import com.tansoflow.tansocore.model.analytics.ModelProfitabilityDto;
import com.tansoflow.tansocore.model.analytics.ModelSummaryDto;
import com.tansoflow.tansocore.model.analytics.ModelsAnalyticsResponseDto;
import com.tansoflow.tansocore.model.analytics.PortfolioSummaryDto;
import com.tansoflow.tansocore.model.analytics.RevenueBridgePeriodDto;
import com.tansoflow.tansocore.model.analytics.RevenueBridgeResponseDto;
import com.tansoflow.tansocore.model.analytics.RevenueBurnDownDto;
import com.tansoflow.tansocore.model.event.events.type.EventType;
import com.tansoflow.tansocore.model.monetization.cost.CostModel;
import com.tansoflow.tansocore.model.monetization.pricing.PricingModel;
import com.tansoflow.tansocore.entity.StripeCustomer;
import com.tansoflow.tansocore.repository.CustomerRepository;
import com.tansoflow.tansocore.repository.StripeCustomerRepository;
import com.tansoflow.tansocore.repository.EntitlementRepository;
import com.tansoflow.tansocore.repository.EventRepository;
import com.tansoflow.tansocore.repository.FeatureRepository;
import com.tansoflow.tansocore.repository.InvoiceItemRepository;
import com.tansoflow.tansocore.repository.InvoiceRepository;
import com.tansoflow.tansocore.repository.PlanFeatureRuleRepository;

import com.tansoflow.tansocore.repository.SubscriptionRepository;
import com.tansoflow.tansocore.repository.SubscriptionScheduledChangeRepository;
import com.tansoflow.tansocore.service.internal.analytics.AnalyticsService;
import com.tansoflow.tansocore.util.monetization.RuleCalculationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {
    private static final BigDecimal HEALTHY_THRESHOLD = new BigDecimal("0.70");
    private static final BigDecimal AT_RISK_THRESHOLD = new BigDecimal("0.40");

    private final SubscriptionRepository subscriptionRepository;
    private final PlanFeatureRuleRepository planFeatureRuleRepository;
    private final EventRepository eventRepository;
    private final SubscriptionScheduledChangeRepository scheduledChangeRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final EntitlementRepository entitlementRepository;
    private final CustomerRepository customerRepository;
    private final FeatureRepository featureRepository;
    private final StripeCustomerRepository stripeCustomerRepository;


    @Override
    public AnalyticsResponseDto getPortfolioAnalytics(String accountId) {
        UUID accountUuid = UUID.fromString(accountId);

        List<Subscription> activeSubscriptions = subscriptionRepository.findActiveSubscriptionsByAccountId(accountUuid);

        List<Plan> plans = activeSubscriptions.stream()
                .map(Subscription::getPlan)
                .distinct()
                .toList();

        List<PlanFeatureRule> allRules = planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(plans);
        Map<PlanFeatureKey, PlanFeatureRule> ruleMap = allRules.stream()
                .collect(HashMap::new, (m, r) -> m.put(new PlanFeatureKey(r.getPlan().getId(), r.getFeature().getId()), r), HashMap::putAll);

        List<SubscriptionScheduledChange> pendingChanges = scheduledChangeRepository.findAllPendingChangesByAccountId(accountUuid);
        Map<UUID, SubscriptionScheduledChange> scheduledChangeMap = pendingChanges.stream()
                .collect(Collectors.toMap(
                        sc -> sc.getSubscription().getId(),
                        sc -> sc,
                        (existing, replacement) -> existing
                ));

        List<CustomerAnalyticsDto> customerAnalytics = new ArrayList<>();

        for (Subscription subscription : activeSubscriptions) {
            Customer customer = subscription.getCustomer();
            Plan plan = subscription.getPlan();

            BigDecimal mrr = normalizeMrr(plan);

            List<FeatureProfitabilityDto> featureProfitability = calculateFeatureProfitability(subscription, ruleMap);

            BigDecimal projectedUsageRevenue = featureProfitability.stream()
                    .map(FeatureProfitabilityDto::getRevenue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean hasCostModel = featureProfitability.stream()
                    .anyMatch(fp -> fp.getCost() != null);
            BigDecimal totalCost = hasCostModel
                    ? featureProfitability.stream()
                            .map(fp -> fp.getCost() != null ? fp.getCost() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                    : null;
            BigDecimal effectiveMrr = mrr.add(projectedUsageRevenue);

            BigDecimal rgp = totalCost != null ? effectiveMrr.subtract(totalCost) : null;
            BigDecimal margin = totalCost != null ? calculateMargin(effectiveMrr, totalCost) : null;
            String marginStatus = margin != null ? determineMarginStatus(margin) : null;

            String churnRisk = null;
            Instant cancelEffectiveAt = subscription.getCancelEffectiveAt();
            if (cancelEffectiveAt != null) {
                churnRisk = "pending_cancel";
            } else {
                SubscriptionScheduledChange scheduledChange = scheduledChangeMap.get(subscription.getId());
                if (scheduledChange != null && "DOWNGRADE".equals(scheduledChange.getType())) {
                    churnRisk = "pending_downgrade";
                }
            }

            String customerName = buildCustomerName(customer);

            CustomerAnalyticsDto dto = CustomerAnalyticsDto.builder()
                    .customerId(customer.getId())
                    .customerName(customerName)
                    .email(customer.getEmail())
                    .customerReferenceId(customer.getExternalClientCustomerId())
                    .planId(plan.getId())
                    .planName(plan.getName())
                    .mrr(mrr)
                    .totalCost(totalCost)
                    .rgp(rgp)
                    .margin(margin)
                    .marginStatus(marginStatus)
                    .featureProfitability(featureProfitability)
                    .projectedUsageRevenue(projectedUsageRevenue)
                    .effectiveMrr(effectiveMrr)
                    .churnRisk(churnRisk)
                    .cancelEffectiveAt(cancelEffectiveAt)
                    .build();

            customerAnalytics.add(dto);
        }

        // Event-only customers: those with events but no active subscription
        appendEventOnlyCustomers(accountUuid, customerAnalytics, activeSubscriptions);

        if (customerAnalytics.isEmpty()) {
            return buildEmptyResponse();
        }

        // Second pass: churn scoring (batch queries for entitlements and events)
        // Only score subscription-based customers
        if (!activeSubscriptions.isEmpty()) {
            calculateChurnScores(customerAnalytics.stream()
                    .filter(c -> c.getPlanId() != null)
                    .collect(Collectors.toList()), activeSubscriptions, plans);
        }

        // Credit impact: per-customer credits from CREDIT invoices
        List<Invoice> allInvoicesForAccount = invoiceRepository.getInvoicesByAccount_Id(accountUuid);
        for (CustomerAnalyticsDto dto : customerAnalytics) {
            BigDecimal credits = calculateCustomerCredits(allInvoicesForAccount, dto.getCustomerId());
            dto.setTotalCredits(credits);
        }

        PortfolioSummaryDto summary = calculatePortfolioSummary(accountUuid, customerAnalytics, activeSubscriptions);

        // Portfolio-level credit impact
        CreditImpactDto creditImpact = calculateCreditImpact(allInvoicesForAccount, summary.getTotalEffectiveMrr());
        summary.setCreditImpact(creditImpact);

        return AnalyticsResponseDto.builder()
                .summary(summary)
                .customers(customerAnalytics)
                .build();
    }

    private record PlanFeatureKey(UUID planId, UUID featureId) {}

    private BigDecimal normalizeMrr(Plan plan) {
        BigDecimal priceAmount = plan.getPriceAmount() != null ? plan.getPriceAmount() : BigDecimal.ZERO;
        int intervalMonths = plan.getIntervalMonths() != null && plan.getIntervalMonths() > 0
                ? plan.getIntervalMonths() : 1;
        return priceAmount.divide(BigDecimal.valueOf(intervalMonths), 4, RoundingMode.HALF_UP);
    }

    private List<FeatureProfitabilityDto> calculateFeatureProfitability(
            Subscription subscription,
            Map<PlanFeatureKey, PlanFeatureRule> ruleMap) {

        Instant start = subscription.getCurrentPeriodStart();
        Instant end = subscription.getCurrentPeriodEnd();
        if (start == null || end == null) {
            start = Instant.now().minus(30, ChronoUnit.DAYS);
            end = Instant.now();
        }

        UUID planId = subscription.getPlan().getId();

        List<UUID> featureIds = ruleMap.keySet().stream()
                .filter(key -> key.planId().equals(planId))
                .map(PlanFeatureKey::featureId)
                .toList();

        if (featureIds.isEmpty()) {
            return List.of();
        }

        List<Object[]> usageRows = eventRepository.sumUsageGroupedBySubscriptionAndFeatureIncludingUntagged(
                List.of(subscription.getId()),
                featureIds,
                List.of(EventType.ENTITLEMENT_CHECKED, EventType.CLIENT_TRACKED),
                start,
                end
        );

        // Row shape: [0]=subId, [1]=featureId, [2]=usageUnits

        List<FeatureProfitabilityDto> results = new ArrayList<>();

        for (Object[] row : usageRows) {
            UUID featureId = (UUID) row[1];
            BigDecimal usageUnits = (BigDecimal) row[2];

            if (usageUnits.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            PlanFeatureRule rule = ruleMap.get(new PlanFeatureKey(planId, featureId));
            if (rule == null) {
                continue;
            }

            BigDecimal totalRevenue = BigDecimal.ZERO;
            PricingModel pricingModel = RuleCalculationUtil.extractPricingModel(rule);
            if (pricingModel != null) {
                totalRevenue = pricingModel.calculateCost(usageUnits);
            }

            BigDecimal totalCost = null;
            CostModel costModel = RuleCalculationUtil.extractCostModel(rule);
            if (costModel != null) {
                totalCost = costModel.calculateCostAmount(usageUnits);
            }

            results.add(FeatureProfitabilityDto.builder()
                    .featureId(featureId)
                    .featureName(rule.getFeature().getName())
                    .featureKey(rule.getFeature().getKey())
                    .usageUnits(usageUnits)
                    .revenue(totalRevenue)
                    .cost(totalCost)
                    .margin(totalCost != null ? totalRevenue.subtract(totalCost) : null)
                    .modelBreakdown(null)
                    .build());
        }

        return results;
    }

    private BigDecimal calculateMargin(BigDecimal mrr, BigDecimal cost) {
        if (mrr.compareTo(BigDecimal.ZERO) == 0) {
            return cost.compareTo(BigDecimal.ZERO) > 0
                    ? new BigDecimal("-1.0000")
                    : BigDecimal.ZERO;
        }
        return mrr.subtract(cost).divide(mrr, 4, RoundingMode.HALF_UP);
    }

    private String determineMarginStatus(BigDecimal margin) {
        if (margin.compareTo(HEALTHY_THRESHOLD) >= 0) {
            return "healthy";
        } else if (margin.compareTo(AT_RISK_THRESHOLD) >= 0) {
            return "at_risk";
        } else {
            return "underwater";
        }
    }

    private String buildCustomerName(Customer customer) {
        if (customer.getEmail() != null && !customer.getEmail().isBlank()) return customer.getEmail();
        String firstName = customer.getFirstName() != null ? customer.getFirstName() : "";
        String lastName = customer.getLastName() != null ? customer.getLastName() : "";
        String fullName = (firstName + " " + lastName).trim();
        if (!fullName.isEmpty()) return fullName;
        if (customer.getExternalClientCustomerId() != null) return customer.getExternalClientCustomerId();
        StripeCustomer sc = stripeCustomerRepository.findByCustomer(customer);
        if (sc != null && sc.getStripeCustomerExternalId() != null) return sc.getStripeCustomerExternalId();
        return customer.getId().toString();
    }

    private void appendEventOnlyCustomers(UUID accountUuid, List<CustomerAnalyticsDto> customerAnalytics,
                                          List<Subscription> activeSubscriptions) {
        Set<UUID> subscribedCustomerIds = activeSubscriptions.stream()
                .map(s -> s.getCustomer().getId())
                .collect(Collectors.toSet());

        // Use a sentinel to avoid empty IN clause
        if (subscribedCustomerIds.isEmpty()) {
            subscribedCustomerIds = Set.of(UUID.fromString("00000000-0000-0000-0000-000000000000"));
        }

        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
        List<EventType> trackingTypes = List.of(EventType.CLIENT_TRACKED, EventType.ENTITLEMENT_CHECKED);

        List<UUID> eventOnlyIds = eventRepository.findEventOnlyCustomerIds(
                accountUuid, subscribedCustomerIds, trackingTypes, since);

        if (eventOnlyIds.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        List<Object[]> aggregates = eventRepository.sumRevenueAndCostByCustomer(
                accountUuid, eventOnlyIds, trackingTypes, since, now);

        Map<UUID, BigDecimal[]> revenueAndCost = new HashMap<>();
        for (Object[] row : aggregates) {
            UUID customerId = (UUID) row[0];
            BigDecimal revenue = (BigDecimal) row[1];
            BigDecimal cost = (BigDecimal) row[2];
            revenueAndCost.put(customerId, new BigDecimal[]{revenue, cost});
        }

        // Feature breakdown per customer
        List<Object[]> featureRows = eventRepository.sumUsageRevenueAndCostByCustomerAndFeature(
                accountUuid, eventOnlyIds, trackingTypes, since, now);

        // Collect all feature IDs for name lookup
        Set<UUID> allFeatureIds = featureRows.stream()
                .map(row -> (UUID) row[1])
                .collect(Collectors.toSet());
        Map<UUID, String> featureNames = new HashMap<>();
        Map<UUID, String> featureKeys = new HashMap<>();
        if (!allFeatureIds.isEmpty()) {
            featureRepository.findAllById(allFeatureIds)
                    .forEach(f -> {
                        featureNames.put(f.getId(), f.getName());
                        featureKeys.put(f.getId(), f.getKey());
                    });
        }

        // Model breakdown per customer+feature
        List<Object[]> modelRows = eventRepository.sumUsageRevenueAndCostByCustomerFeatureAndModel(
                accountUuid, eventOnlyIds, trackingTypes, since, now);
        // Key: "custId|featureId" -> list of model breakdowns
        Map<String, List<ModelProfitabilityDto>> modelsByCustomerFeature = new HashMap<>();
        for (Object[] row : modelRows) {
            UUID custId = (UUID) row[0];
            UUID featId = (UUID) row[1];
            String model = (String) row[2];
            String provider = (String) row[3];
            BigDecimal mUsage = (BigDecimal) row[4];
            BigDecimal mCost = (BigDecimal) row[5];
            BigDecimal mRevenue = (BigDecimal) row[6];
            BigDecimal mMargin = mRevenue.compareTo(BigDecimal.ZERO) > 0
                    ? mRevenue.subtract(mCost).divide(mRevenue, 4, java.math.RoundingMode.HALF_UP)
                    : null;
            String key = custId + "|" + featId;
            modelsByCustomerFeature.computeIfAbsent(key, k -> new ArrayList<>()).add(
                    ModelProfitabilityDto.builder()
                            .model(model).modelProvider(provider)
                            .usageUnits(mUsage).cost(mCost).revenue(mRevenue).margin(mMargin)
                            .build());
        }

        // Group feature rows by customer
        Map<UUID, List<FeatureProfitabilityDto>> featuresByCustomer = new HashMap<>();
        for (Object[] row : featureRows) {
            UUID custId = (UUID) row[0];
            UUID featureId = (UUID) row[1];
            BigDecimal usageUnits = (BigDecimal) row[2];
            BigDecimal cost = (BigDecimal) row[3];
            BigDecimal revenue = (BigDecimal) row[4];

            String key = custId + "|" + featureId;
            FeatureProfitabilityDto fp = FeatureProfitabilityDto.builder()
                    .featureId(featureId)
                    .featureName(featureNames.getOrDefault(featureId, featureId.toString()))
                    .featureKey(featureKeys.getOrDefault(featureId, featureId.toString()))
                    .usageUnits(usageUnits)
                    .revenue(revenue)
                    .cost(cost)
                    .margin(revenue.subtract(cost))
                    .modelBreakdown(modelsByCustomerFeature.get(key))
                    .build();

            featuresByCustomer.computeIfAbsent(custId, k -> new ArrayList<>()).add(fp);
        }

        // Load customer entities for names
        List<Customer> eventOnlyCustomers = customerRepository.findAllById(eventOnlyIds);
        Map<UUID, Customer> customerMap = eventOnlyCustomers.stream()
                .collect(Collectors.toMap(Customer::getId, c -> c));

        // Stripe revenue enrichment: for event-only customers with no event-level revenue,
        // look up their Stripe-linked subscription MRR as a fallback.
        Map<UUID, BigDecimal> stripeRevenueFallback = resolveStripeRevenueFallback(eventOnlyIds, customerMap, accountUuid);

        for (UUID customerId : eventOnlyIds) {
            BigDecimal[] amounts = revenueAndCost.getOrDefault(customerId, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal eventRevenue = amounts[0];
            BigDecimal totalCost = amounts[1];

            // Use event-level revenue if available, otherwise fall back to Stripe subscription MRR
            boolean usingStripeFallback = eventRevenue.compareTo(BigDecimal.ZERO) == 0
                    && stripeRevenueFallback.containsKey(customerId);
            BigDecimal effectiveMrr = eventRevenue.compareTo(BigDecimal.ZERO) > 0
                    ? eventRevenue
                    : stripeRevenueFallback.getOrDefault(customerId, BigDecimal.ZERO);

            // When using Stripe MRR fallback, distribute revenue across features proportional
            // to their cost share. This gives meaningful per-feature margins instead of showing
            // zero revenue against real costs.
            List<FeatureProfitabilityDto> features = featuresByCustomer.getOrDefault(customerId, List.of());
            if (usingStripeFallback && effectiveMrr.compareTo(BigDecimal.ZERO) > 0 && !features.isEmpty()) {
                features = distributeRevenueByFeatureCost(features, effectiveMrr);
                featuresByCustomer.put(customerId, features);
            }

            // For event-only customers, totalCost comes from COALESCE(SUM, 0) — ZERO means no cost data,
            // not "cost is zero". Only show margin/RGP when cost data actually exists.
            boolean hasCostData = totalCost.compareTo(BigDecimal.ZERO) > 0;
            BigDecimal margin = hasCostData ? calculateMargin(effectiveMrr, totalCost) : null;
            String marginStatus = margin != null ? determineMarginStatus(margin) : null;

            Customer customer = customerMap.get(customerId);
            String customerName = customer != null ? buildCustomerName(customer) : customerId.toString();
            String email = customer != null ? customer.getEmail() : null;

            // Aggregate model profitability across all features for this customer,
            // merging rows with the same model+provider
            Map<String, ModelProfitabilityDto> mergedModels = new HashMap<>();
            featuresByCustomer.getOrDefault(customerId, List.of()).stream()
                    .filter(f -> f.getModelBreakdown() != null)
                    .flatMap(f -> f.getModelBreakdown().stream())
                    .forEach(m -> {
                        String key = (m.getModel() != null ? m.getModel() : "") + "|" + (m.getModelProvider() != null ? m.getModelProvider() : "");
                        mergedModels.merge(key, m, (existing, incoming) -> {
                            BigDecimal cost = existing.getCost().add(incoming.getCost());
                            BigDecimal revenue = existing.getRevenue().add(incoming.getRevenue());
                            BigDecimal usage = existing.getUsageUnits().add(incoming.getUsageUnits());
                            BigDecimal mrg = revenue.compareTo(BigDecimal.ZERO) > 0
                                    ? revenue.subtract(cost).divide(revenue, 4, java.math.RoundingMode.HALF_UP)
                                    : null;
                            return ModelProfitabilityDto.builder()
                                    .model(existing.getModel()).modelProvider(existing.getModelProvider())
                                    .usageUnits(usage).cost(cost).revenue(revenue).margin(mrg)
                                    .build();
                        });
                    });
            List<ModelProfitabilityDto> customerModels = new ArrayList<>(mergedModels.values());

            customerAnalytics.add(CustomerAnalyticsDto.builder()
                    .customerId(customerId)
                    .customerName(customerName)
                    .email(email)
                    .customerReferenceId(customer != null ? customer.getExternalClientCustomerId() : null)
                    .planId(null)
                    .planName(null)
                    .mrr(effectiveMrr)
                    .totalCost(hasCostData ? totalCost : null)
                    .rgp(hasCostData ? effectiveMrr.subtract(totalCost) : null)
                    .margin(margin)
                    .marginStatus(marginStatus)
                    .featureProfitability(featuresByCustomer.getOrDefault(customerId, List.of()))
                    .modelProfitability(customerModels.isEmpty() ? null : customerModels)
                    .projectedUsageRevenue(BigDecimal.ZERO)
                    .effectiveMrr(effectiveMrr)
                    .build());
        }
    }

    /**
     * For event-only customers (no Tanso subscription), check if they have a Stripe-linked
     * subscription. If so, use the plan's MRR as the revenue fallback for margin calculation.
     * This enables Observe users who connect Stripe to see margin data without manually
     * sending revenueAmount on every event.
     */
    private Map<UUID, BigDecimal> resolveStripeRevenueFallback(List<UUID> customerIds, Map<UUID, Customer> customerMap, UUID accountId) {
        Map<UUID, BigDecimal> result = new HashMap<>();
        if (customerIds.isEmpty()) return result;

        // Batch load all subscriptions for the given customer IDs to avoid N+1
        List<Subscription> allSubs = subscriptionRepository.findSubscriptionsByCustomer_IdInAndAccount_Id(customerIds, accountId);
        Map<UUID, List<Subscription>> subsByCustomer = allSubs.stream()
                .collect(java.util.stream.Collectors.groupingBy(s -> s.getCustomer().getId()));

        for (UUID customerId : customerIds) {
            Customer customer = customerMap.get(customerId);
            if (customer == null) continue;

            List<Subscription> subs = subsByCustomer.getOrDefault(customerId, List.of());
            if (subs.isEmpty()) continue;

            // Only use active subscriptions for MRR fallback
            Subscription sub = subs.stream()
                    .filter(Subscription::getIsActive)
                    .findFirst()
                    .orElse(null);

            if (sub == null) continue;

            if (sub.getPlan() != null) {
                BigDecimal mrr = normalizeMrr(sub.getPlan());
                if (mrr.compareTo(BigDecimal.ZERO) > 0) {
                    result.put(customerId, mrr);
                }
            }
        }
        return result;
    }

    /**
     * Distribute Stripe MRR across features proportional to each feature's cost share.
     * When a customer's revenue comes from the Stripe fallback (not event-level revenueAmount),
     * we allocate revenue to features based on how much each feature costs relative to total cost.
     * This produces meaningful per-feature margins instead of showing zero revenue against real costs.
     */
    private List<FeatureProfitabilityDto> distributeRevenueByFeatureCost(
            List<FeatureProfitabilityDto> features, BigDecimal totalMrr) {

        BigDecimal totalFeatureCost = features.stream()
                .map(f -> f.getCost() != null ? f.getCost() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // If no cost data, split revenue equally across features
        boolean equalSplit = totalFeatureCost.compareTo(BigDecimal.ZERO) == 0;
        int featureCount = features.size();

        List<FeatureProfitabilityDto> enriched = new ArrayList<>();
        for (FeatureProfitabilityDto fp : features) {
            BigDecimal featureCost = fp.getCost() != null ? fp.getCost() : BigDecimal.ZERO;

            BigDecimal allocatedRevenue;
            if (equalSplit) {
                allocatedRevenue = totalMrr.divide(BigDecimal.valueOf(featureCount), 4, RoundingMode.HALF_UP);
            } else {
                allocatedRevenue = featureCost.compareTo(BigDecimal.ZERO) > 0
                        ? totalMrr.multiply(featureCost).divide(totalFeatureCost, 4, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
            }

            enriched.add(FeatureProfitabilityDto.builder()
                    .featureId(fp.getFeatureId())
                    .featureName(fp.getFeatureName())
                    .featureKey(fp.getFeatureKey())
                    .usageUnits(fp.getUsageUnits())
                    .revenue(allocatedRevenue)
                    .cost(fp.getCost())
                    .margin(fp.getCost() != null ? allocatedRevenue.subtract(fp.getCost()) : null)
                    .modelBreakdown(fp.getModelBreakdown())
                    .build());
        }
        return enriched;
    }

    private PortfolioSummaryDto calculatePortfolioSummary(UUID accountId, List<CustomerAnalyticsDto> customers, List<Subscription> activeSubscriptions) {
        BigDecimal totalMrr = BigDecimal.ZERO;
        BigDecimal totalCosts = BigDecimal.ZERO;
        boolean anyCostData = false;
        BigDecimal totalProjectedUsageRevenue = BigDecimal.ZERO;
        BigDecimal totalEffectiveMrr = BigDecimal.ZERO;

        int healthyCount = 0;
        int atRiskCount = 0;
        int underwaterCount = 0;

        BigDecimal healthyMrr = BigDecimal.ZERO;
        BigDecimal atRiskMrr = BigDecimal.ZERO;
        BigDecimal underwaterMrr = BigDecimal.ZERO;

        int pendingCancelCount = 0;
        BigDecimal pendingCancelMrr = BigDecimal.ZERO;
        int pendingDowngradeCount = 0;

        Instant now = Instant.now();
        Instant in30Days = now.plus(30, ChronoUnit.DAYS);
        Instant in60Days = now.plus(60, ChronoUnit.DAYS);
        Instant in90Days = now.plus(90, ChronoUnit.DAYS);

        BigDecimal burnDown30 = BigDecimal.ZERO;
        BigDecimal burnDown60 = BigDecimal.ZERO;
        BigDecimal burnDown90 = BigDecimal.ZERO;

        for (CustomerAnalyticsDto customer : customers) {
            totalMrr = totalMrr.add(customer.getMrr());
            if (customer.getTotalCost() != null) {
                totalCosts = totalCosts.add(customer.getTotalCost());
                anyCostData = true;
            }
            totalProjectedUsageRevenue = totalProjectedUsageRevenue.add(
                    customer.getProjectedUsageRevenue() != null ? customer.getProjectedUsageRevenue() : BigDecimal.ZERO);
            totalEffectiveMrr = totalEffectiveMrr.add(
                    customer.getEffectiveMrr() != null ? customer.getEffectiveMrr() : BigDecimal.ZERO);

            if (customer.getMarginStatus() != null) {
                switch (customer.getMarginStatus()) {
                    case "healthy" -> {
                        healthyCount++;
                        healthyMrr = healthyMrr.add(customer.getMrr());
                    }
                    case "at_risk" -> {
                        atRiskCount++;
                        atRiskMrr = atRiskMrr.add(customer.getMrr());
                    }
                    case "underwater" -> {
                        underwaterCount++;
                        underwaterMrr = underwaterMrr.add(customer.getMrr());
                    }
                }
            }

            if ("pending_cancel".equals(customer.getChurnRisk())) {
                pendingCancelCount++;
                pendingCancelMrr = pendingCancelMrr.add(customer.getMrr());

                Instant cancelAt = customer.getCancelEffectiveAt();
                if (cancelAt != null) {
                    if (cancelAt.isBefore(in30Days)) {
                        burnDown30 = burnDown30.add(customer.getMrr());
                    }
                    if (cancelAt.isBefore(in60Days)) {
                        burnDown60 = burnDown60.add(customer.getMrr());
                    }
                    if (cancelAt.isBefore(in90Days)) {
                        burnDown90 = burnDown90.add(customer.getMrr());
                    }
                }
            } else if ("pending_downgrade".equals(customer.getChurnRisk())) {
                pendingDowngradeCount++;
            }
        }

        BigDecimal effectiveTotalCosts = anyCostData ? totalCosts : null;

        BigDecimal avgMargin = !anyCostData || totalEffectiveMrr.compareTo(BigDecimal.ZERO) == 0
                ? null
                : totalEffectiveMrr.subtract(totalCosts).divide(totalEffectiveMrr, 4, RoundingMode.HALF_UP);

        BigDecimal totalRgp = anyCostData ? totalEffectiveMrr.subtract(totalCosts) : null;
        BigDecimal rgpMargin = !anyCostData || totalEffectiveMrr.compareTo(BigDecimal.ZERO) == 0
                ? null
                : totalRgp.divide(totalEffectiveMrr, 4, RoundingMode.HALF_UP);

        // Churn score aggregation
        int scoredCount = 0;
        BigDecimal scoreSum = BigDecimal.ZERO;
        int criticalChurnCount = 0;
        BigDecimal highRiskMrr = BigDecimal.ZERO;
        for (CustomerAnalyticsDto c : customers) {
            if (c.getChurnScoreDetails() != null && c.getChurnScoreDetails().getScore() != null) {
                scoredCount++;
                scoreSum = scoreSum.add(BigDecimal.valueOf(c.getChurnScoreDetails().getScore()));
                if (c.getChurnScoreDetails().getScore() >= 76) {
                    criticalChurnCount++;
                }
                if (c.getChurnScoreDetails().getScore() >= 51) {
                    highRiskMrr = highRiskMrr.add(c.getMrr());
                }
            }
        }
        BigDecimal avgChurnScore = scoredCount > 0
                ? scoreSum.divide(BigDecimal.valueOf(scoredCount), 2, RoundingMode.HALF_UP)
                : null;

        BigDecimal ltv = calculateLtv(accountId, totalMrr, customers.size());
        BigDecimal nrr = calculateNrr(accountId, activeSubscriptions);

        return PortfolioSummaryDto.builder()
                .totalMrr(totalMrr)
                .totalCosts(effectiveTotalCosts)
                .avgMargin(avgMargin)
                .customersByStatus(PortfolioSummaryDto.CustomersByStatus.builder()
                        .healthy(healthyCount)
                        .atRisk(atRiskCount)
                        .underwater(underwaterCount)
                        .build())
                .mrrByStatus(PortfolioSummaryDto.MrrByStatus.builder()
                        .healthy(healthyMrr)
                        .atRisk(atRiskMrr)
                        .underwater(underwaterMrr)
                        .build())
                .ltv(ltv)
                .nrr(nrr)
                .totalProjectedUsageRevenue(totalProjectedUsageRevenue)
                .totalEffectiveMrr(totalEffectiveMrr)
                .totalRgp(totalRgp)
                .rgpMargin(rgpMargin)
                .avgChurnScore(avgChurnScore)
                .criticalChurnCount(criticalChurnCount)
                .highRiskMrr(highRiskMrr)
                .pendingCancelCount(pendingCancelCount)
                .pendingCancelMrr(pendingCancelMrr)
                .pendingDowngradeCount(pendingDowngradeCount)
                .revenueBurnDown(RevenueBurnDownDto.builder()
                        .next30Days(burnDown30)
                        .next60Days(burnDown60)
                        .next90Days(burnDown90)
                        .build())
                .topModelsByCost(buildTopModelsByCost(accountId))
                .build();
    }

    private BigDecimal calculateLtv(UUID accountId, BigDecimal totalMrr, int activeCustomerCount) {
        if (activeCustomerCount == 0) {
            return null;
        }

        BigDecimal arpu = totalMrr.divide(BigDecimal.valueOf(activeCustomerCount), 4, RoundingMode.HALF_UP);

        Instant now = Instant.now();
        Instant thirtyDaysAgo = now.minus(30, ChronoUnit.DAYS);
        long churned = subscriptionRepository.countChurnedSubscriptions(accountId, thirtyDaysAgo, now);

        if (churned == 0) {
            return null;
        }

        BigDecimal churnRate = BigDecimal.valueOf(churned)
                .divide(BigDecimal.valueOf(activeCustomerCount + churned), 4, RoundingMode.HALF_UP);

        return arpu.divide(churnRate, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateNrr(UUID accountId, List<Subscription> currentActiveSubscriptions) {
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);

        List<Subscription> cohortSubscriptions = subscriptionRepository.findSubscriptionsActiveAsOf(accountId, thirtyDaysAgo);

        if (cohortSubscriptions.isEmpty()) {
            return null;
        }

        BigDecimal startingMrr = cohortSubscriptions.stream()
                .map(s -> normalizeMrr(s.getPlan()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (startingMrr.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        Set<UUID> cohortCustomerIds = cohortSubscriptions.stream()
                .map(s -> s.getCustomer().getId())
                .collect(Collectors.toSet());

        BigDecimal endingMrr = currentActiveSubscriptions.stream()
                .filter(s -> cohortCustomerIds.contains(s.getCustomer().getId()))
                .map(s -> normalizeMrr(s.getPlan()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return endingMrr.divide(startingMrr, 4, RoundingMode.HALF_UP);
    }

    @Override
    public RevenueBridgeResponseDto getRevenueBridge(String accountId, int periods) {
        UUID accountUuid = UUID.fromString(accountId);
        int effectivePeriods = Math.max(1, Math.min(periods, 24));

        List<Invoice> allInvoices = invoiceRepository.getInvoicesByAccount_Id(accountUuid);

        Set<String> completedStatuses = Set.of("PAID", "ADJUSTMENT_PAID");
        List<Invoice> paidInvoices = allInvoices.stream()
                .filter(inv -> inv.getStatus() != null && completedStatuses.contains(inv.getStatus()))
                .toList();

        if (paidInvoices.isEmpty()) {
            return RevenueBridgeResponseDto.builder().periods(List.of()).build();
        }

        YearMonth now = YearMonth.now(ZoneOffset.UTC);
        YearMonth earliest = now.minusMonths(effectivePeriods - 1);

        // Group invoices by YearMonth of their period start
        Map<YearMonth, List<Invoice>> invoicesByMonth = new TreeMap<>();
        for (Invoice invoice : paidInvoices) {
            if (invoice.getInvoicePeriodStart() == null) {
                continue;
            }
            YearMonth month = YearMonth.from(invoice.getInvoicePeriodStart().atZone(ZoneOffset.UTC));
            if (month.isBefore(earliest)) {
                continue;
            }
            invoicesByMonth.computeIfAbsent(month, k -> new ArrayList<>()).add(invoice);
        }

        // Build period DTOs
        List<RevenueBridgePeriodDto> periodDtos = new ArrayList<>();
        Set<UUID> previousPeriodCustomers = null;

        for (Map.Entry<YearMonth, List<Invoice>> entry : invoicesByMonth.entrySet()) {
            YearMonth month = entry.getKey();
            List<Invoice> monthInvoices = entry.getValue();

            BigDecimal totalRevenue = BigDecimal.ZERO;
            BigDecimal baseRevenue = BigDecimal.ZERO;
            BigDecimal usageRevenue = BigDecimal.ZERO;
            BigDecimal adjustmentRevenue = BigDecimal.ZERO;
            BigDecimal creditAmount = BigDecimal.ZERO;

            Set<UUID> currentPeriodCustomers = new HashSet<>();

            for (Invoice invoice : monthInvoices) {
                UUID customerId = invoice.getSubscription().getCustomer().getId();
                currentPeriodCustomers.add(customerId);

                String invoiceType = invoice.getType();

                if ("CREDIT".equals(invoiceType)) {
                    BigDecimal amount = invoice.getAmount() != null ? invoice.getAmount() : BigDecimal.ZERO;
                    creditAmount = creditAmount.add(amount);
                    totalRevenue = totalRevenue.add(amount);
                } else {
                    List<InvoiceItem> items = invoiceItemRepository.findAllByInvoice(invoice);

                    for (InvoiceItem item : items) {
                        BigDecimal amount = item.getChargeAmount() != null ? item.getChargeAmount() : BigDecimal.ZERO;
                        totalRevenue = totalRevenue.add(amount);

                        if ("ADJUSTMENT".equals(invoiceType)) {
                            adjustmentRevenue = adjustmentRevenue.add(amount);
                        } else {
                            String desc = item.getDescription() != null ? item.getDescription() : "";
                            if (desc.startsWith("Plan base price:")) {
                                baseRevenue = baseRevenue.add(amount);
                            } else if (desc.startsWith("Usage for ")) {
                                usageRevenue = usageRevenue.add(amount);
                            } else {
                                baseRevenue = baseRevenue.add(amount);
                            }
                        }
                    }
                }
            }

            int newCustomers = 0;
            int churnedCustomers = 0;
            if (previousPeriodCustomers != null) {
                for (UUID cid : currentPeriodCustomers) {
                    if (!previousPeriodCustomers.contains(cid)) {
                        newCustomers++;
                    }
                }
                for (UUID cid : previousPeriodCustomers) {
                    if (!currentPeriodCustomers.contains(cid)) {
                        churnedCustomers++;
                    }
                }
            }

            Instant periodStart = month.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            Instant periodEnd = month.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);

            BigDecimal grossRevenue = totalRevenue.subtract(creditAmount);
            BigDecimal creditToRevenueRatio = grossRevenue.compareTo(BigDecimal.ZERO) == 0
                    ? null
                    : creditAmount.abs().divide(grossRevenue, 4, RoundingMode.HALF_UP);

            periodDtos.add(RevenueBridgePeriodDto.builder()
                    .periodStart(periodStart)
                    .periodEnd(periodEnd)
                    .totalRevenue(totalRevenue)
                    .baseRevenue(baseRevenue)
                    .usageRevenue(usageRevenue)
                    .adjustmentRevenue(adjustmentRevenue)
                    .creditAmount(creditAmount)
                    .netRevenue(grossRevenue)
                    .creditToRevenueRatio(creditToRevenueRatio)
                    .customerCount(currentPeriodCustomers.size())
                    .newCustomers(newCustomers)
                    .churnedCustomers(churnedCustomers)
                    .build());

            previousPeriodCustomers = currentPeriodCustomers;
        }

        return RevenueBridgeResponseDto.builder().periods(periodDtos).build();
    }

    private AnalyticsResponseDto buildEmptyResponse() {
        return AnalyticsResponseDto.builder()
                .summary(PortfolioSummaryDto.builder()
                        .totalMrr(BigDecimal.ZERO)
                        .totalCosts(null)
                        .avgMargin(null)
                        .customersByStatus(PortfolioSummaryDto.CustomersByStatus.builder()
                                .healthy(0)
                                .atRisk(0)
                                .underwater(0)
                                .build())
                        .mrrByStatus(PortfolioSummaryDto.MrrByStatus.builder()
                                .healthy(BigDecimal.ZERO)
                                .atRisk(BigDecimal.ZERO)
                                .underwater(BigDecimal.ZERO)
                                .build())
                        .ltv(null)
                        .nrr(null)
                        .totalProjectedUsageRevenue(BigDecimal.ZERO)
                        .totalEffectiveMrr(BigDecimal.ZERO)
                        .totalRgp(null)
                        .rgpMargin(null)
                        .avgChurnScore(null)
                        .criticalChurnCount(0)
                        .highRiskMrr(BigDecimal.ZERO)
                        .creditImpact(null)
                        .topModelsByCost(List.of())
                        .pendingCancelCount(0)
                        .pendingCancelMrr(BigDecimal.ZERO)
                        .pendingDowngradeCount(0)
                        .revenueBurnDown(RevenueBurnDownDto.builder()
                                .next30Days(BigDecimal.ZERO)
                                .next60Days(BigDecimal.ZERO)
                                .next90Days(BigDecimal.ZERO)
                                .build())
                        .build())
                .customers(List.of())
                .build();
    }

    // --- Model Analytics Methods ---

    @Override
    public ModelsAnalyticsResponseDto getModelAnalytics(String accountId) {
        UUID accountUuid = UUID.fromString(accountId);
        Instant end = Instant.now();
        Instant start = end.minus(30, ChronoUnit.DAYS);
        List<EventType> eventTypes = List.of(EventType.CLIENT_TRACKED, EventType.ENTITLEMENT_CHECKED);

        List<Object[]> rows = eventRepository.sumCostGroupedByModel(
                accountUuid, eventTypes, start, end);

        List<ModelSummaryDto> models = new ArrayList<>();
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        long totalEvents = 0;
        Map<String, BigDecimal> providerCosts = new HashMap<>();

        for (Object[] row : rows) {
            String model = (String) row[0];
            String modelProvider = (String) row[1];
            long eventCount = (Long) row[2];
            long customerCount = (Long) row[3];
            long featureCount = (Long) row[4];
            BigDecimal cost = (BigDecimal) row[5];
            BigDecimal revenue = (BigDecimal) row[6];
            BigDecimal usage = (BigDecimal) row[7];
            Instant lastSeen = (Instant) row[8];

            BigDecimal avgCostPerEvent = eventCount > 0
                    ? cost.divide(BigDecimal.valueOf(eventCount), 6, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal margin = revenue.compareTo(BigDecimal.ZERO) > 0
                    ? revenue.subtract(cost).divide(revenue, 4, RoundingMode.HALF_UP)
                    : null;

            models.add(ModelSummaryDto.builder()
                    .model(model)
                    .modelProvider(modelProvider)
                    .eventCount(eventCount)
                    .customerCount(customerCount)
                    .featureCount(featureCount)
                    .totalCost(cost)
                    .totalRevenue(revenue)
                    .totalUsage(usage)
                    .avgCostPerEvent(avgCostPerEvent)
                    .margin(margin)
                    .lastSeen(lastSeen)
                    .build());

            totalCost = totalCost.add(cost);
            totalRevenue = totalRevenue.add(revenue);
            totalEvents += eventCount;

            String providerKey = modelProvider != null ? modelProvider : "Unknown";
            providerCosts.merge(providerKey, cost, BigDecimal::add);
        }

        final BigDecimal finalTotalCost = totalCost;
        List<ModelsAnalyticsResponseDto.ProviderCostDto> providerBreakdown = providerCosts.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(entry -> ModelsAnalyticsResponseDto.ProviderCostDto.builder()
                        .provider(entry.getKey())
                        .cost(entry.getValue())
                        .percentage(finalTotalCost.compareTo(BigDecimal.ZERO) > 0
                                ? entry.getValue().divide(finalTotalCost, 4, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO)
                        .build())
                .toList();

        return ModelsAnalyticsResponseDto.builder()
                .totalModels(models.size())
                .totalCost(totalCost)
                .totalRevenue(totalRevenue)
                .totalEvents(totalEvents)
                .providerBreakdown(providerBreakdown)
                .models(models)
                .build();
    }

    private List<ModelProfitabilityDto> buildTopModelsByCost(UUID accountId) {
        Instant end = Instant.now();
        Instant start = end.minus(30, ChronoUnit.DAYS);
        List<EventType> eventTypes = List.of(EventType.CLIENT_TRACKED, EventType.ENTITLEMENT_CHECKED);

        List<Object[]> rows = eventRepository.sumCostGroupedByModel(
                accountId, eventTypes, start, end);

        return rows.stream()
                .map(row -> {
                    BigDecimal cost = (BigDecimal) row[5];
                    BigDecimal revenue = (BigDecimal) row[6];
                    BigDecimal usageUnits = (BigDecimal) row[7];
                    BigDecimal margin = revenue.compareTo(BigDecimal.ZERO) > 0
                            ? revenue.subtract(cost).divide(revenue, 4, RoundingMode.HALF_UP)
                            : null;
                    return ModelProfitabilityDto.builder()
                            .model((String) row[0])
                            .modelProvider((String) row[1])
                            .usageUnits(usageUnits)
                            .cost(cost)
                            .revenue(revenue)
                            .margin(margin)
                            .build();
                })
                .toList();
    }

    // --- Churn Scoring Methods ---

    private void calculateChurnScores(List<CustomerAnalyticsDto> customerAnalytics,
                                       List<Subscription> activeSubscriptions,
                                       List<Plan> plans) {
        if (customerAnalytics.isEmpty()) {
            return;
        }

        List<Customer> customers = activeSubscriptions.stream()
                .map(Subscription::getCustomer)
                .distinct()
                .toList();

        List<UUID> customerIds = customers.stream()
                .map(Customer::getId)
                .toList();

        // Batch load entitlements (1 query)
        List<Entitlement> allEntitlements = entitlementRepository.findActiveEntitlementsByCustomerIn(customers);
        Map<UUID, List<Entitlement>> entitlementsByCustomer = allEntitlements.stream()
                .collect(Collectors.groupingBy(e -> e.getCustomer().getId()));

        // Batch load event counts for current and prior 30-day windows (2 queries)
        Instant now = Instant.now();
        Instant thirtyDaysAgo = now.minus(30, ChronoUnit.DAYS);
        Instant sixtyDaysAgo = now.minus(60, ChronoUnit.DAYS);

        List<EventType> trackingTypes = List.of(EventType.CLIENT_TRACKED, EventType.ENTITLEMENT_CHECKED);

        Map<UUID, Long> currentEventCounts = parseEventCountResults(
                eventRepository.countEventsGroupedByCustomerInRange(customerIds, trackingTypes, thirtyDaysAgo, now));
        Map<UUID, Long> priorEventCounts = parseEventCountResults(
                eventRepository.countEventsGroupedByCustomerInRange(customerIds, trackingTypes, sixtyDaysAgo, thirtyDaysAgo));

        // Build plan price rank map for plan tier scoring
        List<BigDecimal> sortedPrices = plans.stream()
                .map(p -> p.getPriceAmount() != null ? p.getPriceAmount() : BigDecimal.ZERO)
                .sorted()
                .toList();

        Map<UUID, BigDecimal> planPriceMap = plans.stream()
                .collect(Collectors.toMap(Plan::getId, p -> p.getPriceAmount() != null ? p.getPriceAmount() : BigDecimal.ZERO));

        // Build subscription map for plan lookup
        Map<UUID, Subscription> subscriptionByCustomer = activeSubscriptions.stream()
                .collect(Collectors.toMap(s -> s.getCustomer().getId(), s -> s, (a, b) -> a));

        for (CustomerAnalyticsDto dto : customerAnalytics) {
            UUID customerId = dto.getCustomerId();
            List<Entitlement> customerEntitlements = entitlementsByCustomer.getOrDefault(customerId, List.of());

            int stalenessScore = calculateStalenessScore(customerEntitlements, now);
            int eventTrendScore = calculateEventTrendScore(
                    currentEventCounts.getOrDefault(customerId, 0L),
                    priorEventCounts.getOrDefault(customerId, 0L));
            int marginScore = calculateMarginScore(dto.getMarginStatus());
            int planTierScore = calculatePlanTierScore(dto.getPlanId(), planPriceMap, sortedPrices);

            Subscription sub = subscriptionByCustomer.get(customerId);
            Instant periodStart = sub != null && sub.getCurrentPeriodStart() != null
                    ? sub.getCurrentPeriodStart()
                    : thirtyDaysAgo;
            int utilizationScore = calculateUtilizationScore(customerEntitlements, periodStart);

            int composite = (int) Math.round(
                    stalenessScore * 0.25 +
                    eventTrendScore * 0.30 +
                    marginScore * 0.15 +
                    planTierScore * 0.10 +
                    utilizationScore * 0.20);
            composite = Math.max(0, Math.min(100, composite));

            String riskLabel;
            if (composite <= 25) {
                riskLabel = "low";
            } else if (composite <= 50) {
                riskLabel = "moderate";
            } else if (composite <= 75) {
                riskLabel = "high";
            } else {
                riskLabel = "critical";
            }

            dto.setChurnScoreDetails(ChurnScoreDto.builder()
                    .score(composite)
                    .riskLabel(riskLabel)
                    .stalenessScore(stalenessScore)
                    .eventTrendScore(eventTrendScore)
                    .marginScore(marginScore)
                    .planTierScore(planTierScore)
                    .utilizationScore(utilizationScore)
                    .build());
        }
    }

    private Map<UUID, Long> parseEventCountResults(List<Object[]> rows) {
        Map<UUID, Long> result = new HashMap<>();
        for (Object[] row : rows) {
            UUID customerId = (UUID) row[0];
            Long count = ((Number) row[1]).longValue();
            result.put(customerId, count);
        }
        return result;
    }

    private int calculateStalenessScore(List<Entitlement> entitlements, Instant now) {
        if (entitlements.isEmpty()) {
            return 50; // No entitlements = moderate concern
        }

        double totalScore = 0;
        for (Entitlement e : entitlements) {
            if (e.getLastAccessed() == null) {
                totalScore += 100; // Never accessed = maximum staleness
            } else {
                long daysSinceAccess = ChronoUnit.DAYS.between(e.getLastAccessed(), now);
                // Scale: 0 days = 0, 30+ days = 100
                totalScore += Math.min(100, (daysSinceAccess * 100.0) / 30.0);
            }
        }
        return (int) Math.round(totalScore / entitlements.size());
    }

    private int calculateEventTrendScore(long currentCount, long priorCount) {
        if (priorCount == 0 && currentCount == 0) {
            return 50; // No activity in either period
        }
        if (priorCount == 0) {
            return 0; // New activity, no prior baseline — low risk
        }
        // Decline ratio: how much did activity drop?
        double ratio = (double) currentCount / priorCount;
        if (ratio >= 1.0) {
            return 0; // Activity stable or growing
        }
        // ratio 0.0 = 100% decline → score 100; ratio 1.0 = no decline → score 0
        return (int) Math.round((1.0 - ratio) * 100);
    }

    private int calculateMarginScore(String marginStatus) {
        if (marginStatus == null) {
            return 20;
        }
        return switch (marginStatus) {
            case "healthy" -> 0;
            case "at_risk" -> 40;
            case "underwater" -> 80;
            default -> 20;
        };
    }

    private int calculatePlanTierScore(UUID planId, Map<UUID, BigDecimal> planPriceMap, List<BigDecimal> sortedPrices) {
        BigDecimal price = planPriceMap.getOrDefault(planId, BigDecimal.ZERO);
        if (sortedPrices.size() <= 1) {
            return 0; // Single plan (or none) — no tier differentiation possible
        }
        if (price.compareTo(sortedPrices.getFirst()) == 0) {
            return 60; // Cheapest plan
        }
        // Position in sorted list — bottom 25th percentile gets 30
        int index = 0;
        for (int i = 0; i < sortedPrices.size(); i++) {
            if (sortedPrices.get(i).compareTo(price) >= 0) {
                index = i;
                break;
            }
            index = i;
        }
        double percentile = (double) (index + 1) / sortedPrices.size();
        if (percentile <= 0.25) {
            return 30;
        }
        return 0;
    }

    private int calculateUtilizationScore(List<Entitlement> entitlements, Instant periodStart) {
        if (entitlements.isEmpty()) {
            return 100; // No features to use = high risk
        }
        long accessedCount = entitlements.stream()
                .filter(e -> e.getLastAccessed() != null && e.getLastAccessed().isAfter(periodStart))
                .count();
        double utilizationRatio = (double) accessedCount / entitlements.size();
        // Invert: high utilization = low risk score
        return (int) Math.round((1.0 - utilizationRatio) * 100);
    }

    // --- Credit Impact Methods ---

    private CreditImpactDto calculateCreditImpact(List<Invoice> allInvoices, BigDecimal totalEffectiveMrr) {
        BigDecimal totalCredits = BigDecimal.ZERO;
        int creditCount = 0;

        for (Invoice invoice : allInvoices) {
            if ("CREDIT".equals(invoice.getType())) {
                totalCredits = totalCredits.add(invoice.getAmount() != null ? invoice.getAmount() : BigDecimal.ZERO);
                creditCount++;
            }
        }

        if (creditCount == 0) {
            return null;
        }

        BigDecimal netEffectiveMrr = totalEffectiveMrr.add(totalCredits);
        BigDecimal creditToMrrRatio = totalEffectiveMrr.compareTo(BigDecimal.ZERO) == 0
                ? null
                : totalCredits.abs().divide(totalEffectiveMrr, 4, RoundingMode.HALF_UP);

        return CreditImpactDto.builder()
                .totalCredits(totalCredits)
                .creditInvoiceCount(creditCount)
                .netEffectiveMrr(netEffectiveMrr)
                .creditToMrrRatio(creditToMrrRatio)
                .build();
    }

    private BigDecimal calculateCustomerCredits(List<Invoice> allInvoices, UUID customerId) {
        BigDecimal credits = BigDecimal.ZERO;
        boolean hasCredits = false;

        for (Invoice invoice : allInvoices) {
            if ("CREDIT".equals(invoice.getType())
                    && invoice.getSubscription() != null
                    && invoice.getSubscription().getCustomer() != null
                    && customerId.equals(invoice.getSubscription().getCustomer().getId())) {
                credits = credits.add(invoice.getAmount() != null ? invoice.getAmount() : BigDecimal.ZERO);
                hasCredits = true;
            }
        }

        return hasCredits ? credits : null;
    }
}
