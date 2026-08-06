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
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.model.apikey.CustomerApiKeyDto;
import com.tansoflow.tansocore.model.customer.request.CustomerRequest;
import com.tansoflow.tansocore.model.exception.RateLimitExceededException;
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.model.signup.AgentSignupResponse;
import com.tansoflow.tansocore.model.signup.request.AgentSignupRequest;
import com.tansoflow.tansocore.repository.AccountRepository;
import com.tansoflow.tansocore.repository.AccountSettingRepository;
import com.tansoflow.tansocore.repository.CustomerRepository;
import com.tansoflow.tansocore.repository.PlanRepository;
import com.tansoflow.tansocore.service.internal.account.CustomerApiKeyService;
import com.tansoflow.tansocore.service.internal.account.CustomerService;
import com.tansoflow.tansocore.service.internal.monetization.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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

    @InjectMocks
    private AgentSignupServiceImpl service;

    private final UUID accountId = UUID.randomUUID();
    private final UUID planId = UUID.randomUUID();
    private Account account;
    private AccountSetting settings;
    private Plan plan;

    @BeforeEach
    void setUp() {
        account = new Account();
        account.setId(accountId);
        account.setSlug("acme");

        settings = new AccountSetting();
        settings.setAccounts(account);
        settings.setAgentSignupEnabled(true);
        settings.setAgentSignupDefaultPlanId(planId);
        settings.setAgentSignupHourlyCap(2);

        plan = new Plan();
        plan.setId(planId);
        plan.setKey("free");

        lenient().when(accountRepository.findBySlug("acme")).thenReturn(Optional.of(account));
        lenient().when(accountSettingRepository.findAccountSettingById(accountId)).thenReturn(settings);
        lenient().when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        lenient().when(customerRepository.countAgentSignupsSince(eq(accountId), any())).thenReturn(0L);
        lenient().when(customerService.createCustomer(eq(accountId.toString()), any(CustomerRequest.class)))
                .thenAnswer(inv -> {
                    Customer customer = new Customer();
                    customer.setId(UUID.randomUUID());
                    customer.setAccount(account);
                    customer.setExternalClientCustomerId(
                            ((CustomerRequest) inv.getArgument(1)).getCustomerReferenceId());
                    return customer;
                });
        lenient().when(customerApiKeyService.createKey(eq(accountId.toString()), anyString(), any()))
                .thenAnswer(inv -> CustomerApiKeyDto.builder()
                        .apiKey("ck_test_generated")
                        .scopes(List.of("read", "purchase"))
                        .customerReferenceId(inv.getArgument(1))
                        .build());
    }

    private AgentSignupRequest request() {
        AgentSignupRequest request = new AgentSignupRequest();
        request.setEmail("agent-owner@example.com");
        return request;
    }

    @Test
    void signupCreatesCustomerSubscribesAndIssuesKey() {
        AgentSignupResponse response = service.signup("acme", request(), "https://billing.acme.ai");

        assertThat(response.getCustomerReferenceId()).startsWith("agent_");
        assertThat(response.getApiKey()).isEqualTo("ck_test_generated");
        assertThat(response.getPlan()).isEqualTo("free");
        assertThat(response.getNextSteps()).containsKey("check_entitlement");

        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(subscriptionService).subscribe(customerCaptor.capture(), eq(plan), eq(accountId.toString()));
        assertThat(customerCaptor.getValue().getExternalClientCustomerId())
                .isEqualTo(response.getCustomerReferenceId());
    }

    @Test
    void disabledSignupAndUnknownSlugAreIndistinguishable() {
        when(accountRepository.findBySlug("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.signup("missing", request(), "http://x"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("No signup at this address");

        settings.setAgentSignupEnabled(false);
        assertThatThrownBy(() -> service.signup("acme", request(), "http://x"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("No signup at this address");

        settings.setAgentSignupEnabled(true);
        settings.setAgentSignupDefaultPlanId(null);
        assertThatThrownBy(() -> service.signup("acme", request(), "http://x"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("No signup at this address");
    }

    @Test
    void hourlyCapReturns429WithRetryAfter() {
        when(customerRepository.countAgentSignupsSince(eq(accountId), any())).thenReturn(2L);
        assertThatThrownBy(() -> service.signup("acme", request(), "http://x"))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(e -> assertThat(((RateLimitExceededException) e).getRetryAfterSeconds()).isEqualTo(3600));
    }
}
