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

import com.tansoflow.tansocore.entity.AgentStatus;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.model.apikey.CustomerApiKeyDto;
import com.tansoflow.tansocore.model.apikey.request.UpdateKeyBudgetRequest;
import com.tansoflow.tansocore.model.apikey.type.BudgetPeriod;
import com.tansoflow.tansocore.repository.CustomerRepository;
import com.tansoflow.tansocore.service.client.AgentLifecycleService;
import com.tansoflow.tansocore.service.internal.account.CustomerApiKeyService;
import com.tansoflow.tansocore.service.internal.account.KeyBudgetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AgentLifecycleServiceImpl implements AgentLifecycleService {

    private final CustomerRepository customerRepository;
    private final CustomerApiKeyService customerApiKeyService;
    private final KeyBudgetService keyBudgetService;

    @Override
    @Transactional
    public void claimOnPayment(Customer customer) {
        if (customer.getAgentStatus() != AgentStatus.PROVISIONAL) {
            return;
        }
        customer.setAgentStatus(AgentStatus.CLAIMED);
        customer.setAgentClaimedAt(Instant.now());
        customer.setAgentExpiresAt(null);
        customerRepository.save(customer);
        log.info("Agent customer {} claimed by payment", customer.getExternalClientCustomerId());
    }

    @Override
    @Transactional
    public void claimOnPayment(UUID customerId, UUID accountId) {
        if (customerId == null) {
            return;
        }
        customerRepository.getCustomerById(customerId)
                .filter(c -> c.getAccount().getId().equals(accountId))
                .ifPresent(this::claimOnPayment);
    }

    @Override
    @Transactional
    public void setOwnerEmail(Customer customer, String email) {
        customer.setAgentOwnerEmail(email.trim().toLowerCase(Locale.ROOT));
        if (customer.getEmail() == null) {
            customer.setEmail(customer.getAgentOwnerEmail());
        }
        customerRepository.save(customer);
    }

    @Override
    @Transactional
    public void activateSpendMandate(UUID accountId, UUID customerId, UUID apiKeyId, String paymentMethodId,
                                     BigDecimal maxAmount, String period) {
        Customer customer = customerRepository.getCustomerById(customerId)
                .filter(c -> c.getAccount().getId().equals(accountId))
                .orElseThrow(() -> new IllegalStateException("Spend mandate for unknown customer " + customerId));

        UpdateKeyBudgetRequest budget = new UpdateKeyBudgetRequest();
        budget.setPeriod(BudgetPeriod.valueOf(period.toUpperCase(Locale.ROOT)));
        budget.setAmountLimit(maxAmount);
        keyBudgetService.setBudget(accountId.toString(), customer.getExternalClientCustomerId(),
                apiKeyId.toString(), budget);

        customer.setStripeDefaultPaymentMethodId(paymentMethodId);
        customerRepository.save(customer);
        claimOnPayment(customer);
        log.info("Spend mandate active for agent customer {}: {} per {} on key {}",
                customer.getExternalClientCustomerId(), maxAmount, period, apiKeyId);
    }

    @Override
    @Transactional
    public int expireProvisional(Instant now) {
        List<Customer> expired = customerRepository.findExpiredProvisionalAgentCustomers(now);
        for (Customer customer : expired) {
            String accountId = customer.getAccount().getId().toString();
            String reference = customer.getExternalClientCustomerId();
            for (CustomerApiKeyDto key : customerApiKeyService.listKeys(accountId, reference)) {
                if (Boolean.TRUE.equals(key.getActive())) {
                    customerApiKeyService.revokeKey(accountId, reference, key.getId());
                }
            }
            customer.setAgentStatus(AgentStatus.EXPIRED);
            customerRepository.save(customer);
        }
        return expired.size();
    }
}
