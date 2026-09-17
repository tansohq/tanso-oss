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

import com.stripe.exception.StripeException;
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
import com.tansoflow.tansocore.repository.AccountRepository;
import com.tansoflow.tansocore.repository.AccountSettingRepository;
import com.tansoflow.tansocore.repository.CheckoutSessionRepository;
import com.tansoflow.tansocore.repository.CustomerRepository;
import com.tansoflow.tansocore.repository.PlanRepository;
import com.tansoflow.tansocore.service.client.AgentSignupService;
import com.tansoflow.tansocore.service.client.ClientEntitlementService;
import com.tansoflow.tansocore.service.internal.account.CustomerApiKeyService;
import com.tansoflow.tansocore.service.internal.account.CustomerService;
import com.tansoflow.tansocore.service.internal.monetization.FeatureService;
import com.tansoflow.tansocore.service.internal.monetization.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    private final FeatureService featureService;
    private final ClientEntitlementService clientEntitlementService;
    private final StripePaymentMethodService stripePaymentMethodService;
    private final CheckoutSessionRepository checkoutSessionRepository;

    @Override
    @Transactional
    public AgentSignupResponse signup(String slug, AgentSignupRequest request, String baseUrl, String clientIp) {
        Account account = accountRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("No signup at this address"));
        AccountSetting settings = accountSettingRepository.findAccountSettingById(account.getId());
        if (settings == null || !settings.isAgentSignupEnabled()
                || settings.getAgentSignupDefaultPlanId() == null) {
            // Same 404 as an unknown slug — do not confirm the account exists
            throw new ResourceNotFoundException("No signup at this address");
        }

        Instant hourAgo = Instant.now().minus(Duration.ofHours(1));
        if (customerRepository.countAgentSignupsSince(account.getId(), hourAgo) >= settings.getAgentSignupHourlyCap()) {
            throw new RateLimitExceededException(
                    "Signup rate limit reached for this catalog; retry after Retry-After seconds", 3600);
        }
        if (clientIp != null
                && customerRepository.countAgentSignupsFromIpSince(clientIp, hourAgo) >= settings.getAgentSignupPerIpCap()) {
            throw new RateLimitExceededException(
                    "Signup rate limit reached for this address; retry after Retry-After seconds", 3600);
        }

        Plan plan = planRepository.findById(settings.getAgentSignupDefaultPlanId())
                .orElseThrow(() -> new ResourceNotFoundException("No signup at this address"));

        String ownerEmail = request.getEmail() == null || request.getEmail().isBlank()
                ? null : request.getEmail().trim().toLowerCase(Locale.ROOT);

        // The owner email is unverified, so it never resolves to an existing customer: that would let
        // anyone who knows the address mint a key for someone else's account.
        Customer customer = createProvisionalCustomer(account, settings, request, ownerEmail, clientIp);
        subscriptionService.subscribe(customer, plan, account.getId().toString());
        String referenceId = customer.getExternalClientCustomerId();

        CustomerApiKeyDto key = customerApiKeyService.createKey(
                account.getId().toString(), referenceId, List.of("read", "purchase"));

        log.info("Agent signup on account {}: customer {} plan {}", account.getId(), referenceId, plan.getKey());

        List<FeatureDto> features = featureService.retrieveFeaturesLinkedToPlan(plan);
        String exampleFeatureKey = features.stream()
                .map(FeatureDto::getKey)
                .filter(k -> k != null && !k.isBlank())
                .findFirst()
                .orElse(null);

        String customerBase = baseUrl + "/api/v1/client/customers/" + referenceId;
        return AgentSignupResponse.builder()
                .customerReferenceId(referenceId)
                .apiKey(key.getApiKey())
                .apiKeyScopes(key.getScopes())
                .plan(plan.getKey())
                .status(customer.getAgentStatus().name().toLowerCase(Locale.ROOT))
                .expiresAt(customer.getAgentExpiresAt())
                .limits(limits(account, settings, plan, referenceId, features))
                .spendMandate(spendMandate(account, settings, customer, key, request.getSpendMandate()))
                .statusUrl(customerBase + "/status")
                .ownerUrl(customerBase + "/owner")
                .nextSteps(nextSteps(baseUrl, slug, referenceId, exampleFeatureKey))
                .build();
    }

    private Customer createProvisionalCustomer(Account account, AccountSetting settings, AgentSignupRequest request,
                                               String ownerEmail, String clientIp) {
        String referenceId = "agent_" + UUID.randomUUID().toString().replace("-", "");
        CustomerRequest customerRequest = new CustomerRequest();
        customerRequest.setCustomerReferenceId(referenceId);
        customerRequest.setEmail(ownerEmail);
        if (request.getName() != null && !request.getName().isBlank()) {
            customerRequest.setFirstName(request.getName().trim());
        }
        Customer customer = customerService.createCustomer(account.getId().toString(), customerRequest);
        customer.setAgentStatus(AgentStatus.PROVISIONAL);
        customer.setAgentExpiresAt(Instant.now().plus(Duration.ofDays(settings.getAgentProvisionalDays())));
        customer.setAgentOwnerEmail(ownerEmail);
        customer.setAgentSignupIp(clientIp);
        return customerRepository.save(customer);
    }

    private AgentSignupResponse.AgentLimits limits(Account account, AccountSetting settings, Plan plan,
                                                    String referenceId, List<FeatureDto> features) {
        String period = plan.getIntervalMonths() == null || plan.getIntervalMonths() == 1
                ? "month" : plan.getIntervalMonths() + " months";
        Map<String, AgentSignupResponse.FeatureLimit> byFeature = new LinkedHashMap<>();
        for (FeatureDto feature : features) {
            EntitlementResponse entitlement = clientEntitlementService.checkEntitlement(
                    referenceId, account.getId().toString(), feature.getKey(), false);
            EntitlementResponse.Usage usage = entitlement.getUsage();
            boolean unlimited = usage == null || usage.getLimit() == null || Boolean.TRUE.equals(usage.getUnlimited());
            byFeature.put(feature.getKey(), AgentSignupResponse.FeatureLimit.builder()
                    .included(unlimited ? null : usage.getLimit())
                    .period(period)
                    .unlimited(unlimited)
                    .build());
        }
        return AgentSignupResponse.AgentLimits.builder()
                .features(byFeature)
                .spendCap(settings.getAgentMaxTopupAmount())
                .currency(settings.getCurrency())
                .build();
    }

    private AgentSignupResponse.AgentSpendMandate spendMandate(Account account, AccountSetting settings,
                                                               Customer customer, CustomerApiKeyDto key,
                                                               AgentSignupRequest.SpendMandate requested) {
        if (requested == null) {
            return null;
        }
        String period = requested.getPeriod() == null ? "month" : requested.getPeriod().toLowerCase(Locale.ROOT);
        String currency = requested.getCurrency() == null
                ? settings.getCurrency() : requested.getCurrency().toUpperCase(Locale.ROOT);
        if (!currency.equalsIgnoreCase(settings.getCurrency())) {
            throw new IllegalArgumentException("spend_mandate.currency must be " + settings.getCurrency()
                    + ", the account currency");
        }
        AgentSignupResponse.AgentSpendMandate.AgentSpendMandateBuilder mandate = AgentSignupResponse.AgentSpendMandate.builder()
                .maxAmount(requested.getMaxAmount())
                .currency(currency)
                .period(period);
        if (!settings.isAgentSpendMandateEnabled() || !settings.isStripeEnabled()) {
            log.info("Spend mandate requested on account {} but unavailable (enabled={}, stripe={})",
                    account.getId(), settings.isAgentSpendMandateEnabled(), settings.isStripeEnabled());
            return mandate.status("unavailable").build();
        }

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("tanso_account_id", account.getId().toString());
        metadata.put("tanso_customer_id", customer.getId().toString());
        metadata.put("tanso_purpose", CheckoutSession.PURPOSE_SPEND_MANDATE);
        metadata.put("tanso_period", period);
        StripePaymentMethodService.HostedCheckout hosted;
        try {
            hosted = stripePaymentMethodService.createSetupCheckoutSession(account.getId(), customer.getId(), metadata);
        } catch (StripeException e) {
            // The mandate is optional; the account and key are still good. The agent can retry via the status endpoint later.
            log.error("Stripe refused the spend mandate session for agent customer {} on account {}: {}",
                    customer.getExternalClientCustomerId(), account.getId(), e.getMessage(), e);
            return mandate.status("unavailable").build();
        }

        CheckoutSession session = new CheckoutSession();
        session.setAccountId(account.getId());
        session.setCustomerId(customer.getId());
        session.setPurpose(CheckoutSession.PURPOSE_SPEND_MANDATE);
        session.setStripeSessionId(hosted.stripeSessionId());
        session.setCheckoutUrl(hosted.url());
        session.setApiKeyId(UUID.fromString(key.getId()));
        session.setAmount(requested.getMaxAmount());
        checkoutSessionRepository.save(session);

        return mandate.status("pending").setupUrl(hosted.url()).build();
    }

    private Map<String, String> nextSteps(String baseUrl, String slug, String referenceId, String exampleFeatureKey) {
        Map<String, String> nextSteps = new LinkedHashMap<>();
        nextSteps.put("base_url", baseUrl);
        nextSteps.put("pricing", baseUrl + "/public/v1/catalog/" + slug + "/pricing.json");
        nextSteps.put("check_entitlement_template", baseUrl + "/api/v1/client/entitlements/" + referenceId + "/{featureKey}");
        if (exampleFeatureKey != null) {
            nextSteps.put("check_entitlement_example", baseUrl + "/api/v1/client/entitlements/" + referenceId + "/" + exampleFeatureKey);
        }
        nextSteps.put("record_usage", baseUrl + "/api/v1/client/events");
        nextSteps.put("usage_summary", baseUrl + "/api/v1/client/customers/" + referenceId + "/usage");
        nextSteps.put("credit_balances", baseUrl + "/api/v1/client/credits/" + referenceId + "/pools");
        nextSteps.put("buy_credits", baseUrl + "/api/v1/client/credits/purchases");
        nextSteps.put("change_plan", baseUrl + "/api/v1/client/subscriptions");
        nextSteps.put("runbook", baseUrl + "/agent-signup.md");
        nextSteps.put("docs", baseUrl + "/swagger-ui.html");
        return nextSteps;
    }
}
