package com.tansoflow.tansocore.service.internal.onboarding;

import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.auth.UserContextAuthentication;
import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.entity.User;
import com.tansoflow.tansocore.entity.UsersAccount;
import com.tansoflow.tansocore.model.account.response.SubscriptionStatusResponse;
import com.tansoflow.tansocore.model.auth.response.JwtResponse;
import com.tansoflow.tansocore.model.customer.request.CustomerRequest;
import com.tansoflow.tansocore.repository.UsersAccountRepository;
import com.tansoflow.tansocore.service.internal.account.AccountService;
import com.tansoflow.tansocore.service.internal.account.CustomerService;
import com.tansoflow.tansocore.service.internal.account.UserService;
import com.tansoflow.tansocore.service.internal.audit.AuditHelper;
import com.tansoflow.tansocore.service.internal.monetization.PlanService;
import com.tansoflow.tansocore.service.internal.monetization.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class OnboardingService {
    private final UserService userService;
    private final AccountService accountService;
    private final CustomerService customerService;
    private final UsersAccountRepository usersAccountRepository;
    private final SubscriptionService subscriptionService;
    private final PlanService planService;
    private final com.tansoflow.tansocore.property.AppProperty appProperty;
    private final AuditHelper auditHelper;

    @Transactional
    public JwtResponse onboardNewOrganization(CustomerRequest request, String organizationName, String password, String planId) {
        log.info("Starting onboarding for new organization");

        if (userService.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("An account with this email already exists");
        }

        String name;
        if (organizationName != null && !organizationName.isBlank()) {
            name = organizationName;
        } else if (request.getFirstName() != null && !request.getFirstName().isBlank()) {
            name = request.getFirstName() + "'s Organization";
        } else {
            name = request.getEmail().split("@")[0] + "'s Organization";
        }
        Account newAccount = accountService.createAccount(name);

        User newUser = userService.createUser(request, password);

        UsersAccount link = new UsersAccount();
        link.setUser(newUser);
        link.setAccount(newAccount);
        link.setRole("ADMIN");
        usersAccountRepository.save(link);

        accountService.createApiKeyForAccount(newAccount);
        accountService.createAccountSettings(newAccount);

        if (appProperty.isDogfoodingEnabled()) {
            SecurityContext originalContext = SecurityContextHolder.getContext();
            try {
                setupMasterAccountSecurityContext();

                CustomerRequest customerRequest = new CustomerRequest();
                customerRequest.setCustomerReferenceId(newAccount.getId().toString());
                customerRequest.setEmail(request.getEmail());
                customerRequest.setFirstName(request.getFirstName());
                customerRequest.setLastName(request.getLastName());

                customerService.createCustomer(appProperty.getMasterAccountId(), customerRequest);
                autoEnrollInFreePlan(newAccount.getId().toString());

                log.info("Successfully onboarded organization {} as a customer of Tanso Platform", newAccount.getId());
            } finally {
                SecurityContextHolder.setContext(originalContext);
            }
        } else {
            log.info("Dogfooding is disabled, skipping registration of organization {} as a customer", newAccount.getId());
        }

        auditHelper.audit("SIGNUP", newUser.getId(), newAccount.getId(),
                "ACCOUNT", newAccount.getId().toString(), "New organization onboarded");

        return userService.generateJwtTokenForUser(newUser, newAccount.getId().toString());
    }

    public SubscriptionStatusResponse getSubscriptionStatus(String accountId) {
        if (!appProperty.isDogfoodingEnabled()) {
            return SubscriptionStatusResponse.builder()
                    .hasActiveSubscription(true)
                    .build();
        }

        if (accountId.equals(appProperty.getMasterAccountId())) {
            return SubscriptionStatusResponse.builder()
                    .hasActiveSubscription(true)
                    .build();
        }

        SecurityContext originalContext = SecurityContextHolder.getContext();
        try {
            setupMasterAccountSecurityContext();

            Customer customer = customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(
                    accountId, appProperty.getMasterAccountId());

            if (customer == null) {
                log.warn("No customer found for account {} - account may not be properly registered", accountId);
                return SubscriptionStatusResponse.builder()
                        .hasActiveSubscription(false)
                        .build();
            }

            var subscriptions = subscriptionService.getSubscriptionsByCustomer(
                    customer.getId().toString(), appProperty.getMasterAccountId());

            var activeSubscription = subscriptions.stream()
                    .filter(sub -> Boolean.TRUE.equals(sub.getIsActive()))
                    .findFirst();

            if (activeSubscription.isEmpty()) {
                log.debug("Account {} has no active subscription", accountId);
                return SubscriptionStatusResponse.builder()
                        .hasActiveSubscription(false)
                        .build();
            }

            var sub = activeSubscription.get();
            var builder = SubscriptionStatusResponse.builder()
                    .hasActiveSubscription(true);

            if (sub.getPlan() != null) {
                builder.planName(sub.getPlan().getName())
                       .planPriceAmount(sub.getPlan().getPriceAmount())
                       .planIntervalMonths(sub.getPlan().getIntervalMonths())
                       .planKey(sub.getPlan().getKey());
            }

            return builder.build();
        } catch (Exception e) {
            log.error("Error checking subscription status for account {}: {}", accountId, e.getMessage());
            return SubscriptionStatusResponse.builder()
                    .hasActiveSubscription(false)
                    .build();
        } finally {
            SecurityContextHolder.setContext(originalContext);
        }
    }

    @Transactional
    public void subscribeAccountToPlan(String accountId, String planId) {
        if (!appProperty.isDogfoodingEnabled()) {
            log.warn("Cannot subscribe account {} to plan {} - dogfooding is disabled", accountId, planId);
            throw new IllegalStateException("Subscription management is not enabled in this environment");
        }

        SecurityContext originalContext = SecurityContextHolder.getContext();
        try {
            setupMasterAccountSecurityContext();

            Customer customer = customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(
                    accountId, appProperty.getMasterAccountId());

            if (customer == null) {
                log.error("No customer found for account {} - cannot subscribe to plan", accountId);
                throw new IllegalStateException("Account not properly registered. Please contact support.");
            }

            Plan plan = planService.retrievePlan(UUID.fromString(appProperty.getMasterAccountId()), UUID.fromString(planId));

            subscriptionService.subscribe(customer, plan, appProperty.getMasterAccountId());
            log.info("Successfully subscribed account {} to plan {}", accountId, planId);
            auditHelper.audit("SUBSCRIPTION_CREATED", null, UUID.fromString(accountId),
                    "PLAN", planId, "Subscribed to plan");
        } finally {
            SecurityContextHolder.setContext(originalContext);
        }
    }

    private void autoEnrollInFreePlan(String accountId) {
        String freePlanId = appProperty.getDefaultFreePlanId();
        if (freePlanId == null || freePlanId.isBlank()) {
            log.debug("No default free plan configured, skipping auto-enrollment for account {}", accountId);
            return;
        }

        try {
            Customer customer = customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(
                    accountId, appProperty.getMasterAccountId());
            if (customer == null) {
                log.warn("Cannot auto-enroll account {} - customer not found", accountId);
                return;
            }

            Plan plan = planService.retrievePlan(
                    UUID.fromString(appProperty.getMasterAccountId()), UUID.fromString(freePlanId));
            subscriptionService.subscribe(customer, plan, appProperty.getMasterAccountId());
            log.info("Auto-enrolled account {} in default free plan {}", accountId, freePlanId);
        } catch (Exception e) {
            log.error("Failed to auto-enroll account {} in free plan {}: {}",
                    accountId, freePlanId, e.getMessage(), e);
        }
    }

    private void setupMasterAccountSecurityContext() {
        UserContext principal = new UserContext(appProperty.getMasterAccountId(), "tanso_platform_master_key");
        UserContextAuthentication auth = new UserContextAuthentication(principal, List.of(new SimpleGrantedAuthority("ROLE_CLIENT")));
        auth.setAuthenticated(true);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }
}
