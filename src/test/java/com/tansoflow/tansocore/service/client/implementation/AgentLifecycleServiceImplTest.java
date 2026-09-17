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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
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

    private TransactionTemplate transactionTemplate;
    private AgentLifecycleServiceImpl service;

    private final UUID accountId = UUID.randomUUID();
    private Customer customer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        transactionTemplate = mock(TransactionTemplate.class);
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv ->
                ((TransactionCallback<Object>) inv.getArgument(0)).doInTransaction(new SimpleTransactionStatus()));
        service = new AgentLifecycleServiceImpl(customerRepository, customerApiKeyService, keyBudgetService, transactionTemplate);

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
    void paymentAfterExpiryReclaims() {
        customer.setAgentStatus(AgentStatus.EXPIRED);
        service.claimOnPayment(customer);
        assertThat(customer.getAgentStatus()).isEqualTo(AgentStatus.CLAIMED);
    }

    @Test
    void humanCreatedCustomersAreLeftAlone() {
        customer.setAgentStatus(null);
        service.claimOnPayment(customer);
        verify(customerRepository, never()).save(any());
    }

    @Test
    void claimByIdIgnoresUnknownCustomersAndOtherAccounts() {
        UUID unknown = UUID.randomUUID();
        when(customerRepository.getCustomerById(unknown)).thenReturn(Optional.empty());
        service.claimOnPayment(unknown, accountId);
        service.claimOnPayment(null, accountId);

        when(customerRepository.getCustomerById(customer.getId())).thenReturn(Optional.of(customer));
        service.claimOnPayment(customer.getId(), UUID.randomUUID());
        assertThat(customer.getAgentStatus()).isEqualTo(AgentStatus.PROVISIONAL);

        service.claimOnPayment(customer.getId(), accountId);
        assertThat(customer.getAgentStatus()).isEqualTo(AgentStatus.CLAIMED);
    }

    @Test
    void spendMandateCapsEveryActiveKeyStoresTheCardAndClaims() {
        UUID keyId = UUID.randomUUID();
        when(customerRepository.getCustomerById(customer.getId())).thenReturn(Optional.of(customer));
        when(customerApiKeyService.listKeys(accountId.toString(), "agent_abc")).thenReturn(List.of(
                CustomerApiKeyDto.builder().id(keyId.toString()).active(true).build(),
                CustomerApiKeyDto.builder().id("k2").active(true).build(),
                CustomerApiKeyDto.builder().id("k3").active(false).build()));

        service.activateSpendMandate(accountId, customer.getId(), keyId, "pm_123", new BigDecimal("40"), "month");

        ArgumentCaptor<UpdateKeyBudgetRequest> budget = ArgumentCaptor.forClass(UpdateKeyBudgetRequest.class);
        verify(keyBudgetService).setBudget(eq(accountId.toString()), eq("agent_abc"), eq(keyId.toString()), budget.capture());
        verify(keyBudgetService).setBudget(eq(accountId.toString()), eq("agent_abc"), eq("k2"), any());
        verify(keyBudgetService, never()).setBudget(eq(accountId.toString()), eq("agent_abc"), eq("k3"), any());
        assertThat(budget.getValue().getPeriod()).isEqualTo(BudgetPeriod.MONTH);
        assertThat(budget.getValue().getAmountLimit()).isEqualByComparingTo("40");
        assertThat(customer.getStripeDefaultPaymentMethodId()).isEqualTo("pm_123");
        assertThat(customer.getAgentStatus()).isEqualTo(AgentStatus.CLAIMED);
    }

    @Test
    void expiryRevokesActiveKeysOnlyWhenTheFlipWins() {
        Instant now = Instant.now();
        when(customerRepository.findExpiredProvisionalAgentCustomers(now)).thenReturn(List.of(customer));
        when(customerRepository.expireIfStillProvisional(customer.getId(), now)).thenReturn(1);
        when(customerApiKeyService.listKeys(accountId.toString(), "agent_abc")).thenReturn(List.of(
                CustomerApiKeyDto.builder().id("k1").active(true).build(),
                CustomerApiKeyDto.builder().id("k2").active(false).build()));

        assertThat(service.expireProvisional(now)).isEqualTo(1);
        verify(customerApiKeyService).revokeKey(accountId.toString(), "agent_abc", "k1");
        verify(customerApiKeyService, never()).revokeKey(accountId.toString(), "agent_abc", "k2");
    }

    @Test
    void expiryLeavesACustomerThatPaidInBetween() {
        Instant now = Instant.now();
        when(customerRepository.findExpiredProvisionalAgentCustomers(now)).thenReturn(List.of(customer));
        when(customerRepository.expireIfStillProvisional(customer.getId(), now)).thenReturn(0);

        assertThat(service.expireProvisional(now)).isEqualTo(0);
        verify(customerApiKeyService, never()).listKeys(any(), any());
    }
}
