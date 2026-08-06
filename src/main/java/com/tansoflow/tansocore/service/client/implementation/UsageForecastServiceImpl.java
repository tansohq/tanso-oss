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

    @Override
    @Transactional(readOnly = true)
    public CustomerUsageResponse getUsage(String customerReferenceId, String accountId) {
        Customer customer = customerService
                .retrieveCustomerByExternalClientCustomerIdAndAccount(customerReferenceId, accountId);
        Instant now = Instant.now();

        List<CustomerUsageResponse.SubscriptionUsage> subscriptions = new ArrayList<>();
        for (Subscription subscription : subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId())) {
            if (!Boolean.TRUE.equals(subscription.getIsActive())) {
                continue;
            }
            List<CustomerUsageResponse.FeatureUsage> features = new ArrayList<>();
            for (PlanFeatureRule rule : planFeatureRuleRepository
                    .findPlanFeatureRulesByPlanId(subscription.getPlan().getId())) {
                String featureKey = rule.getFeature().getKey();
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
