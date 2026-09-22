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
package com.tansoflow.tansocore.controller.client;

import com.tansoflow.tansocore.auth.CustomerAccessGuard;
import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.entity.AccountSetting;
import com.tansoflow.tansocore.entity.AgentStatus;
import com.tansoflow.tansocore.entity.CheckoutSession;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.model.apikey.KeyBudgetDto;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.model.signup.AgentSignupResponse;
import com.tansoflow.tansocore.model.signup.AgentStatusResponse;
import com.tansoflow.tansocore.model.signup.request.AgentSignupRequest;
import com.tansoflow.tansocore.model.usage.CustomerUsageResponse;
import com.tansoflow.tansocore.repository.AccountSettingRepository;
import com.tansoflow.tansocore.repository.CheckoutSessionRepository;
import com.tansoflow.tansocore.service.client.AgentLifecycleService;
import com.tansoflow.tansocore.service.client.AgentSignupService;
import com.tansoflow.tansocore.service.client.UsageForecastService;
import com.tansoflow.tansocore.service.internal.account.CustomerService;
import com.tansoflow.tansocore.service.internal.account.KeyBudgetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/client/customers/{customerReferenceId}")
@PreAuthorize("hasRole('CLIENT')")
@Tag(name = "Agent account", description = "Where an agent-created account stands: provisional, claimed, expired")
@ConditionalOnProperty(name = "app.modules.monetization.enabled", havingValue = "true", matchIfMissing = true)
public class CustomerStatusClientController {

