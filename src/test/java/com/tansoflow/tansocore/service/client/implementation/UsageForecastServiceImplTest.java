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
import com.tansoflow.tansocore.entity.CreditPool;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.Feature;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.entity.PlanFeatureRule;
import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.model.entitlement.response.EntitlementResponse;
import com.tansoflow.tansocore.model.usage.CustomerUsageResponse;
import com.tansoflow.tansocore.repository.CreditPoolRepository;
import com.tansoflow.tansocore.repository.PlanFeatureRuleRepository;
import com.tansoflow.tansocore.repository.SubscriptionRepository;
import com.tansoflow.tansocore.service.client.ClientEntitlementService;
import com.tansoflow.tansocore.service.internal.account.CustomerService;
import com.tansoflow.tansocore.service.internal.monetization.CreditPriceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsageForecastServiceImplTest {

    @Mock
    private CustomerService customerService;
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private PlanFeatureRuleRepository planFeatureRuleRepository;
    @Mock
    private ClientEntitlementService clientEntitlementService;
    @Mock
    private CreditPoolRepository creditPoolRepository;
    @Mock
    private CreditPriceService creditPriceService;

    @InjectMocks
    private UsageForecastServiceImpl service;

    private final UUID accountId = UUID.randomUUID();
    private Customer customer;
    private Subscription subscription;

    @BeforeEach
    void setUp() {
        Account account = new Account();
        account.setId(accountId);
        customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setAccount(account);
        customer.setExternalClientCustomerId("cust-1");

        Plan plan = new Plan();
        plan.setId(UUID.randomUUID());
        plan.setKey("pro");

        subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setPlan(plan);
        subscription.setIsActive(true);
        // Half the period has elapsed
        subscription.setCurrentPeriodStart(Instant.now().minus(Duration.ofDays(15)));
        subscription.setCurrentPeriodEnd(Instant.now().plus(Duration.ofDays(15)));

        lenient().when(customerService.retrieveCustomerByExternalClientCustomerIdAndAccount("cust-1", accountId.toString()))
                .thenReturn(customer);
        lenient().when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId()))
                .thenReturn(List.of(subscription));
        lenient().when(creditPoolRepository.findByCustomerIdAndAccountId(customer.getId(), accountId))
                .thenReturn(List.of());
        lenient().when(creditPriceService.resolvePrice(any(), any(), any())).thenReturn(Optional.empty());

        Feature feature = new Feature();
        feature.setKey("ai.chat");
        PlanFeatureRule rule = new PlanFeatureRule();
        rule.setFeature(feature);
        lenient().when(planFeatureRuleRepository.findPlanFeatureRulesByPlanId(plan.getId()))
                .thenReturn(List.of(rule));
    }

    private EntitlementResponse entitlementWithUsage(BigDecimal used, BigDecimal limit) {
        EntitlementResponse response = new EntitlementResponse();
        EntitlementResponse.Usage usage = new EntitlementResponse.Usage();
        usage.setUsed(used);
        usage.setLimit(limit);
        usage.setRemaining(limit.subtract(used));
        response.setUsage(usage);
        return response;
    }

    @Test
    void projectsLinearlyAtMidPeriod() {
        when(clientEntitlementService.checkEntitlement("cust-1", accountId.toString(), "ai.chat", false))
                .thenReturn(entitlementWithUsage(new BigDecimal("500"), new BigDecimal("2000")));

        CustomerUsageResponse usage = service.getUsage("cust-1", accountId.toString());

        CustomerUsageResponse.FeatureUsage feature = usage.getSubscriptions().get(0).getFeatures().get(0);
        // 500 used at ~50% elapsed → ~1000 projected
        assertThat(feature.getProjectedEndOfPeriod().doubleValue()).isBetween(990.0, 1010.0);
    }

    @Test
    void skipsProjectionAtStartOfPeriod() {
        subscription.setCurrentPeriodStart(Instant.now().minus(Duration.ofMinutes(10)));
        subscription.setCurrentPeriodEnd(Instant.now().plus(Duration.ofDays(30)));
        when(clientEntitlementService.checkEntitlement("cust-1", accountId.toString(), "ai.chat", false))
                .thenReturn(entitlementWithUsage(new BigDecimal("5"), new BigDecimal("2000")));

        CustomerUsageResponse usage = service.getUsage("cust-1", accountId.toString());
        assertThat(usage.getSubscriptions().get(0).getFeatures().get(0).getProjectedEndOfPeriod()).isNull();
    }

    @Test
    void creditPoolDepletionUsesAverageBurn() {
        CreditPool pool = new CreditPool();
        pool.setId(UUID.randomUUID());
        pool.setDenomination("credits");
        pool.setBalance(new BigDecimal("100"));
        pool.setTotalConsumed(new BigDecimal("300"));
        try {
            var field = CreditPool.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(pool, Instant.now().minus(Duration.ofDays(30)));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        when(creditPoolRepository.findByCustomerIdAndAccountId(customer.getId(), accountId))
                .thenReturn(List.of(pool));
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId())).thenReturn(List.of());
        when(creditPriceService.resolvePrice(eq(accountId), eq("credits"), any()))
                .thenReturn(Optional.of(new CreditPriceService.ResolvedPrice(
                        new BigDecimal("0.01"), "USD", UUID.randomUUID())));

        CustomerUsageResponse usage = service.getUsage("cust-1", accountId.toString());

        CustomerUsageResponse.CreditPoolUsage poolUsage = usage.getCreditPools().get(0);
        // 300 consumed over 30 days → 10/day; 100 balance → ~10 days out
        assertThat(poolUsage.getAverageDailyBurn()).isEqualByComparingTo("10");
        assertThat(poolUsage.getProjectedDepletionDate())
                .isBetween(Instant.now().plus(Duration.ofDays(9)), Instant.now().plus(Duration.ofDays(11)));
        assertThat(poolUsage.getPricePerCredit()).isEqualByComparingTo("0.01");
    }

    @Test
    void zeroBurnMeansNoDepletionDate() {
        CreditPool pool = new CreditPool();
        pool.setId(UUID.randomUUID());
        pool.setDenomination("credits");
        pool.setBalance(new BigDecimal("100"));
        pool.setTotalConsumed(BigDecimal.ZERO);
        when(creditPoolRepository.findByCustomerIdAndAccountId(customer.getId(), accountId))
                .thenReturn(List.of(pool));
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId())).thenReturn(List.of());

        CustomerUsageResponse usage = service.getUsage("cust-1", accountId.toString());
        assertThat(usage.getCreditPools().get(0).getProjectedDepletionDate()).isNull();
        assertThat(usage.getCreditPools().get(0).getAverageDailyBurn()).isNull();
    }
}
