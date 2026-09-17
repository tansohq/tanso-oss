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
import com.tansoflow.tansocore.entity.AgentStatus;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.model.apikey.CustomerApiKeyDto;
import com.tansoflow.tansocore.model.apikey.request.UpdateKeyBudgetRequest;
import com.tansoflow.tansocore.model.apikey.type.BudgetPeriod;
import com.tansoflow.tansocore.repository.CustomerRepository;
import com.tansoflow.tansocore.service.internal.account.CustomerApiKeyService;
import com.tansoflow.tansocore.service.internal.account.KeyBudgetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentLifecycleServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private CustomerApiKeyService customerApiKeyService;
    @Mock
    private KeyBudgetService keyBudgetService;

    @InjectMocks
    private AgentLifecycleServiceImpl service;

    private final UUID accountId = UUID.randomUUID();
    private Customer customer;

    @BeforeEach
    void setUp() {
        Account account = new Account();
        account.setId(accountId);
        customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setAccount(account);
        customer.setExternalClientCustomerId("agent_abc");
        customer.setAgentStatus(AgentStatus.PROVISIONAL);
        customer.setAgentExpiresAt(Instant.now().plusSeconds(3600));
    }

    @Test
    void paymentClaimsAProvisionalCustomerOnce() {
        service.claimOnPayment(customer);

        assertThat(customer.getAgentStatus()).isEqualTo(AgentStatus.CLAIMED);
        assertThat(customer.getAgentClaimedAt()).isNotNull();
        assertThat(customer.getAgentExpiresAt()).isNull();
        verify(customerRepository).save(customer);

        Instant firstClaim = customer.getAgentClaimedAt();
        service.claimOnPayment(customer);
        assertThat(customer.getAgentClaimedAt()).isEqualTo(firstClaim);
    }

    @Test
    void humanCreatedCustomersAreLeftAlone() {
        customer.setAgentStatus(null);
        service.claimOnPayment(customer);
        verify(customerRepository, never()).save(any());
    }

    @Test
    void claimByIdIgnoresOtherAccountsAndNulls() {
        service.claimOnPayment(null, accountId);
        when(customerRepository.getCustomerById(customer.getId())).thenReturn(Optional.of(customer));

        service.claimOnPayment(customer.getId(), UUID.randomUUID());
        assertThat(customer.getAgentStatus()).isEqualTo(AgentStatus.PROVISIONAL);

        service.claimOnPayment(customer.getId(), accountId);
        assertThat(customer.getAgentStatus()).isEqualTo(AgentStatus.CLAIMED);
    }

    @Test
    void spendMandateCapsTheKeyStoresTheCardAndClaims() {
        UUID keyId = UUID.randomUUID();
        when(customerRepository.getCustomerById(customer.getId())).thenReturn(Optional.of(customer));

        service.activateSpendMandate(accountId, customer.getId(), keyId, "pm_123", new BigDecimal("40"), "month");

        ArgumentCaptor<UpdateKeyBudgetRequest> budget = ArgumentCaptor.forClass(UpdateKeyBudgetRequest.class);
        verify(keyBudgetService).setBudget(eq(accountId.toString()), eq("agent_abc"), eq(keyId.toString()), budget.capture());
        assertThat(budget.getValue().getPeriod()).isEqualTo(BudgetPeriod.MONTH);
        assertThat(budget.getValue().getAmountLimit()).isEqualByComparingTo("40");
        assertThat(customer.getStripeDefaultPaymentMethodId()).isEqualTo("pm_123");
        assertThat(customer.getAgentStatus()).isEqualTo(AgentStatus.CLAIMED);
    }

    @Test
    void expiryRevokesActiveKeysAndMarksExpired() {
        when(customerRepository.findExpiredProvisionalAgentCustomers(any())).thenReturn(List.of(customer));
        when(customerApiKeyService.listKeys(accountId.toString(), "agent_abc")).thenReturn(List.of(
                CustomerApiKeyDto.builder().id("k1").active(true).build(),
                CustomerApiKeyDto.builder().id("k2").active(false).build()));

        int expired = service.expireProvisional(Instant.now());

        assertThat(expired).isEqualTo(1);
        verify(customerApiKeyService).revokeKey(accountId.toString(), "agent_abc", "k1");
        verify(customerApiKeyService, never()).revokeKey(accountId.toString(), "agent_abc", "k2");
        assertThat(customer.getAgentStatus()).isEqualTo(AgentStatus.EXPIRED);
        verify(customerRepository).save(customer);
    }
}
