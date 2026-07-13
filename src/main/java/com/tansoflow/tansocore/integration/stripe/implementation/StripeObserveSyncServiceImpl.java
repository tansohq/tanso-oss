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
package com.tansoflow.tansocore.integration.stripe.implementation;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Price;
import com.stripe.model.Product;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.param.CustomerListParams;
import com.stripe.param.PriceListParams;
import com.stripe.param.ProductListParams;
import com.stripe.param.SubscriptionListParams;
import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.entity.StripeCustomer;
import com.tansoflow.tansocore.entity.StripeProduct;
import com.tansoflow.tansocore.entity.StripeSubscription;
import com.tansoflow.tansocore.integration.stripe.StripeClientFactory;
import com.tansoflow.tansocore.integration.stripe.StripeObserveSyncService;
import com.tansoflow.tansocore.model.customer.CustomerDto;
import com.tansoflow.tansocore.model.data.stripe.response.StripeObserveSyncResponse;
import com.tansoflow.tansocore.entity.AccountSetting;
import com.tansoflow.tansocore.repository.AccountRepository;
import com.tansoflow.tansocore.repository.AccountSettingRepository;
import com.tansoflow.tansocore.repository.CustomerRepository;
import com.tansoflow.tansocore.repository.PlanRepository;
import com.tansoflow.tansocore.repository.StripeCustomerRepository;
import com.tansoflow.tansocore.repository.StripeProductPlansRepository;
import com.tansoflow.tansocore.repository.StripeSubscriptionRepository;
import com.tansoflow.tansocore.repository.SubscriptionRepository;
import com.tansoflow.tansocore.service.internal.account.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class StripeObserveSyncServiceImpl implements StripeObserveSyncService {

    private final StripeClientFactory stripeClientFactory;
    private final AccountRepository accountRepository;
    private final AccountSettingRepository accountSettingRepository;
    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final PlanRepository planRepository;
    private final StripeProductPlansRepository stripeProductPlansRepository;
    private final StripeCustomerRepository stripeCustomerRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final StripeSubscriptionRepository stripeSubscriptionRepository;

    @Override
    @Transactional
    public StripeObserveSyncResponse sync(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

        AccountSetting settings = accountSettingRepository.findAccountSettingById(accountId);
        if (settings == null || !settings.isObserveMode()) {
            throw new IllegalArgumentException("Observe sync is only available in Observe mode");
        }

        StripeClient stripe = stripeClientFactory.forAccount(accountId);

        int customersSynced = 0;
        int plansSynced = 0;
        int subscriptionsSynced = 0;
        int errors = 0;
        List<String> warnings = new ArrayList<>();

        // Phase 1: Sync customers
        Map<String, Customer> customerMap = new HashMap<>();
        try {
            CustomerListParams customerParams = CustomerListParams.builder().setLimit(100L).build();
            for (com.stripe.model.Customer sc : stripe.customers().list(customerParams).autoPagingIterable()) {
                try {
                    Customer tansoCustomer = syncCustomer(account, sc);
                    customerMap.put(sc.getId(), tansoCustomer);
                    customersSynced++;
                } catch (Exception e) {
                    log.error("Failed to sync Stripe customer {}: {}", sc.getId(), e.getMessage(), e);
                    warnings.add("Failed to sync customer " + sc.getId() + ": " + e.getMessage());
                    errors++;
                }
            }
        } catch (StripeException e) {
            throw new RuntimeException("Failed to list Stripe customers: " + e.getMessage(), e);
        }

        // Phase 2: Sync products + prices → plans
        // First, build a price map keyed by product ID
        Map<String, Price> priceByProduct = new HashMap<>();
        try {
            PriceListParams priceParams = PriceListParams.builder().setLimit(100L).setActive(true).build();
            for (Price price : stripe.prices().list(priceParams).autoPagingIterable()) {
                String productId = price.getProduct();
                if (productId == null) continue;
                // Prefer recurring prices; only replace if we don't have one yet or previous was non-recurring
                Price existing = priceByProduct.get(productId);
                if (existing == null || (existing.getRecurring() == null && price.getRecurring() != null)) {
                    priceByProduct.put(productId, price);
                }
            }
        } catch (StripeException e) {
            throw new RuntimeException("Failed to list Stripe prices: " + e.getMessage(), e);
        }

        Map<String, Plan> productToPlan = new HashMap<>();
        try {
            ProductListParams productParams = ProductListParams.builder().setLimit(100L).setActive(true).build();
            for (Product product : stripe.products().list(productParams).autoPagingIterable()) {
                try {
                    Price price = priceByProduct.get(product.getId());
                    if (price == null) {
                        warnings.add("Product '" + product.getName() + "' has no active price, skipped");
                        continue;
                    }
                    Plan plan = syncPlan(account, product, price);
                    productToPlan.put(product.getId(), plan);
                    plansSynced++;
                } catch (Exception e) {
                    log.error("Failed to sync Stripe product {}: {}", product.getId(), e.getMessage(), e);
                    warnings.add("Failed to sync product " + product.getId() + ": " + e.getMessage());
                    errors++;
                }
            }
        } catch (StripeException e) {
            throw new RuntimeException("Failed to list Stripe products: " + e.getMessage(), e);
        }

        // Phase 3: Sync subscriptions
        try {
            SubscriptionListParams subParams = SubscriptionListParams.builder().setLimit(100L).build();
            for (Subscription stripeSub : stripe.subscriptions().list(subParams).autoPagingIterable()) {
                try {
                    // Resolve customer
                    Customer tansoCustomer = customerMap.get(stripeSub.getCustomer());
                    if (tansoCustomer == null) {
                        StripeCustomer bridge = stripeCustomerRepository.findByStripeCustomerExternalIdAndAccount(
                                stripeSub.getCustomer(), account);
                        if (bridge != null) tansoCustomer = bridge.getCustomer();
                    }
                    if (tansoCustomer == null) {
                        warnings.add("Subscription " + stripeSub.getId() + ": no mapped customer for " + stripeSub.getCustomer());
                        continue;
                    }

                    // Resolve plan from first item's product
                    Plan plan = resolvePlanFromSubscription(stripeSub, productToPlan);
                    if (plan == null) {
                        warnings.add("Subscription " + stripeSub.getId() + ": no mapped plan");
                        continue;
                    }

                    syncSubscription(account, stripeSub, tansoCustomer, plan);
                    subscriptionsSynced++;
                } catch (Exception e) {
                    log.error("Failed to sync Stripe subscription {}: {}", stripeSub.getId(), e.getMessage(), e);
                    warnings.add("Failed to sync subscription " + stripeSub.getId() + ": " + e.getMessage());
                    errors++;
                }
            }
        } catch (StripeException e) {
            throw new RuntimeException("Failed to list Stripe subscriptions: " + e.getMessage(), e);
        }

        log.info("Observe sync completed for account {}: {} customers, {} plans, {} subscriptions, {} errors",
                accountId, customersSynced, plansSynced, subscriptionsSynced, errors);

        return StripeObserveSyncResponse.builder()
                .customersSynced(customersSynced)
                .plansSynced(plansSynced)
                .subscriptionsSynced(subscriptionsSynced)
                .errors(errors)
                .warnings(warnings)
                .build();
    }

    private Customer syncCustomer(Account account, com.stripe.model.Customer stripeCustomer) {
        // Check bridge table first
        StripeCustomer bridge = stripeCustomerRepository.findByStripeCustomerExternalIdAndAccount(
                stripeCustomer.getId(), account);
        if (bridge != null) {
            // Update existing customer with latest Stripe data
            Customer customer = bridge.getCustomer();
            updateCustomerFromStripe(customer, stripeCustomer);
            customerRepository.save(customer);
            return customer;
        }

        // Check if customer exists by external reference ID (e.g., auto-created from events)
        Customer existing = customerRepository.getCustomerByReferenceIdAndAccountId(
                stripeCustomer.getId(), account.getId()).orElse(null);
        if (existing != null) {
            updateCustomerFromStripe(existing, stripeCustomer);
            customerRepository.save(existing);
            // Create bridge entry
            createStripeCustomerBridge(account, existing, stripeCustomer.getId());
            return existing;
        }

        // Create new customer
        CustomerDto dto = new CustomerDto();
        dto.setCustomerReferenceId(stripeCustomer.getId());
        dto.setSource("STRIPE_IMPORTED");
        populateCustomerDto(dto, stripeCustomer);
        Customer customer = customerService.createCustomer(account, dto);

        // Create bridge entry
        createStripeCustomerBridge(account, customer, stripeCustomer.getId());
        return customer;
    }

    private void updateCustomerFromStripe(Customer customer, com.stripe.model.Customer stripeCustomer) {
        if (stripeCustomer.getName() != null && !stripeCustomer.getName().isBlank()) {
            String[] parts = stripeCustomer.getName().trim().split("\\s+", 2);
            customer.setFirstName(parts[0]);
            customer.setLastName(parts.length > 1 ? parts[1] : "");
        }
        if (stripeCustomer.getEmail() != null && !stripeCustomer.getEmail().isBlank()) {
            customer.setEmail(stripeCustomer.getEmail());
        }
    }

    private void populateCustomerDto(CustomerDto dto, com.stripe.model.Customer stripeCustomer) {
        if (stripeCustomer.getName() != null && !stripeCustomer.getName().isBlank()) {
            String[] parts = stripeCustomer.getName().trim().split("\\s+", 2);
            dto.setFirstName(parts[0]);
            dto.setLastName(parts.length > 1 ? parts[1] : "");
        } else {
            dto.setFirstName("Stripe");
            dto.setLastName("Customer");
        }
        dto.setEmail(stripeCustomer.getEmail() != null && !stripeCustomer.getEmail().isBlank()
                ? stripeCustomer.getEmail()
                : stripeCustomer.getId() + "@imported.stripe");
    }

    private void createStripeCustomerBridge(Account account, Customer customer, String stripeCustomerId) {
        StripeCustomer sc = new StripeCustomer();
        sc.setCustomer(customer);
        sc.setAccount(account);
        sc.setStripeCustomerExternalId(stripeCustomerId);
        sc.setSyncedAt(Instant.now());
        stripeCustomerRepository.save(sc);
    }

    private Plan syncPlan(Account account, Product product, Price price) {
        // Check bridge table
        StripeProduct bridge = stripeProductPlansRepository.findByStripeProductExternalIdAndAccount(
                product.getId(), account);
        if (bridge != null) {
            // Update existing plan's price
            Plan plan = bridge.getPlan();
            updatePlanFromPrice(plan, product, price);
            planRepository.save(plan);
            return plan;
        }

        // Create new plan
        String baseKey = slugify(product.getName());
        String planKey = ensureUniquePlanKey(baseKey, account.getId());

        Plan plan = new Plan();
        plan.setAccount(account);
        plan.setKey(planKey);
        plan.setName(product.getName());
        plan.setDescription(product.getDescription() != null ? product.getDescription() : "Imported from Stripe");
        plan.setBillingTiming("IN_ADVANCE");
        plan.setStatus("ACTIVE");
        updatePlanFromPrice(plan, product, price);
        planRepository.save(plan);

        // Create bridge entry
        StripeProduct sp = new StripeProduct();
        sp.setPlan(plan);
        sp.setAccount(account);
        sp.setStripeProductExternalId(product.getId());
        stripeProductPlansRepository.save(sp);

        return plan;
    }

    private void updatePlanFromPrice(Plan plan, Product product, Price price) {
        plan.setName(product.getName());
        long unitAmount = price.getUnitAmount() != null ? price.getUnitAmount() : 0L;
        plan.setPriceAmount(BigDecimal.valueOf(unitAmount).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));

        if (price.getRecurring() != null && price.getRecurring().getInterval() != null) {
            plan.setIntervalMonths(mapInterval(price.getRecurring().getInterval()));
        } else {
            plan.setIntervalMonths(1);
        }

        if (price.getCurrency() != null) {
            plan.setCurrency(price.getCurrency().toUpperCase());
        }
    }

    private int mapInterval(String interval) {
        return switch (interval) {
            case "year" -> 12;
            case "month" -> 1;
            case "week" -> 1;
            case "day" -> 1;
            default -> 1;
        };
    }

    private void syncSubscription(Account account, Subscription stripeSub, Customer customer, Plan plan) {
        // Check bridge table
        StripeSubscription bridge = stripeSubscriptionRepository
                .findByStripeSubscriptionExternalIdAndAccount(stripeSub.getId(), account);
        if (bridge != null) {
            // Update existing subscription
            com.tansoflow.tansocore.entity.Subscription tansoSub = bridge.getSubscription();
            tansoSub.setIsActive("active".equals(stripeSub.getStatus()));
            setPeriodFromStripe(tansoSub, stripeSub);
            subscriptionRepository.save(tansoSub);
            return;
        }

        // Create new subscription
        com.tansoflow.tansocore.entity.Subscription tansoSub = new com.tansoflow.tansocore.entity.Subscription();
        tansoSub.setCustomer(customer);
        tansoSub.setPlan(plan);
        tansoSub.setAccount(account);
        tansoSub.setIsActive("active".equals(stripeSub.getStatus()));
        setPeriodFromStripe(tansoSub, stripeSub);
        subscriptionRepository.save(tansoSub);

        // Create bridge entry
        StripeSubscription bridgeSub = new StripeSubscription();
        bridgeSub.setSubscription(tansoSub);
        bridgeSub.setAccount(account);
        bridgeSub.setStripeSubscriptionExternalId(stripeSub.getId());
        stripeSubscriptionRepository.save(bridgeSub);
    }

    private void setPeriodFromStripe(com.tansoflow.tansocore.entity.Subscription tansoSub, Subscription stripeSub) {
        if (stripeSub.getItems() != null && !stripeSub.getItems().getData().isEmpty()) {
            SubscriptionItem firstItem = stripeSub.getItems().getData().get(0);
            if (firstItem.getCurrentPeriodStart() != null) {
                tansoSub.setCurrentPeriodStart(Instant.ofEpochSecond(firstItem.getCurrentPeriodStart()));
            }
            if (firstItem.getCurrentPeriodEnd() != null) {
                tansoSub.setCurrentPeriodEnd(Instant.ofEpochSecond(firstItem.getCurrentPeriodEnd()));
            }
        }
    }

    private Plan resolvePlanFromSubscription(Subscription stripeSub, Map<String, Plan> productToPlan) {
        if (stripeSub.getItems() == null || stripeSub.getItems().getData().isEmpty()) return null;
        SubscriptionItem item = stripeSub.getItems().getData().get(0);
        if (item.getPrice() == null || item.getPrice().getProduct() == null) return null;
        return productToPlan.get(item.getPrice().getProduct());
    }

    private String slugify(String input) {
        if (input == null || input.isBlank()) return "unnamed";
        String slug = input.toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (slug.isEmpty()) return "unnamed";
        return slug.length() > 80 ? slug.substring(0, 80) : slug;
    }

    private String ensureUniquePlanKey(String baseKey, UUID accountId) {
        int maxLength = 100;
        String candidate = baseKey.length() > maxLength ? baseKey.substring(0, maxLength) : baseKey;
        if (!planRepository.existsByKeyAndAccountId(candidate, accountId)) return candidate;

        for (int i = 2; i <= 100; i++) {
            String suffix = "_" + i;
            String attempt = baseKey.length() + suffix.length() > maxLength
                    ? baseKey.substring(0, maxLength - suffix.length()) + suffix
                    : baseKey + suffix;
            if (!planRepository.existsByKeyAndAccountId(attempt, accountId)) return attempt;
        }

        String uuidSuffix = "_" + UUID.randomUUID().toString().substring(0, 8);
        return baseKey.length() + uuidSuffix.length() > maxLength
                ? baseKey.substring(0, maxLength - uuidSuffix.length()) + uuidSuffix
                : baseKey + uuidSuffix;
    }
}
