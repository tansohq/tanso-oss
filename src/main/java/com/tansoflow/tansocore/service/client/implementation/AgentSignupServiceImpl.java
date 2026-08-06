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
import com.tansoflow.tansocore.service.client.AgentSignupService;
import com.tansoflow.tansocore.service.internal.account.CustomerApiKeyService;
import com.tansoflow.tansocore.service.internal.account.CustomerService;
import com.tansoflow.tansocore.service.internal.monetization.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AgentSignupServiceImpl implements AgentSignupService {

    private final AccountRepository accountRepository;
    private final AccountSettingRepository accountSettingRepository;
    private final PlanRepository planRepository;
    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final SubscriptionService subscriptionService;
    private final CustomerApiKeyService customerApiKeyService;

    @Override
    @Transactional
    public AgentSignupResponse signup(String slug, AgentSignupRequest request, String baseUrl) {
        Account account = accountRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("No signup at this address"));
        AccountSetting settings = accountSettingRepository.findAccountSettingById(account.getId());
        if (settings == null || !settings.isAgentSignupEnabled()
                || settings.getAgentSignupDefaultPlanId() == null) {
            // Same 404 as an unknown slug — do not confirm the account exists
            throw new ResourceNotFoundException("No signup at this address");
        }

        long recentSignups = customerRepository.countAgentSignupsSince(
                account.getId(), Instant.now().minus(Duration.ofHours(1)));
        if (recentSignups >= settings.getAgentSignupHourlyCap()) {
            throw new RateLimitExceededException(
                    "Signup rate limit reached for this catalog — retry later", 3600);
        }

        Plan plan = planRepository.findById(settings.getAgentSignupDefaultPlanId())
                .orElseThrow(() -> new ResourceNotFoundException("No signup at this address"));

        String referenceId = "agent_" + UUID.randomUUID().toString().replace("-", "");
        CustomerRequest customerRequest = new CustomerRequest();
        customerRequest.setCustomerReferenceId(referenceId);
        customerRequest.setEmail(request.getEmail());
        if (request.getName() != null && !request.getName().isBlank()) {
            customerRequest.setFirstName(request.getName().trim());
        }
        Customer customer = customerService.createCustomer(account.getId().toString(), customerRequest);

        subscriptionService.subscribe(customer, plan, account.getId().toString());

        CustomerApiKeyDto key = customerApiKeyService.createKey(
                account.getId().toString(), referenceId, List.of("read", "purchase"));

        log.info("Agent signup on account {}: customer {} subscribed to plan {}",
                account.getId(), referenceId, plan.getKey());

        return AgentSignupResponse.builder()
                .customerReferenceId(referenceId)
                .apiKey(key.getApiKey())
                .apiKeyScopes(key.getScopes())
                .plan(plan.getKey())
                .nextSteps(Map.of(
                        "base_url", baseUrl,
                        "check_entitlement", baseUrl + "/api/v1/client/entitlements/" + referenceId + "/{featureKey}",
                        "record_usage", baseUrl + "/api/v1/client/events",
                        "credit_balances", baseUrl + "/api/v1/client/credits/" + referenceId + "/pools",
                        "docs", baseUrl + "/swagger-ui.html"))
                .build();
    }
}
