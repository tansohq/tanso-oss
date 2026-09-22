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
import com.tansoflow.tansocore.model.apikey.type.BudgetPeriod;
import com.tansoflow.tansocore.repository.CustomerRepository;
import com.tansoflow.tansocore.service.internal.account.CustomerApiKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    private TransactionTemplate transactionTemplate;
    private com.tansoflow.tansocore.integration.stripe.StripeSyncService stripeSyncService;
    private AgentLifecycleServiceImpl service;

    private final UUID accountId = UUID.randomUUID();
    private Customer customer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        transactionTemplate = mock(TransactionTemplate.class);
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv ->
                ((TransactionCallback<Object>) inv.getArgument(0)).doInTransaction(new SimpleTransactionStatus()));
        stripeSyncService = mock(com.tansoflow.tansocore.integration.stripe.StripeSyncService.class);
        service = new AgentLifecycleServiceImpl(customerRepository, customerApiKeyService, transactionTemplate,
                stripeSyncService);

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
    void spendMandateIsStoredOnTheCustomerAndLeavesKeysAlone() {
        UUID keyId = UUID.randomUUID();
        when(customerRepository.getCustomerById(customer.getId())).thenReturn(Optional.of(customer));

        service.activateSpendMandate(accountId, customer.getId(), keyId, "pm_123", new BigDecimal("40"), "month");

        assertThat(customer.getMandateAmount()).isEqualByComparingTo("40");
        assertThat(customer.getMandatePeriod()).isEqualTo(BudgetPeriod.MONTH);
        assertThat(customer.getMandateStartedAt()).isNotNull();
        assertThat(customer.getStripeDefaultPaymentMethodId()).isEqualTo("pm_123");
        assertThat(customer.getAgentStatus()).isEqualTo(AgentStatus.CLAIMED);
        // Keys, and any budget an operator set on them, are never read or written.
        verify(customerApiKeyService, never()).listKeys(any(), any());
    }

    @Test
    void aRaisedMandateReplacesTheAmountButKeepsTheRunningWindow() {
        Instant started = Instant.now().minusSeconds(86_400);
        customer.setMandateAmount(new BigDecimal("40"));
        customer.setMandatePeriod(BudgetPeriod.MONTH);
        customer.setMandateStartedAt(started);
        when(customerRepository.getCustomerById(customer.getId())).thenReturn(Optional.of(customer));

        service.activateSpendMandate(accountId, customer.getId(), UUID.randomUUID(), "pm_456", new BigDecimal("90"), "month");

        assertThat(customer.getMandateAmount()).isEqualByComparingTo("90");
        assertThat(customer.getMandateStartedAt()).isEqualTo(started);

        service.activateSpendMandate(accountId, customer.getId(), UUID.randomUUID(), "pm_456", new BigDecimal("20"), "week");

        assertThat(customer.getMandatePeriod()).isEqualTo(BudgetPeriod.WEEK);
        assertThat(customer.getMandateStartedAt()).isAfter(started);
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

    @Test
    void ownerEmailFillsABlankEmailAndTellsStripe() throws Exception {
        customer.setEmail("  ");
        service.setOwnerEmail(customer, "Owner@Example.com");
        assertThat(customer.getEmail()).isEqualTo("owner@example.com");
        verify(stripeSyncService).syncCustomerEmail(accountId, customer.getId(), "owner@example.com");
    }

    @Test
    void ownerEmailLeavesAnExistingEmailAndSkipsStripe() throws Exception {
        customer.setEmail("billing@example.com");
        service.setOwnerEmail(customer, "owner@example.com");
        assertThat(customer.getEmail()).isEqualTo("billing@example.com");
        assertThat(customer.getAgentOwnerEmail()).isEqualTo("owner@example.com");
        verify(stripeSyncService, never()).syncCustomerEmail(any(), any(), any());
    }
}
