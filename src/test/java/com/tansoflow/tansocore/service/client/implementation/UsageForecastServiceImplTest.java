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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    @Mock
    private com.tansoflow.tansocore.repository.EventRepository eventRepository;
    @Mock
    private com.tansoflow.tansocore.repository.FeatureRepository featureRepository;

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

    // An upgrade retires the plan the usage was recorded on. Dropping it here would take the period's record with
    // it, and that period still has to be auditable. Found by the agent-ready end-to-end run, 2026-09-19.
    @Test
    void usageOnAPlanTheCustomerHasLeftIsStillReported() {
        Plan free = new Plan();
        free.setId(UUID.randomUUID());
        free.setKey("developer_demo");
        Subscription ended = new Subscription();
        ended.setId(UUID.randomUUID());
        ended.setPlan(free);
        ended.setIsActive(false);
        ended.setCurrentPeriodStart(Instant.now().minus(Duration.ofDays(3)));
        ended.setCurrentPeriodEnd(Instant.now());
        ended.setCancelledAt(Instant.now());

        Feature chat = new Feature();
        chat.setId(UUID.randomUUID());
        chat.setKey("ai.chat");
        PlanFeatureRule freeRule = new PlanFeatureRule();
        freeRule.setFeature(chat);
        when(planFeatureRuleRepository.findPlanFeatureRulesByPlanId(free.getId())).thenReturn(List.of(freeRule));
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId()))
                .thenReturn(List.of(ended, subscription));
        when(eventRepository.sumUsageUnitsBySubscriptionAndFeatureIdSince(
                eq(customer.getId()), eq(ended.getId()), eq(chat.getId()), any(), any()))
                .thenReturn(new BigDecimal("7"));
        when(clientEntitlementService.checkEntitlement("cust-1", accountId.toString(), "ai.chat", false))
                .thenReturn(entitlementWithUsage(new BigDecimal("2"), new BigDecimal("2000")));

        CustomerUsageResponse usage = service.getUsage("cust-1", accountId.toString());

        CustomerUsageResponse.SubscriptionUsage left = usage.getSubscriptions().stream()
                .filter(s -> "developer_demo".equals(s.getPlanKey())).findFirst().orElseThrow();
        assertThat(left.getStatus()).isEqualTo("ended");
        assertThat(left.getEndedAt()).isNotNull();
        assertThat(left.getFeatures().get(0).getUsed()).isEqualByComparingTo("7");
        // An ended plan grants nothing, so it carries no limit and no projection.
        assertThat(left.getFeatures().get(0).getLimit()).isNull();
        assertThat(left.getFeatures().get(0).getProjectedEndOfPeriod()).isNull();

        CustomerUsageResponse.SubscriptionUsage current = usage.getSubscriptions().stream()
                .filter(s -> "pro".equals(s.getPlanKey())).findFirst().orElseThrow();
        assertThat(current.getStatus()).isEqualTo("active");
        assertThat(current.getEndedAt()).isNull();
    }

    // History has to stop somewhere, or a long-lived customer's usage call grows without bound.
    @Test
    void aPlanLeftOverAYearAgoIsNotReported() {
        Plan old = new Plan();
        old.setId(UUID.randomUUID());
        old.setKey("legacy");
        Subscription ancient = new Subscription();
        ancient.setId(UUID.randomUUID());
        ancient.setPlan(old);
        ancient.setIsActive(false);
        ancient.setCurrentPeriodStart(Instant.now().minus(Duration.ofDays(800)));
        ancient.setCurrentPeriodEnd(Instant.now().minus(Duration.ofDays(770)));
        ancient.setCancelledAt(Instant.now().minus(Duration.ofDays(770)));
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId()))
                .thenReturn(List.of(ancient, subscription));
        when(clientEntitlementService.checkEntitlement("cust-1", accountId.toString(), "ai.chat", false))
                .thenReturn(entitlementWithUsage(new BigDecimal("1"), new BigDecimal("2000")));

        CustomerUsageResponse usage = service.getUsage("cust-1", accountId.toString());

        assertThat(usage.getSubscriptions()).extracting(CustomerUsageResponse.SubscriptionUsage::getPlanKey)
                .containsExactly("pro");
    }

    // History is keyed by customer and window, not by the subscription, so a plan change does not hide a period.
    @Test
    void historyReportsUsageFromTheEndedPlanAndTheCurrentOne() {
        UUID endedSubId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();
        Plan free = new Plan();
        free.setId(UUID.randomUUID());
        free.setKey("developer_demo");
        Subscription ended = new Subscription();
        ended.setId(endedSubId);
        ended.setPlan(free);
        ended.setIsActive(false);
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId()))
                .thenReturn(List.of(ended, subscription));

        Feature chat = new Feature();
        chat.setId(featureId);
        chat.setKey("ai.chat");
        when(featureRepository.findByIdAndAccountId(featureId, accountId)).thenReturn(Optional.of(chat));

        Instant first = Instant.now().minus(Duration.ofDays(2));
        Instant last = Instant.now().minus(Duration.ofDays(1));
        when(eventRepository.sumRecordedUsageByCustomerBetween(eq(customer.getId()), any(), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{endedSubId, featureId, "chat completion", new BigDecimal("7"), 3L, first, last},
                        new Object[]{subscription.getId(), featureId, "chat completion", new BigDecimal("2"), 1L, last, last}));

        var history = service.getUsageHistory("cust-1", accountId.toString(),
                Instant.now().minus(Duration.ofDays(30)), Instant.now(), null, null);

        assertThat(history.getUsage()).hasSize(2);
        var fromEndedPlan = history.getUsage().get(0);
        assertThat(fromEndedPlan.getPlanKey()).isEqualTo("developer_demo");
        assertThat(fromEndedPlan.getFeatureKey()).isEqualTo("ai.chat");
        assertThat(fromEndedPlan.getEventName()).isEqualTo("chat completion");
        assertThat(fromEndedPlan.getUsageUnits()).isEqualByComparingTo("7");
        // The count is the handle a customer reconciles the total against.
        assertThat(fromEndedPlan.getEvents()).isEqualTo(3L);
        assertThat(fromEndedPlan.getFirstOccurredAt()).isEqualTo(first);
    }

    @Test
    void historyCanBeNarrowedToOneSubscription() {
        UUID endedSubId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();
        Plan free = new Plan();
        free.setId(UUID.randomUUID());
        free.setKey("developer_demo");
        Subscription ended = new Subscription();
        ended.setId(endedSubId);
        ended.setPlan(free);
        ended.setIsActive(false);
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId()))
                .thenReturn(List.of(ended, subscription));
        Feature chat = new Feature();
        chat.setId(featureId);
        chat.setKey("ai.chat");
        lenient().when(featureRepository.findByIdAndAccountId(featureId, accountId)).thenReturn(Optional.of(chat));
        when(eventRepository.sumRecordedUsageByCustomerBetween(eq(customer.getId()), any(), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{endedSubId, featureId, "chat completion", new BigDecimal("7"), 3L, Instant.now(), Instant.now()},
                        new Object[]{subscription.getId(), featureId, "chat completion", new BigDecimal("2"), 1L, Instant.now(), Instant.now()}));

        var history = service.getUsageHistory("cust-1", accountId.toString(),
                Instant.now().minus(Duration.ofDays(30)), Instant.now(), null, endedSubId.toString());

        assertThat(history.getUsage()).hasSize(1);
        assertThat(history.getUsage().get(0).getSubscriptionId()).isEqualTo(endedSubId.toString());
    }

    // Usage recorded with no subscription attached is still the customer's, and still has to be auditable.
    @Test
    void historyKeepsUsageThatCarriesNoSubscription() {
        UUID featureId = UUID.randomUUID();
        when(subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId())).thenReturn(List.of());
        when(featureRepository.findByIdAndAccountId(featureId, accountId)).thenReturn(Optional.empty());
        when(eventRepository.sumRecordedUsageByCustomerBetween(eq(customer.getId()), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{null, featureId, "ai.chat", new BigDecimal("4"), 2L, Instant.now(), Instant.now()}));

        var history = service.getUsageHistory("cust-1", accountId.toString(),
                Instant.now().minus(Duration.ofDays(7)), Instant.now(), null, null);

        assertThat(history.getUsage()).hasSize(1);
        assertThat(history.getUsage().get(0).getSubscriptionId()).isNull();
        assertThat(history.getUsage().get(0).getPlanKey()).isNull();
        assertThat(history.getUsage().get(0).getUsageUnits()).isEqualByComparingTo("4");
    }

    // The aggregate on the usage endpoints has to be checkable against the records it was built from.
    @Test
    void recordedEventsComeBackWithTheirIdempotencyKeyAndFeature() {
        UUID featureId = UUID.randomUUID();
        Feature chat = new Feature();
        chat.setId(featureId);
        chat.setKey("ai.chat");
        when(featureRepository.findByIdAndAccountId(featureId, accountId)).thenReturn(Optional.of(chat));

        com.tansoflow.tansocore.entity.Event event = new com.tansoflow.tansocore.entity.Event();
        event.setId(UUID.randomUUID());
        event.setEventIdempotencyKey("run-1-evt-1");
        event.setEventName("chat completion");
        event.setFeatureId(featureId);
        event.setSubscriptionId(subscription.getId());
        event.setUsageUnits(new BigDecimal("3"));
        event.setOccurredAt(Instant.now().minus(Duration.ofHours(2)));
        when(eventRepository.findRecordedEvents(eq(customer.getId()), eq(accountId), any(), any(), eq(null), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(event),
                        org.springframework.data.domain.PageRequest.of(0, 50), 1));

        var events = service.getRecordedEvents("cust-1", accountId.toString(),
                Instant.now().minus(Duration.ofDays(7)), Instant.now(), null, 0, 50);

        assertThat(events.getEvents()).hasSize(1);
        var only = events.getEvents().get(0);
        assertThat(only.getEventIdempotencyKey()).isEqualTo("run-1-evt-1");
        assertThat(only.getFeatureKey()).isEqualTo("ai.chat");
        assertThat(only.getEventName()).isEqualTo("chat completion");
        assertThat(only.getUsageUnits()).isEqualByComparingTo("3");
        assertThat(events.getHasMore()).isFalse();
    }

    @Test
    void moreEventsThanAPageSaysSo() {
        com.tansoflow.tansocore.entity.Event event = new com.tansoflow.tansocore.entity.Event();
        event.setId(UUID.randomUUID());
        event.setOccurredAt(Instant.now());
        when(eventRepository.findRecordedEvents(eq(customer.getId()), eq(accountId), any(), any(), eq(null), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(event),
                        org.springframework.data.domain.PageRequest.of(0, 1), 5));

        var events = service.getRecordedEvents("cust-1", accountId.toString(),
                Instant.now().minus(Duration.ofDays(7)), Instant.now(), null, 0, 1);

        assertThat(events.getHasMore()).isTrue();
    }

    // A feature nobody has heard of matches nothing, rather than silently matching everything.
    @Test
    void anUnknownFeatureFilterReturnsNothing() {
        when(featureRepository.findByKeyAndAccountId("nope", accountId)).thenReturn(Optional.empty());

        var events = service.getRecordedEvents("cust-1", accountId.toString(),
                Instant.now().minus(Duration.ofDays(7)), Instant.now(), "nope", 0, 50);

        assertThat(events.getEvents()).isEmpty();
        assertThat(events.getHasMore()).isFalse();
        verify(eventRepository, never()).findRecordedEvents(any(), any(), any(), any(), any(), any());
    }
}
