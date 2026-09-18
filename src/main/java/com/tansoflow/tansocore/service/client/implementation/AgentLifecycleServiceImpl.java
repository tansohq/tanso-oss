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
import org.springframework.transaction.support.TransactionTemplate;

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
    private final TransactionTemplate transactionTemplate;
    private final com.tansoflow.tansocore.integration.stripe.StripeSyncService stripeSyncService;

    @Override
    @Transactional
    public void claimOnPayment(Customer customer) {
        if (customer.getAgentStatus() == null || customer.getAgentStatus() == AgentStatus.CLAIMED) {
            return;
        }
        if (customer.getAgentStatus() == AgentStatus.EXPIRED) {
            log.warn("Payment arrived for expired agent customer {}; re-claiming, its keys stay revoked",
                    customer.getExternalClientCustomerId());
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
        // Called after money moved inside a webhook transaction: a bad reference is logged, not thrown,
        // so the grant that just landed is not rolled back.
        Customer customer = customerId == null ? null : customerRepository.getCustomerById(customerId).orElse(null);
        if (customer == null) {
            log.warn("Paid checkout references unknown customer {} on account {}; nothing to claim", customerId, accountId);
            return;
        }
        if (!customer.getAccount().getId().equals(accountId)) {
            log.warn("Paid checkout for customer {} names account {} but the customer belongs to {}; nothing to claim",
                    customerId, accountId, customer.getAccount().getId());
            return;
        }
        claimOnPayment(customer);
    }

    @Override
    @Transactional
    public void setOwnerEmail(Customer customer, String email) {
        customer.setAgentOwnerEmail(email.trim().toLowerCase(Locale.ROOT));
        boolean emailWasMissing = customer.getEmail() == null || customer.getEmail().isBlank();
        if (emailWasMissing) {
            customer.setEmail(customer.getAgentOwnerEmail());
        }
        customerRepository.save(customer);
        if (!emailWasMissing) {
            return;
        }
        // Stripe refuses to send an invoice to a customer without an email, so the mirror has to learn it too.
        // Only called when the email actually changed, so a Stripe outage does not block re-nominating an owner.
        try {
            stripeSyncService.syncCustomerEmail(customer.getAccount().getId(), customer.getId(), customer.getEmail());
        } catch (com.stripe.exception.StripeException e) {
            throw new IllegalStateException("Stripe rejected the owner email for customer "
                    + customer.getExternalClientCustomerId() + ": " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void activateSpendMandate(UUID accountId, UUID customerId, UUID apiKeyId, String paymentMethodId,
                                     BigDecimal maxAmount, String period) {
        Customer customer = customerRepository.getCustomerById(customerId)
                .filter(c -> c.getAccount().getId().equals(accountId))
                .orElseThrow(() -> new IllegalStateException("Spend mandate for unknown customer " + customerId));
        String reference = customer.getExternalClientCustomerId();

        // The principal approved a ceiling for the customer, so every live key gets it, not only the one
        // that asked. A key issued later inherits it through rotation or the operator's console.
        UpdateKeyBudgetRequest budget = new UpdateKeyBudgetRequest();
        budget.setPeriod(BudgetPeriod.valueOf(period.toUpperCase(Locale.ROOT)));
        budget.setAmountLimit(maxAmount);
        int capped = 0;
        for (CustomerApiKeyDto key : customerApiKeyService.listKeys(accountId.toString(), reference)) {
            if (Boolean.TRUE.equals(key.getActive())) {
                keyBudgetService.setBudget(accountId.toString(), reference, key.getId(), budget);
                capped++;
            }
        }

        customer.setStripeDefaultPaymentMethodId(paymentMethodId);
        customerRepository.save(customer);
        claimOnPayment(customer);
        log.info("Spend mandate active for agent customer {}: {} per {} on {} key(s), requested by key {}",
                reference, maxAmount, period, capped, apiKeyId);
    }

    @Override
    public int expireProvisional(Instant now) {
        List<Customer> candidates = customerRepository.findExpiredProvisionalAgentCustomers(now);
        int expired = 0;
        for (Customer candidate : candidates) {
            // One transaction per customer, and the flip is conditional, so a payment that lands between the
            // query and this write wins and keeps its keys.
            Boolean flipped = transactionTemplate.execute(status -> {
                if (customerRepository.expireIfStillProvisional(candidate.getId(), now) == 0) {
                    return false;
                }
                String accountId = candidate.getAccount().getId().toString();
                String reference = candidate.getExternalClientCustomerId();
                for (CustomerApiKeyDto key : customerApiKeyService.listKeys(accountId, reference)) {
                    if (Boolean.TRUE.equals(key.getActive())) {
                        customerApiKeyService.revokeKey(accountId, reference, key.getId());
                    }
                }
                return true;
            });
            if (Boolean.TRUE.equals(flipped)) {
                expired++;
            }
        }
        return expired;
    }
}
