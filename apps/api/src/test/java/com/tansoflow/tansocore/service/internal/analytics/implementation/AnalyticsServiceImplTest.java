package com.tansoflow.tansocore.service.internal.analytics.implementation;

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.Entitlement;
import com.tansoflow.tansocore.entity.Feature;
import com.tansoflow.tansocore.entity.Invoice;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.entity.PlanFeatureRule;
import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.model.analytics.AnalyticsResponseDto;
import com.tansoflow.tansocore.model.analytics.ChurnScoreDto;
import com.tansoflow.tansocore.model.analytics.CreditImpactDto;
import com.tansoflow.tansocore.model.analytics.CustomerAnalyticsDto;
import com.tansoflow.tansocore.repository.EntitlementRepository;
import com.tansoflow.tansocore.repository.EventRepository;
import com.tansoflow.tansocore.repository.InvoiceItemRepository;
import com.tansoflow.tansocore.repository.InvoiceRepository;
import com.tansoflow.tansocore.repository.PlanFeatureRuleRepository;
import com.tansoflow.tansocore.repository.SubscriptionRepository;
import com.tansoflow.tansocore.repository.SubscriptionScheduledChangeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceImplTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PlanFeatureRuleRepository planFeatureRuleRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private SubscriptionScheduledChangeRepository scheduledChangeRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private InvoiceItemRepository invoiceItemRepository;

    @Mock
    private EntitlementRepository entitlementRepository;

    @InjectMocks
    private AnalyticsServiceImpl analyticsService;

    private UUID accountId;
    private Plan plan;
    private Feature feature;
    private Customer customer;
    private Subscription subscription;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();

        Account account = new Account();
        account.setId(accountId);

        feature = new Feature();
        feature.setId(UUID.randomUUID());
        feature.setName("AI Tokens");

        plan = new Plan();
        plan.setId(UUID.randomUUID());
        plan.setName("Pro Plan");
        plan.setPriceAmount(new BigDecimal("100.00"));
        plan.setAccount(account);

        customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setEmail("test@example.com");
        customer.setFirstName("John");
        customer.setLastName("Doe");
        customer.setAccount(account);

        subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setAccount(account);
        subscription.setCustomer(customer);
        subscription.setPlan(plan);
        subscription.setIsActive(true);
        subscription.setCurrentPeriodStart(Instant.now().minusSeconds(86400 * 30));
        subscription.setCurrentPeriodEnd(Instant.now());
    }

    private void stubChurnAndCreditDefaults() {
        when(entitlementRepository.findActiveEntitlementsByCustomerIn(any()))
                .thenReturn(List.of());
        when(eventRepository.countEventsGroupedByCustomerInRange(any(), any(), any(), any()))
                .thenReturn(List.of());
        when(invoiceRepository.getInvoicesByAccount_Id(accountId))
                .thenReturn(List.of());
    }

    @Test
    void testGetPortfolioAnalytics_CalculatesCostFromCostModel() {
        // Arrange
        when(subscriptionRepository.findActiveSubscriptionsByAccountId(accountId))
                .thenReturn(List.of(subscription));
        when(scheduledChangeRepository.findAllPendingChangesByAccountId(accountId))
                .thenReturn(List.of());

        PlanFeatureRule rule = new PlanFeatureRule();
        rule.setPlan(plan);
        rule.setFeature(feature);
        rule.setValue(Map.of(
                "cost_model", "simple",
                "cost_per_unit", 0.05,
                "model", "usage",
                "price_per_unit", 0.10
        ));

        when(planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(any()))
                .thenReturn(List.of(rule));

        // 30 units of usage for the feature
        when(eventRepository.sumUsageGroupedBySubscriptionAndFeatureIncludingUntagged(
                any(), any(), any(), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{subscription.getId(), feature.getId(), new BigDecimal("30")}));

        stubChurnAndCreditDefaults();

        // Act
        AnalyticsResponseDto response = analyticsService.getPortfolioAnalytics(accountId.toString());

        // Assert
        assertEquals(1, response.getCustomers().size());
        CustomerAnalyticsDto customerDto = response.getCustomers().getFirst();

        // Cost from CostModel: 30 * 0.05 = 1.50
        BigDecimal expectedTotalCost = new BigDecimal("1.50");
        assertEquals(0, expectedTotalCost.compareTo(customerDto.getTotalCost()),
                "Total cost should be derived from CostModel, not event costAmount");

        // Usage revenue from PricingModel: 30 * 0.10 = 3.00
        assertEquals(0, new BigDecimal("3.00").compareTo(customerDto.getProjectedUsageRevenue()));

        // Effective MRR: 100 + 3.00 = 103.00
        assertEquals(0, new BigDecimal("103.00").compareTo(customerDto.getEffectiveMrr()));

        // RGP: effectiveMrr - totalCost = 103.00 - 1.50 = 101.50
        assertNotNull(customerDto.getRgp());
        assertEquals(0, new BigDecimal("101.50").compareTo(customerDto.getRgp()),
                "RGP should be effectiveMrr - totalCost");

        // Verify summary
        assertEquals(0, expectedTotalCost.compareTo(response.getSummary().getTotalCosts()));
        assertEquals(0, new BigDecimal("100.00").compareTo(response.getSummary().getTotalMrr()));

        // Verify summary RGP
        assertNotNull(response.getSummary().getTotalRgp());
        assertNotNull(response.getSummary().getRgpMargin());
    }

    @Test
    void testGetPortfolioAnalytics_NoCostModel_ReturnsNullCost() {
        // Arrange — rule with pricing only, no cost_model key
        when(subscriptionRepository.findActiveSubscriptionsByAccountId(accountId))
                .thenReturn(List.of(subscription));
        when(scheduledChangeRepository.findAllPendingChangesByAccountId(accountId))
                .thenReturn(List.of());

        PlanFeatureRule rule = new PlanFeatureRule();
        rule.setPlan(plan);
        rule.setFeature(feature);
        rule.setValue(Map.of(
                "model", "usage",
                "price_per_unit", 20.00
        ));

        when(planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(any()))
                .thenReturn(List.of(rule));

        // 5 units of usage
        when(eventRepository.sumUsageGroupedBySubscriptionAndFeatureIncludingUntagged(
                any(), any(), any(), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{subscription.getId(), feature.getId(), new BigDecimal("5")}));

        stubChurnAndCreditDefaults();

        // Act
        AnalyticsResponseDto response = analyticsService.getPortfolioAnalytics(accountId.toString());

        // Assert
        CustomerAnalyticsDto customerDto = response.getCustomers().getFirst();

        // No cost model → totalCost, margin, and RGP should be null
        assertNull(customerDto.getTotalCost(), "Cost should be null when no cost model is configured");
        assertNull(customerDto.getMargin(), "Margin should be null when cost is unknown");
        assertNull(customerDto.getMarginStatus(), "Margin status should be null when cost is unknown");
        assertNull(customerDto.getRgp(), "RGP should be null when cost is unknown");

        // Usage revenue from PricingModel: 5 * 20.00 = 100.00
        assertEquals(0, new BigDecimal("100.00").compareTo(customerDto.getProjectedUsageRevenue()));
        assertEquals(0, new BigDecimal("200.00").compareTo(customerDto.getEffectiveMrr()));

        // Summary should also show null costs/margin when no cost data
        assertNull(response.getSummary().getTotalCosts(), "Summary totalCosts should be null when no cost model");
        assertNull(response.getSummary().getAvgMargin(), "Summary avgMargin should be null when no cost model");
        assertNull(response.getSummary().getTotalRgp(), "Summary totalRgp should be null when no cost model");
    }

    @Test
    void testGetPortfolioAnalytics_MultipleCustomersAndFeatures() {
        // Arrange
        Customer customer2 = new Customer();
        customer2.setId(UUID.randomUUID());
        customer2.setEmail("jane@example.com");
        customer2.setAccount(subscription.getAccount());

        Subscription subscription2 = new Subscription();
        subscription2.setId(UUID.randomUUID());
        subscription2.setAccount(subscription.getAccount());
        subscription2.setCustomer(customer2);
        subscription2.setPlan(plan);
        subscription2.setIsActive(true);
        subscription2.setCurrentPeriodStart(subscription.getCurrentPeriodStart());
        subscription2.setCurrentPeriodEnd(subscription.getCurrentPeriodEnd());

        when(subscriptionRepository.findActiveSubscriptionsByAccountId(accountId))
                .thenReturn(List.of(subscription, subscription2));
        when(scheduledChangeRepository.findAllPendingChangesByAccountId(accountId))
                .thenReturn(List.of());

        Feature feature2 = new Feature();
        feature2.setId(UUID.randomUUID());
        feature2.setName("Storage");

        PlanFeatureRule rule1 = new PlanFeatureRule();
        rule1.setPlan(plan);
        rule1.setFeature(feature);
        rule1.setValue(Map.of(
                "cost_model", "simple",
                "cost_per_unit", 0.10,
                "model", "usage",
                "price_per_unit", 0.20
        ));

        PlanFeatureRule rule2 = new PlanFeatureRule();
        rule2.setPlan(plan);
        rule2.setFeature(feature2);
        rule2.setValue(Map.of(
                "cost_model", "simple",
                "cost_per_unit", 0.50,
                "model", "usage",
                "price_per_unit", 1.00
        ));

        when(planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(any()))
                .thenReturn(List.of(rule1, rule2));

        // Customer 1: 5 units of feature1 -> cost = 5 * 0.10 = 0.50
        // Customer 2: 10 units of feature1 + 2 units of feature2 -> cost = 10 * 0.10 + 2 * 0.50 = 2.00
        when(eventRepository.sumUsageGroupedBySubscriptionAndFeatureIncludingUntagged(
                any(), any(), any(), any(), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{subscription.getId(), feature.getId(), new BigDecimal("5")}
                ))
                .thenReturn(List.<Object[]>of(
                        new Object[]{subscription2.getId(), feature.getId(), new BigDecimal("10")},
                        new Object[]{subscription2.getId(), feature2.getId(), new BigDecimal("2")}
                ));

        stubChurnAndCreditDefaults();

        // Act
        AnalyticsResponseDto response = analyticsService.getPortfolioAnalytics(accountId.toString());

        // Assert
        assertEquals(2, response.getCustomers().size());

        // Total costs: Customer 1 = 0.50, Customer 2 = 2.00. Total = 2.50
        assertEquals(0, new BigDecimal("2.50").compareTo(response.getSummary().getTotalCosts()));
        assertEquals(0, new BigDecimal("200.00").compareTo(response.getSummary().getTotalMrr()));
    }

    @Test
    void testChurnScoring_WithEntitlementsAndEvents() {
        // Arrange
        when(subscriptionRepository.findActiveSubscriptionsByAccountId(accountId))
                .thenReturn(List.of(subscription));
        when(scheduledChangeRepository.findAllPendingChangesByAccountId(accountId))
                .thenReturn(List.of());

        PlanFeatureRule rule = new PlanFeatureRule();
        rule.setPlan(plan);
        rule.setFeature(feature);
        rule.setValue(Map.of(
                "cost_model", "simple",
                "cost_per_unit", 0.05,
                "model", "usage",
                "price_per_unit", 0.10
        ));

        when(planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(any()))
                .thenReturn(List.of(rule));

        when(eventRepository.sumUsageGroupedBySubscriptionAndFeatureIncludingUntagged(
                any(), any(), any(), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{subscription.getId(), feature.getId(), new BigDecimal("10")}));

        // Set up entitlements with recent access
        Entitlement entitlement = new Entitlement();
        entitlement.setId(UUID.randomUUID());
        entitlement.setCustomer(customer);
        entitlement.setFeatureKey("ai_tokens");
        // Use reflection or just leave lastAccessed as recent
        entitlement.setLastAccessed(Instant.now().minus(2, ChronoUnit.DAYS));

        when(entitlementRepository.findActiveEntitlementsByCustomerIn(any()))
                .thenReturn(List.of(entitlement));

        // Current period: 100 events, Prior period: 80 events (growing activity)
        when(eventRepository.countEventsGroupedByCustomerInRange(any(), any(), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{customer.getId(), 100L}))
                .thenReturn(List.<Object[]>of(new Object[]{customer.getId(), 80L}));

        when(invoiceRepository.getInvoicesByAccount_Id(accountId))
                .thenReturn(List.of());

        // Act
        AnalyticsResponseDto response = analyticsService.getPortfolioAnalytics(accountId.toString());

        // Assert
        CustomerAnalyticsDto customerDto = response.getCustomers().getFirst();
        ChurnScoreDto churnScore = customerDto.getChurnScoreDetails();

        assertNotNull(churnScore, "Churn score should be computed");
        assertNotNull(churnScore.getScore());
        assertNotNull(churnScore.getRiskLabel());
        assertTrue(churnScore.getScore() >= 0 && churnScore.getScore() <= 100,
                "Score should be between 0 and 100");
        assertEquals(0, churnScore.getEventTrendScore(),
                "Growing activity should produce event trend score of 0");

        // Portfolio-level churn aggregates
        assertNotNull(response.getSummary().getAvgChurnScore());
    }

    @Test
    void testCreditImpact_WithCreditInvoices() {
        // Arrange
        when(subscriptionRepository.findActiveSubscriptionsByAccountId(accountId))
                .thenReturn(List.of(subscription));
        when(scheduledChangeRepository.findAllPendingChangesByAccountId(accountId))
                .thenReturn(List.of());

        PlanFeatureRule rule = new PlanFeatureRule();
        rule.setPlan(plan);
        rule.setFeature(feature);
        rule.setValue(Map.of(
                "model", "usage",
                "price_per_unit", 0.10
        ));

        when(planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(any()))
                .thenReturn(List.of(rule));

        when(eventRepository.sumUsageGroupedBySubscriptionAndFeatureIncludingUntagged(
                any(), any(), any(), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{subscription.getId(), feature.getId(), new BigDecimal("10")}));

        when(entitlementRepository.findActiveEntitlementsByCustomerIn(any()))
                .thenReturn(List.of());
        when(eventRepository.countEventsGroupedByCustomerInRange(any(), any(), any(), any()))
                .thenReturn(List.of());

        // Create a credit invoice
        Account account = new Account();
        account.setId(accountId);

        Invoice creditInvoice = new Invoice();
        creditInvoice.setId(UUID.randomUUID());
        creditInvoice.setType("CREDIT");
        creditInvoice.setAmount(new BigDecimal("-25.00"));
        creditInvoice.setSubscription(subscription);
        creditInvoice.setAccount(account);
        creditInvoice.setStatus("PAID");

        when(invoiceRepository.getInvoicesByAccount_Id(accountId))
                .thenReturn(List.of(creditInvoice));

        // Act
        AnalyticsResponseDto response = analyticsService.getPortfolioAnalytics(accountId.toString());

        // Assert
        CustomerAnalyticsDto customerDto = response.getCustomers().getFirst();
        assertNotNull(customerDto.getTotalCredits(), "Customer should have total credits");
        assertEquals(0, new BigDecimal("-25.00").compareTo(customerDto.getTotalCredits()));

        CreditImpactDto creditImpact = response.getSummary().getCreditImpact();
        assertNotNull(creditImpact, "Portfolio should have credit impact");
        assertEquals(1, creditImpact.getCreditInvoiceCount());
        assertEquals(0, new BigDecimal("-25.00").compareTo(creditImpact.getTotalCredits()));
        assertNotNull(creditImpact.getNetEffectiveMrr());
        assertNotNull(creditImpact.getCreditToMrrRatio());
    }

    @Test
    void testCreditImpact_NoCreditInvoices_ReturnsNull() {
        // Arrange
        when(subscriptionRepository.findActiveSubscriptionsByAccountId(accountId))
                .thenReturn(List.of(subscription));
        when(scheduledChangeRepository.findAllPendingChangesByAccountId(accountId))
                .thenReturn(List.of());

        PlanFeatureRule rule = new PlanFeatureRule();
        rule.setPlan(plan);
        rule.setFeature(feature);
        rule.setValue(Map.of(
                "model", "usage",
                "price_per_unit", 0.10
        ));

        when(planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(any()))
                .thenReturn(List.of(rule));

        when(eventRepository.sumUsageGroupedBySubscriptionAndFeatureIncludingUntagged(
                any(), any(), any(), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{subscription.getId(), feature.getId(), new BigDecimal("10")}));

        stubChurnAndCreditDefaults();

        // Act
        AnalyticsResponseDto response = analyticsService.getPortfolioAnalytics(accountId.toString());

        // Assert
        CustomerAnalyticsDto customerDto = response.getCustomers().getFirst();
        assertNull(customerDto.getTotalCredits(), "No credit invoices should yield null");
        assertNull(response.getSummary().getCreditImpact(), "No credit invoices should yield null credit impact");
    }
}