    private final CustomerAccessGuard customerAccessGuard;
    private final CustomerService customerService;
    private final UsageForecastService usageForecastService;
    private final KeyBudgetService keyBudgetService;
    private final AccountSettingRepository accountSettingRepository;
    private final CheckoutSessionRepository checkoutSessionRepository;
    private final com.tansoflow.tansocore.repository.SubscriptionRepository subscriptionRepository;
    private final AgentLifecycleService agentLifecycleService;
    private final AgentSignupService agentSignupService;

    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('CLIENT','CUSTOMER')")
    @Operation(summary = "Account status", description = "Status, expiry, plan limits, remaining allowance, and the "
            + "calling key's spend budget. Poll this after a 402 or 403 to see what changed.",
            security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<AgentStatusResponse>> status(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String customerReferenceId) {
        customerReferenceId = customerAccessGuard.resolveCustomerRef(userContext, customerReferenceId);
        Customer customer = customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(
                customerReferenceId, userContext.getAccountId());
        return ResponseEntity.ok(ApiResponse.<AgentStatusResponse>builder()
                .data(build(customer, userContext)).success(true).build());
    }

    @PutMapping("/owner")
    @PreAuthorize("hasAnyRole('CLIENT','CUSTOMER')")
    @Operation(summary = "Nominate an owner", description = "Records the principal's email on the account. Tanso "
            + "sends nothing to it; Stripe may send receipts and invoices to it. Paying is what claims the account.",
            security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<AgentStatusResponse>> setOwner(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String customerReferenceId,
            @Valid @RequestBody OwnerRequest request) {
        customerReferenceId = customerAccessGuard.resolveCustomerRef(userContext, customerReferenceId);
        Customer customer = customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(
                customerReferenceId, userContext.getAccountId());
        agentLifecycleService.setOwnerEmail(customer, request.getEmail());
        return ResponseEntity.ok(ApiResponse.<AgentStatusResponse>builder()
                .data(build(customer, userContext)).success(true).build());
    }

    @PostMapping("/spend-mandate")
    @PreAuthorize("hasAnyRole('CLIENT','CUSTOMER')")
    @Operation(summary = "Ask for a new spend mandate", description = "Opens a Stripe page where the principal "
            + "saves a card and approves max_amount per period, for a first mandate after signup or a higher one. "
            + "Completing it replaces the customer's current mandate and claims the account. max_amount above the "
            + "operator's agentMaxMandateAmount is a 400.",
            security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<AgentSignupResponse.AgentSpendMandate>> requestSpendMandate(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable String customerReferenceId,
            @Valid @RequestBody AgentSignupRequest.SpendMandate request) {
        customerReferenceId = customerAccessGuard.resolveCustomerRef(userContext, customerReferenceId);
        customerAccessGuard.requirePurchaseScope(userContext);
        AgentSignupResponse.AgentSpendMandate mandate = agentSignupService.requestSpendMandate(
                userContext.getAccountId(), customerReferenceId, userContext.getApiKeyId(), request);
        return ResponseEntity.ok(ApiResponse.<AgentSignupResponse.AgentSpendMandate>builder()
                .data(mandate).success(true).build());
    }

    private AgentStatusResponse build(Customer customer, UserContext userContext) {
        AccountSetting settings = accountSettingRepository.findAccountSettingById(customer.getAccount().getId());
        CustomerUsageResponse usage = usageForecastService.getUsage(
                customer.getExternalClientCustomerId(), userContext.getAccountId());

        Map<String, String> periodByPlan = new LinkedHashMap<>();
        // The plan an agent cares about is the one it most recently got onto, so a paid upgrade wins over the free default.
        String plan = null;
        java.time.Instant newest = null;
        for (com.tansoflow.tansocore.entity.Subscription subscription : subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId())) {
            Integer months = subscription.getPlan().getIntervalMonths();
            periodByPlan.put(subscription.getPlan().getKey(), months == null || months == 1 ? "month" : months + " months");
            if (Boolean.TRUE.equals(subscription.getIsActive())
                    && (newest == null || subscription.getCreatedAt().isAfter(newest))) {
                newest = subscription.getCreatedAt();
                plan = subscription.getPlan().getKey();
            }
        }

        Map<String, AgentSignupResponse.FeatureLimit> limits = new LinkedHashMap<>();
        Map<String, BigDecimal> remaining = new LinkedHashMap<>();
        for (CustomerUsageResponse.SubscriptionUsage subscription : usage.getSubscriptions()) {
            if (plan == null) {
                plan = subscription.getPlanKey();
            }
            for (CustomerUsageResponse.FeatureUsage feature : subscription.getFeatures()) {
                boolean unlimited = feature.getLimit() == null;
                limits.put(feature.getFeatureKey(), AgentSignupResponse.FeatureLimit.builder()
                        .included(feature.getLimit())
                        .period(periodByPlan.getOrDefault(subscription.getPlanKey(), "month"))
                        .unlimited(unlimited)
                        .build());
                remaining.put(feature.getFeatureKey(), feature.getRemaining());
            }
        }

        AgentStatus status = customer.getAgentStatus() == null ? AgentStatus.CLAIMED : customer.getAgentStatus();
        return AgentStatusResponse.builder()
                .customerReferenceId(customer.getExternalClientCustomerId())
                .status(status.name().toLowerCase(Locale.ROOT))
                .expiresAt(customer.getAgentExpiresAt())
                .claimedAt(customer.getAgentClaimedAt())
                .plan(plan)
                .limits(AgentSignupResponse.AgentLimits.builder()
                        .features(limits)
                        .spendCap(settings.getAgentMaxTopupAmount())
                        .currency(settings.getCurrency())
                        .build())
                .remaining(remaining)
                .spend(spend(customer, userContext, settings))
                .ownerEmail(customer.getAgentOwnerEmail())
                .spendMandate(spendMandate(customer, settings))
                .build();
    }

    private AgentStatusResponse.Spend spend(Customer customer, UserContext userContext, AccountSetting settings) {
        UUID keyId = userContext.getApiKeyId();
        if (keyId == null) {
            return null;
        }
        KeyBudgetDto budget = keyBudgetService.getBudget(
                userContext.getAccountId(), customer.getExternalClientCustomerId(), keyId.toString());
        if (budget == null || budget.getAmountLimit() == null) {
            return null;
        }
        return AgentStatusResponse.Spend.builder()
                .cap(budget.getAmountLimit())
                .spent(budget.getAmountSpent())
                .remaining(budget.getAmountRemaining())
                .currency(settings.getCurrency())
                .resetsAt(budget.getResetsAt())
                .build();
    }

    private AgentStatusResponse.SpendMandateStatus spendMandate(Customer customer, AccountSetting settings) {
        CheckoutSession latest = checkoutSessionRepository
                .findFirstByCustomerIdAndPurposeOrderByCreatedAtDesc(customer.getId(), CheckoutSession.PURPOSE_SPEND_MANDATE)
                .orElse(null);
        String pendingUrl = latest != null && CheckoutSession.STATUS_PENDING.equals(latest.getStatus())
                ? latest.getCheckoutUrl() : null;

        KeyBudgetService.MandateUsage usage = keyBudgetService.mandateUsage(customer);
        if (usage != null) {
            // An active mandate stays in force while a raised one waits for the principal.
            return AgentStatusResponse.SpendMandateStatus.builder()
                    .status("active")
                    .setupUrl(pendingUrl)
                    .maxAmount(usage.limit())
                    .spent(usage.spent())
                    .remaining(usage.remaining())
                    .period(usage.period().name().toLowerCase(Locale.ROOT))
                    .resetsAt(usage.resetsAt())
                    .currency(settings.getCurrency())
                    .build();
        }
        if (latest == null) {
            return AgentStatusResponse.SpendMandateStatus.builder().status("none").build();
        }
        // No mandate on the customer: either none has completed yet, or it completed before mandates moved
        // onto the customer and lives on as key budgets, which the spend block reports.
        String status = switch (latest.getStatus()) {
            case CheckoutSession.STATUS_COMPLETED -> "active";
            case CheckoutSession.STATUS_EXPIRED -> "expired";
            default -> "pending";
        };
        return AgentStatusResponse.SpendMandateStatus.builder()
                .status(status)
                .setupUrl(pendingUrl)
                .maxAmount(latest.getAmount())
                .currency(settings.getCurrency())
                .build();
    }

    @Data
    public static class OwnerRequest {
        @NotBlank
        @Email
        @jakarta.validation.constraints.Size(max = 255)
        private String email;
    }
}
