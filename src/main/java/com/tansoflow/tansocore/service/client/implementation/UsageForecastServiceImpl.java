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

import com.tansoflow.tansocore.entity.CreditPool;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.PlanFeatureRule;
import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.model.entitlement.response.EntitlementResponse;
import com.tansoflow.tansocore.model.usage.CustomerUsageResponse;
import com.tansoflow.tansocore.repository.CreditPoolRepository;
import com.tansoflow.tansocore.repository.PlanFeatureRuleRepository;
import com.tansoflow.tansocore.repository.SubscriptionRepository;
import com.tansoflow.tansocore.service.client.ClientEntitlementService;
import com.tansoflow.tansocore.service.client.UsageForecastService;
import com.tansoflow.tansocore.service.internal.account.CustomerService;
import com.tansoflow.tansocore.service.internal.monetization.CreditPriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UsageForecastServiceImpl implements UsageForecastService {

    private final CustomerService customerService;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanFeatureRuleRepository planFeatureRuleRepository;
    private final ClientEntitlementService clientEntitlementService;
    private final CreditPoolRepository creditPoolRepository;
    private final CreditPriceService creditPriceService;
    private final com.tansoflow.tansocore.repository.EventRepository eventRepository;
    private final com.tansoflow.tansocore.repository.FeatureRepository featureRepository;

    @Override
    @Transactional(readOnly = true)
    public com.tansoflow.tansocore.model.usage.CustomerEventsResponse getRecordedEvents(
            String customerReferenceId, String accountId, Instant from, Instant to,
            String featureKey, int page, int limit) {
        Customer customer = customerService
                .retrieveCustomerByExternalClientCustomerIdAndAccount(customerReferenceId, accountId);

        UUID featureId = null;
        if (featureKey != null) {
            featureId = featureRepository.findByKeyAndAccountId(featureKey, UUID.fromString(accountId))
                    .map(f -> f.getId()).orElse(null);
            if (featureId == null) {
                // A feature nobody has heard of matches nothing, rather than matching everything.
                return com.tansoflow.tansocore.model.usage.CustomerEventsResponse.builder()
                        .customerReferenceId(customerReferenceId).from(from).to(to)
                        .hasMore(false).events(List.of()).build();
            }
        }

        org.springframework.data.domain.Page<com.tansoflow.tansocore.entity.Event> found =
                eventRepository.findRecordedEvents(customer.getId(), UUID.fromString(accountId), from, to, featureId,
                        org.springframework.data.domain.PageRequest.of(page, limit));

        java.util.Map<UUID, String> featureKeys = new java.util.HashMap<>();
        List<com.tansoflow.tansocore.model.usage.CustomerEventsResponse.RecordedEvent> events = new ArrayList<>();
        for (com.tansoflow.tansocore.entity.Event event : found.getContent()) {
            String key = event.getFeatureId() == null ? null : featureKeys.computeIfAbsent(event.getFeatureId(),
                    id -> featureRepository.findByIdAndAccountId(id, UUID.fromString(accountId))
                            .map(f -> f.getKey()).orElse(null));
            events.add(com.tansoflow.tansocore.model.usage.CustomerEventsResponse.RecordedEvent.builder()
                    .id(event.getId() == null ? null : event.getId().toString())
                    .eventIdempotencyKey(event.getEventIdempotencyKey())
                    .eventName(event.getEventName())
                    .featureKey(key)
                    .subscriptionId(event.getSubscriptionId() == null ? null : event.getSubscriptionId().toString())
                    .usageUnits(event.getUsageUnits())
                    .usageUnitType(event.getUsageUnitType())
                    .occurredAt(event.getOccurredAt())
                    .build());
        }

        return com.tansoflow.tansocore.model.usage.CustomerEventsResponse.builder()
                .customerReferenceId(customerReferenceId)
                .from(from)
                .to(to)
                .hasMore(found.hasNext())
                .events(events)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public com.tansoflow.tansocore.model.usage.CustomerUsageHistoryResponse getUsageHistory(
            String customerReferenceId, String accountId, Instant from, Instant to,
            String featureKey, String subscriptionId) {
        Customer customer = customerService
                .retrieveCustomerByExternalClientCustomerIdAndAccount(customerReferenceId, accountId);

        java.util.Map<UUID, Subscription> subscriptionsById = new java.util.HashMap<>();
        for (Subscription subscription : subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId())) {
            subscriptionsById.put(subscription.getId(), subscription);
        }

        List<com.tansoflow.tansocore.model.usage.CustomerUsageHistoryResponse.RecordedUsage> rows = new ArrayList<>();
        for (Object[] row : eventRepository.sumRecordedUsageByCustomerBetween(customer.getId(), from, to)) {
            UUID rowSubscriptionId = (UUID) row[0];
            UUID featureId = (UUID) row[1];
            if (subscriptionId != null && !subscriptionId.equals(rowSubscriptionId != null ? rowSubscriptionId.toString() : null)) {
                continue;
            }
            String key = featureId == null ? null : featureRepository.findByIdAndAccountId(featureId, UUID.fromString(accountId))
                    .map(f -> f.getKey()).orElse(null);
            if (featureKey != null && !featureKey.equals(key)) {
                continue;
            }
            Subscription subscription = rowSubscriptionId == null ? null : subscriptionsById.get(rowSubscriptionId);
            rows.add(com.tansoflow.tansocore.model.usage.CustomerUsageHistoryResponse.RecordedUsage.builder()
                    .subscriptionId(rowSubscriptionId == null ? null : rowSubscriptionId.toString())
                    .planKey(subscription == null ? null : subscription.getPlan().getKey())
                    .featureKey(key)
                    .eventName((String) row[2])
                    .usageUnits((BigDecimal) row[3])
                    .events((Long) row[4])
                    .firstOccurredAt((Instant) row[5])
                    .lastOccurredAt((Instant) row[6])
                    .build());
        }

        return com.tansoflow.tansocore.model.usage.CustomerUsageHistoryResponse.builder()
                .customerReferenceId(customerReferenceId)
                .from(from)
                .to(to)
                .usage(rows)
                .build();
    }

    /** When an ended subscription stopped: the moment it was cancelled, else the end of its last period. */
    private Instant endedAt(Subscription subscription) {
        return subscription.getCancelledAt() != null ? subscription.getCancelledAt() : subscription.getCurrentPeriodEnd();
    }

    /**
     * How far back ended plans are reported. A year covers the periods anyone reconciles against an invoice
     * without turning this into an unbounded history endpoint.
     */
    private boolean endedWithin(Subscription subscription, Instant now) {
        Instant ended = endedAt(subscription);
        return ended != null && ended.isAfter(now.minus(Duration.ofDays(365)));
    }

    /**
     * What was recorded against an ended subscription over its final period, read from the events themselves.
     * Entitlements are revoked when a plan ends, so the entitlement path would report nothing here.
     */
    private BigDecimal recordedUsage(UUID customerId, Subscription subscription, PlanFeatureRule rule) {
        Instant start = subscription.getCurrentPeriodStart();
        Instant end = endedAt(subscription);
        if (start == null || end == null) {
            return null;
        }
        return eventRepository.sumUsageUnitsBySubscriptionAndFeatureIdSince(
                customerId, subscription.getId(), rule.getFeature().getId(), start, end);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerUsageResponse getUsage(String customerReferenceId, String accountId) {
        Customer customer = customerService
                .retrieveCustomerByExternalClientCustomerIdAndAccount(customerReferenceId, accountId);
        Instant now = Instant.now();

        List<CustomerUsageResponse.SubscriptionUsage> subscriptions = new ArrayList<>();
        for (Subscription subscription : subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId())) {
            boolean active = Boolean.TRUE.equals(subscription.getIsActive());
            // A plan the customer has left keeps its usage here. An upgrade retires the plan the usage was
            // recorded on, and dropping it would take the period's record with it; that period still has to be
            // auditable. Entitlement questions are answered by the active plans only.
            if (!active && !endedWithin(subscription, now)) {
                continue;
            }
            List<CustomerUsageResponse.FeatureUsage> features = new ArrayList<>();
            for (PlanFeatureRule rule : planFeatureRuleRepository
                    .findPlanFeatureRulesByPlanId(subscription.getPlan().getId())) {
                String featureKey = rule.getFeature().getKey();
                if (!active) {
                    features.add(CustomerUsageResponse.FeatureUsage.builder()
                            .featureKey(featureKey)
                            .used(recordedUsage(customer.getId(), subscription, rule))
                            .build());
                    continue;
                }
                EntitlementResponse entitlement = clientEntitlementService
                        .checkEntitlement(customerReferenceId, accountId, featureKey, false);
                EntitlementResponse.Usage usage = entitlement.getUsage();
                features.add(CustomerUsageResponse.FeatureUsage.builder()
                        .featureKey(featureKey)
                        .used(usage != null ? usage.getUsed() : null)
                        .limit(usage != null ? usage.getLimit() : null)
                        .remaining(usage != null ? usage.getRemaining() : null)
                        .projectedEndOfPeriod(project(usage, subscription, now))
                        .build());
            }
            subscriptions.add(CustomerUsageResponse.SubscriptionUsage.builder()
                    .subscriptionId(subscription.getId().toString())
                    .planKey(subscription.getPlan().getKey())
                    .status(active ? "active" : "ended")
                    .endedAt(active ? null : endedAt(subscription))
                    .currentPeriodStart(subscription.getCurrentPeriodStart())
                    .currentPeriodEnd(subscription.getCurrentPeriodEnd())
                    .features(features)
                    .build());
        }

        List<CustomerUsageResponse.CreditPoolUsage> pools = new ArrayList<>();
        for (CreditPool pool : creditPoolRepository.findByCustomerIdAndAccountId(
                customer.getId(), UUID.fromString(accountId))) {
            BigDecimal dailyBurn = averageDailyBurn(pool, now);
            Instant depletion = null;
            if (dailyBurn.signum() > 0 && pool.getBalance() != null && pool.getBalance().signum() > 0) {
                long daysLeft = pool.getBalance()
                        .divide(dailyBurn, 0, RoundingMode.CEILING).longValueExact();
                depletion = now.plus(Duration.ofDays(daysLeft));
            }
            var price = creditPriceService.resolvePrice(
                    UUID.fromString(accountId), pool.getDenomination(), now);
            pools.add(CustomerUsageResponse.CreditPoolUsage.builder()
                    .poolId(pool.getId().toString())
                    .denomination(pool.getDenomination())
                    .balance(pool.getBalance())
                    .totalConsumed(pool.getTotalConsumed())
                    .averageDailyBurn(dailyBurn.signum() > 0 ? dailyBurn : null)
                    .projectedDepletionDate(depletion)
                    .pricePerCredit(price.map(CreditPriceService.ResolvedPrice::pricePerCredit).orElse(null))
                    .currency(price.map(CreditPriceService.ResolvedPrice::currency).orElse(null))
                    .build());
        }

        return CustomerUsageResponse.builder()
                .customerReferenceId(customerReferenceId)
                .asOf(now)
                .subscriptions(subscriptions)
                .creditPools(pools)
                .build();
    }

    /** used ÷ elapsed period fraction; null when usage is absent or under 5% of the period has elapsed. */
    private BigDecimal project(EntitlementResponse.Usage usage, Subscription subscription, Instant now) {
        if (usage == null || usage.getUsed() == null
                || subscription.getCurrentPeriodStart() == null || subscription.getCurrentPeriodEnd() == null) {
            return null;
        }
        long total = Duration.between(subscription.getCurrentPeriodStart(), subscription.getCurrentPeriodEnd()).getSeconds();
        long elapsed = Duration.between(subscription.getCurrentPeriodStart(), now).getSeconds();
        if (total <= 0 || elapsed <= 0) {
            return null;
        }
        BigDecimal fraction = BigDecimal.valueOf(elapsed)
                .divide(BigDecimal.valueOf(total), 6, RoundingMode.HALF_UP);
        if (fraction.compareTo(new BigDecimal("0.05")) < 0) {
            return null;
        }
        return usage.getUsed().divide(fraction, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal averageDailyBurn(CreditPool pool, Instant now) {
        if (pool.getTotalConsumed() == null || pool.getTotalConsumed().signum() <= 0
                || pool.getCreatedAt() == null) {
            return BigDecimal.ZERO;
        }
        long days = Math.max(1, Duration.between(pool.getCreatedAt(), now).toDays());
        return pool.getTotalConsumed().divide(BigDecimal.valueOf(days), 4, RoundingMode.HALF_UP);
    }
}
