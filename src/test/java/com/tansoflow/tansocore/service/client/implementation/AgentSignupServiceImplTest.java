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
import com.tansoflow.tansocore.entity.AccountSetting;
import com.tansoflow.tansocore.entity.AgentStatus;
import com.tansoflow.tansocore.entity.CheckoutSession;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.integration.stripe.StripePaymentMethodService;
import com.tansoflow.tansocore.model.apikey.CustomerApiKeyDto;
import com.tansoflow.tansocore.model.customer.request.CustomerRequest;
import com.tansoflow.tansocore.model.entitlement.response.EntitlementResponse;
import com.tansoflow.tansocore.model.exception.RateLimitExceededException;
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.model.feature.FeatureDto;
import com.tansoflow.tansocore.model.signup.AgentSignupResponse;
import com.tansoflow.tansocore.model.signup.request.AgentSignupRequest;
import com.tansoflow.tansocore.model.api.external.StripeMode;
import com.tansoflow.tansocore.repository.AccountRepository;
import com.tansoflow.tansocore.repository.AccountSettingRepository;
import com.tansoflow.tansocore.repository.CheckoutSessionRepository;
import com.tansoflow.tansocore.repository.CustomerRepository;
import com.tansoflow.tansocore.repository.PlanRepository;
import com.tansoflow.tansocore.service.client.ClientEntitlementService;
import com.tansoflow.tansocore.service.internal.account.CustomerApiKeyService;
import com.tansoflow.tansocore.service.internal.account.CustomerService;
import com.tansoflow.tansocore.service.internal.monetization.FeatureService;
import com.tansoflow.tansocore.service.internal.monetization.SubscriptionService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentSignupServiceImplTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AccountSettingRepository accountSettingRepository;
    @Mock
    private PlanRepository planRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private CustomerService customerService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private CustomerApiKeyService customerApiKeyService;
    @Mock
    private FeatureService featureService;
    @Mock
    private ClientEntitlementService clientEntitlementService;
    @Mock
    private StripePaymentMethodService stripePaymentMethodService;
    @Mock
    private CheckoutSessionRepository checkoutSessionRepository;
    @Mock
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    @Mock
    private EntityManager entityManager;
    @Mock
    private Query lockQuery;

    @InjectMocks
    private AgentSignupServiceImpl service;

    private final UUID accountId = UUID.randomUUID();
    private final UUID planId = UUID.randomUUID();
    private Account account;
    private AccountSetting settings;
    private Plan plan;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv ->
                ((org.springframework.transaction.support.TransactionCallback<Object>) inv.getArgument(0))
                        .doInTransaction(new org.springframework.transaction.support.SimpleTransactionStatus()));
        lenient().when(entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:k))")).thenReturn(lockQuery);
        lenient().when(lockQuery.setParameter(eq("k"), anyString())).thenReturn(lockQuery);
        account = new Account();
        account.setId(accountId);
        account.setSlug("acme");

        settings = new AccountSetting();
        settings.setAccounts(account);
        settings.setAgentSignupEnabled(true);
        settings.setAgentSignupDefaultPlanId(planId);
        settings.setAgentSignupHourlyCap(2);
        settings.setAgentSignupPerIpCap(2);
        settings.setAgentProvisionalDays(14);
        settings.setAgentMaxTopupAmount(new BigDecimal("50.00"));

        plan = new Plan();
        plan.setId(planId);
        plan.setKey("free");
        plan.setIntervalMonths(1);

        lenient().when(accountRepository.findBySlug("acme")).thenReturn(Optional.of(account));
        lenient().when(accountSettingRepository.findAccountSettingById(accountId)).thenReturn(settings);
        lenient().when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        FeatureDto chat = new FeatureDto();
        chat.setKey("ai.chat");
        lenient().when(featureService.retrieveFeaturesLinkedToPlan(plan)).thenReturn(List.of(chat));
        lenient().when(customerRepository.countAgentSignupsSince(eq(accountId), any())).thenReturn(0L);
        lenient().when(customerRepository.countAgentSignupsFromIpSince(anyString(), any())).thenReturn(0L);
        lenient().when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(customerService.createCustomer(eq(accountId.toString()), any(CustomerRequest.class)))
                .thenAnswer(inv -> {
                    Customer customer = new Customer();
                    customer.setId(UUID.randomUUID());
                    customer.setAccount(account);
                    customer.setExternalClientCustomerId(
                            ((CustomerRequest) inv.getArgument(1)).getCustomerReferenceId());
                    customer.setEmail(((CustomerRequest) inv.getArgument(1)).getEmail());
                    return customer;
                });
        lenient().when(customerApiKeyService.createKey(eq(accountId.toString()), anyString(), any()))
                .thenAnswer(inv -> CustomerApiKeyDto.builder()
                        .id(UUID.randomUUID().toString())
                        .apiKey("ck_test_generated")
                        .scopes(List.of("read", "purchase"))
                        .customerReferenceId(inv.getArgument(1))
                        .build());
        EntitlementResponse entitlement = new EntitlementResponse();
        EntitlementResponse.Usage usage = new EntitlementResponse.Usage();
        usage.setLimit(new BigDecimal("1000"));
        usage.setUnlimited(false);
        entitlement.setUsage(usage);
        lenient().when(clientEntitlementService.checkEntitlement(anyString(), eq(accountId.toString()), eq("ai.chat"), eq(false)))
                .thenReturn(entitlement);
    }

    @Test
    void signupWithoutEmailCreatesProvisionalCustomerWithExpiryAndLimits() {
        AgentSignupResponse response = service.signup("acme", new AgentSignupRequest(), "https://billing.acme.ai", "203.0.113.9");

        assertThat(response.getCustomerReferenceId()).startsWith("agent_");
        assertThat(response.getApiKey()).isEqualTo("ck_test_generated");
        assertThat(response.getPlan()).isEqualTo("free");
        assertThat(response.getStatus()).isEqualTo("provisional");
        assertThat(response.getExpiresAt()).isCloseTo(Instant.now().plus(Duration.ofDays(14)), within(1, java.time.temporal.ChronoUnit.MINUTES));
        assertThat(response.getLimits().getFeatures().get("ai.chat").getIncluded()).isEqualByComparingTo("1000");
        assertThat(response.getLimits().getFeatures().get("ai.chat").isUnlimited()).isFalse();
        assertThat(response.getLimits().getFeatures().get("ai.chat").getPeriod()).isEqualTo("month");
        assertThat(response.getLimits().getSpendCap()).isEqualByComparingTo("50.00");
        assertThat(response.getSpendMandate()).isNull();
        assertThat(response.getStatusUrl())
                .isEqualTo("https://billing.acme.ai/api/v1/client/customers/" + response.getCustomerReferenceId() + "/status");
        assertThat(response.getOwnerUrl())
                .isEqualTo("https://billing.acme.ai/api/v1/client/customers/" + response.getCustomerReferenceId() + "/owner");
        assertThat(response.getNextSteps())
                .containsKeys("pricing", "check_entitlement_template", "check_entitlement_example",
                        "record_usage", "usage_summary", "credit_balances", "buy_credits", "change_plan", "runbook", "docs");
        assertThat(response.getNextSteps().get("check_entitlement_example"))
                .endsWith("/" + response.getCustomerReferenceId() + "/ai.chat")
                .doesNotContain("{featureKey}");

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(saved.capture());
        assertThat(saved.getValue().getAgentStatus()).isEqualTo(AgentStatus.PROVISIONAL);
        assertThat(saved.getValue().getAgentOwnerEmail()).isNull();
        assertThat(saved.getValue().getAgentSignupIp()).isEqualTo("203.0.113.9");

        ArgumentCaptor<Customer> subscribed = ArgumentCaptor.forClass(Customer.class);
        verify(subscriptionService).subscribe(subscribed.capture(), eq(plan), eq(accountId.toString()));
        assertThat(subscribed.getValue().getExternalClientCustomerId()).isEqualTo(response.getCustomerReferenceId());
    }

    @Test
    void emailIsRecordedAsOwnerAndNeverResolvesToAnotherCustomer() {
        AgentSignupRequest request = new AgentSignupRequest();
        request.setEmail("Owner@Example.com");

        AgentSignupResponse first = service.signup("acme", request, "http://x", "1.1.1.1");
        AgentSignupResponse second = service.signup("acme", request, "http://x", "1.1.1.1");

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(c -> assertThat(c.getAgentOwnerEmail()).isEqualTo("owner@example.com"));
        assertThat(second.getCustomerReferenceId()).isNotEqualTo(first.getCustomerReferenceId());
        verify(subscriptionService, org.mockito.Mockito.times(2)).subscribe(any(), eq(plan), eq(accountId.toString()));
    }

    @Test
    void disabledSignupAndUnknownSlugAreIndistinguishable() {
        when(accountRepository.findBySlug("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.signup("missing", new AgentSignupRequest(), "http://x", null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("No signup at this address");

        settings.setAgentSignupEnabled(false);
        assertThatThrownBy(() -> service.signup("acme", new AgentSignupRequest(), "http://x", null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("No signup at this address");

        settings.setAgentSignupEnabled(true);
        settings.setAgentSignupDefaultPlanId(null);
        assertThatThrownBy(() -> service.signup("acme", new AgentSignupRequest(), "http://x", null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("No signup at this address");
    }

    @Test
    void hourlyCapReturns429WithRetryAfter() {
        when(customerRepository.countAgentSignupsSince(eq(accountId), any())).thenReturn(2L);
        assertThatThrownBy(() -> service.signup("acme", new AgentSignupRequest(), "http://x", null))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(e -> assertThat(((RateLimitExceededException) e).getRetryAfterSeconds()).isEqualTo(3600));
    }

    @Test
    void perIpCapReturns429AndDoesNotCreateACustomer() {
        when(customerRepository.countAgentSignupsFromIpSince(eq("9.9.9.9"), any())).thenReturn(2L);
        assertThatThrownBy(() -> service.signup("acme", new AgentSignupRequest(), "http://x", "9.9.9.9"))
                .isInstanceOf(RateLimitExceededException.class);
        verify(customerService, never()).createCustomer(anyString(), any());
    }

    @Test
    void capsAreCountedUnderTheAccountAndAddressLocksInsideTheSignupTransaction() {
        service.signup("acme", new AgentSignupRequest(), "http://x", "9.9.9.9");

        // Lock, count, insert — all inside the one transaction, or concurrent signups each read a count under the cap
        InOrder inOrder = inOrder(transactionTemplate, lockQuery, customerRepository, customerService);
        inOrder.verify(transactionTemplate).execute(any());
        inOrder.verify(lockQuery).setParameter("k", "agent-signup:" + accountId);
        inOrder.verify(lockQuery).getSingleResult();
        inOrder.verify(lockQuery).setParameter("k", "agent-signup-ip:9.9.9.9");
        inOrder.verify(lockQuery).getSingleResult();
        inOrder.verify(customerRepository).countAgentSignupsSince(eq(accountId), any());
        inOrder.verify(customerRepository).countAgentSignupsFromIpSince(eq("9.9.9.9"), any());
        inOrder.verify(customerService).createCustomer(eq(accountId.toString()), any(CustomerRequest.class));
    }

    @Test
    void withoutAnAddressOnlyTheAccountLockIsTaken() {
        service.signup("acme", new AgentSignupRequest(), "http://x", null);

        verify(lockQuery).setParameter("k", "agent-signup:" + accountId);
        verify(lockQuery, times(1)).getSingleResult();
        verify(customerRepository, never()).countAgentSignupsFromIpSince(any(), any());
    }

    @Test
    void spendMandateIsUnavailableWhenTheOperatorHasNotEnabledIt() {
        AgentSignupRequest request = new AgentSignupRequest();
        AgentSignupRequest.SpendMandate mandate = new AgentSignupRequest.SpendMandate();
        mandate.setMaxAmount(new BigDecimal("25"));
        request.setSpendMandate(mandate);

        AgentSignupResponse response = service.signup("acme", request, "http://x", null);

        assertThat(response.getSpendMandate().getStatus()).isEqualTo("unavailable");
        assertThat(response.getSpendMandate().getMaxAmount()).isEqualByComparingTo("25");
        assertThat(response.getSpendMandate().getPeriod()).isEqualTo("month");
        verify(checkoutSessionRepository, never()).save(any());
    }

    @Test
    void spendMandateInAnotherCurrencyIsRejected() {
        settings.setAgentSpendMandateEnabled(true);
        settings.setStripeMode(StripeMode.PAYMENT_PASS_THROUGH);
        AgentSignupRequest request = new AgentSignupRequest();
        AgentSignupRequest.SpendMandate mandate = new AgentSignupRequest.SpendMandate();
        mandate.setMaxAmount(new BigDecimal("25"));
        mandate.setCurrency("eur");
        request.setSpendMandate(mandate);

        assertThatThrownBy(() -> service.signup("acme", request, "http://x", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("USD");
        verify(customerService, never()).createCustomer(anyString(), any());
        verify(customerApiKeyService, never()).createKey(anyString(), anyString(), any());
    }

    @Test
    void aStripeHelperFailureStillReturnsTheKeyWithTheMandateUnavailable() throws Exception {
        settings.setAgentSpendMandateEnabled(true);
        settings.setStripeMode(StripeMode.PAYMENT_PASS_THROUGH);
        when(stripePaymentMethodService.createSetupCheckoutSession(eq(accountId), any(), any()))
                .thenThrow(new RuntimeException("wrapped: Customer not found"));
        AgentSignupRequest request = new AgentSignupRequest();
        AgentSignupRequest.SpendMandate mandate = new AgentSignupRequest.SpendMandate();
        mandate.setMaxAmount(new BigDecimal("25"));
        request.setSpendMandate(mandate);

        AgentSignupResponse response = service.signup("acme", request, "http://x", null);

        assertThat(response.getApiKey()).isEqualTo("ck_test_generated");
        assertThat(response.getSpendMandate().getStatus()).isEqualTo("unavailable");
    }

    @Test
    void stripeFailureLeavesTheSignupIntactAndTheMandateUnavailable() throws Exception {
        settings.setAgentSpendMandateEnabled(true);
        settings.setStripeMode(StripeMode.PAYMENT_PASS_THROUGH);
        when(stripePaymentMethodService.createSetupCheckoutSession(eq(accountId), any(), any()))
                .thenThrow(new com.stripe.exception.ApiConnectionException("stripe down"));
        AgentSignupRequest request = new AgentSignupRequest();
        AgentSignupRequest.SpendMandate mandate = new AgentSignupRequest.SpendMandate();
        mandate.setMaxAmount(new BigDecimal("25"));
        request.setSpendMandate(mandate);

        AgentSignupResponse response = service.signup("acme", request, "http://x", null);

        assertThat(response.getApiKey()).isEqualTo("ck_test_generated");
        assertThat(response.getSpendMandate().getStatus()).isEqualTo("unavailable");
        verify(checkoutSessionRepository, never()).save(any());
    }

    @Test
    void spendMandateOpensASetupSessionAndRecordsItWhenEnabled() throws Exception {
        settings.setAgentSpendMandateEnabled(true);
        settings.setStripeMode(StripeMode.PAYMENT_PASS_THROUGH);
        when(stripePaymentMethodService.createSetupCheckoutSession(eq(accountId), any(), any()))
                .thenReturn(new StripePaymentMethodService.HostedCheckout("https://checkout.stripe.com/c/setup_1", "cs_setup_1"));

        AgentSignupRequest request = new AgentSignupRequest();
        AgentSignupRequest.SpendMandate mandate = new AgentSignupRequest.SpendMandate();
        mandate.setMaxAmount(new BigDecimal("100"));
        mandate.setPeriod("week");
        request.setSpendMandate(mandate);

        AgentSignupResponse response = service.signup("acme", request, "http://x", null);

        assertThat(response.getSpendMandate().getStatus()).isEqualTo("pending");
        assertThat(response.getSpendMandate().getSetupUrl()).isEqualTo("https://checkout.stripe.com/c/setup_1");
        ArgumentCaptor<CheckoutSession> session = ArgumentCaptor.forClass(CheckoutSession.class);
        verify(checkoutSessionRepository).save(session.capture());
        assertThat(session.getValue().getPurpose()).isEqualTo(CheckoutSession.PURPOSE_SPEND_MANDATE);
        assertThat(session.getValue().getStripeSessionId()).isEqualTo("cs_setup_1");
        assertThat(session.getValue().getAmount()).isEqualByComparingTo("100");
        assertThat(session.getValue().getApiKeyId()).isNotNull();
    }
}
