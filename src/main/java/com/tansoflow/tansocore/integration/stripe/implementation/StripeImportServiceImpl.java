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
import com.stripe.model.StripeCollection;
import com.stripe.model.SubscriptionItem;
import com.stripe.param.CustomerListParams;
import com.stripe.param.ProductListParams;
import com.stripe.param.SubscriptionListParams;
import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.Feature;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.entity.PlanFeatureRule;
import com.tansoflow.tansocore.entity.StripeCustomer;
import com.tansoflow.tansocore.entity.StripeImportJob;
import com.tansoflow.tansocore.entity.StripeProduct;
import com.tansoflow.tansocore.entity.StripeSubscription;
import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.integration.stripe.StripeClientFactory;
import com.tansoflow.tansocore.integration.stripe.StripeImportService;
import com.tansoflow.tansocore.model.customer.CustomerDto;
import com.tansoflow.tansocore.model.data.stripe.request.StripeDiscoverRequest;
import com.tansoflow.tansocore.model.data.stripe.request.StripeImportStartRequest;
import com.tansoflow.tansocore.model.data.stripe.request.StripeMapProductRequest;
import com.tansoflow.tansocore.model.data.stripe.response.StripeDiscoveryResponse;
import com.tansoflow.tansocore.model.data.stripe.response.StripeImportStatusResponse;
import com.tansoflow.tansocore.repository.AccountRepository;
import com.tansoflow.tansocore.repository.FeatureRepository;
import com.tansoflow.tansocore.repository.PlanFeatureRuleRepository;
import com.tansoflow.tansocore.repository.PlanRepository;
import com.tansoflow.tansocore.repository.StripeCustomerRepository;
import com.tansoflow.tansocore.repository.StripeImportJobRepository;
import com.tansoflow.tansocore.repository.StripeProductPlansRepository;
import com.tansoflow.tansocore.repository.StripeSubscriptionRepository;
import com.tansoflow.tansocore.repository.SubscriptionRepository;
import com.tansoflow.tansocore.service.internal.account.CustomerService;
import com.tansoflow.tansocore.service.internal.monetization.EntitlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class StripeImportServiceImpl implements StripeImportService {
    private final StripeClientFactory stripeClientFactory;
    private final StripeCustomerRepository stripeCustomerRepository;
    private final StripeProductPlansRepository stripeProductPlansRepository;
    private final StripeSubscriptionRepository stripeSubscriptionRepository;
    private final StripeImportJobRepository stripeImportJobRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final AccountRepository accountRepository;
    private final PlanRepository planRepository;
    private final FeatureRepository featureRepository;
    private final PlanFeatureRuleRepository planFeatureRuleRepository;
    private final CustomerService customerService;
    private final EntitlementService entitlementService;

    @Override
    public StripeDiscoveryResponse discover(UUID accountId, StripeDiscoverRequest request) {
        StripeClient stripe = stripeClientFactory.forAccount(accountId);
        Account account = accountRepository.findById(accountId).orElseThrow();

        StripeDiscoveryResponse.StripeDiscoveryResponseBuilder response = StripeDiscoveryResponse.builder();

        try {
            if (request.isIncludeProducts()) {
                response.products(discoverProducts(stripe, account));
            }
            if (request.isIncludeCustomers()) {
                response.customers(discoverCustomers(stripe, account));
            }
            if (request.isIncludeSubscriptions()) {
                response.subscriptions(discoverSubscriptions(stripe));
            }
        } catch (StripeException e) {
            throw new RuntimeException("Failed to discover Stripe objects: " + e.getMessage(), e);
        }

        return response.build();
    }

    @Override
    @Transactional
    public StripeImportStatusResponse startImport(UUID accountId, StripeImportStartRequest request) {
        Account account = accountRepository.findById(accountId).orElseThrow();

        StripeImportJob job = new StripeImportJob();
        job.setAccount(account);
        job.setStatus("IN_PROGRESS");
        job.setTotalItems(countTotalItems(request));
        stripeImportJobRepository.save(job);

        try {
            processImport(account, request, job);
            job.setStatus("COMPLETED");
        } catch (Exception e) {
            log.error("Import job {} failed: {}", job.getId(), e.getMessage(), e);
            job.setStatus("FAILED");
            job.setErrorDetails(e.getMessage());
        }

        stripeImportJobRepository.save(job);
        return toStatusResponse(job);
    }

    @Override
    public StripeImportStatusResponse getImportStatus(UUID accountId, UUID jobId) {
        StripeImportJob job = stripeImportJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Import job not found: " + jobId));
        if (!job.getAccount().getId().equals(accountId)) {
            throw new IllegalArgumentException("Import job not found: " + jobId);
        }
        return toStatusResponse(job);
    }

    @Override
    @Transactional
    public void mapProduct(UUID accountId, StripeMapProductRequest request) {
        Account account = accountRepository.findById(accountId).orElseThrow();
        Plan plan = planRepository.findByIdAndAccount(request.getTansoPlanId(), account)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + request.getTansoPlanId()));

        if (stripeProductPlansRepository.existsByStripeProductExternalIdAndAccount(request.getStripeProductId(), account)) {
            throw new IllegalStateException("Stripe product already mapped: " + request.getStripeProductId());
        }

        StripeProduct stripeProduct = new StripeProduct();
        stripeProduct.setPlan(plan);
        stripeProduct.setAccount(account);
        stripeProduct.setStripeProductExternalId(request.getStripeProductId());
        stripeProductPlansRepository.save(stripeProduct);
    }

    private List<StripeDiscoveryResponse.DiscoveredProduct> discoverProducts(StripeClient stripe, Account account) throws StripeException {
        List<StripeDiscoveryResponse.DiscoveredProduct> products = new ArrayList<>();
        ProductListParams params = ProductListParams.builder().setLimit(100L).setActive(true).build();
        StripeCollection<com.stripe.model.Product> collection = stripe.products().list(params);

        for (com.stripe.model.Product product : collection.getData()) {
            boolean mapped = stripeProductPlansRepository.existsByStripeProductExternalIdAndAccount(product.getId(), account);
            products.add(StripeDiscoveryResponse.DiscoveredProduct.builder()
                    .stripeProductId(product.getId())
                    .name(product.getName())
                    .description(product.getDescription())
                    .alreadyMapped(mapped)
                    .build());
        }
        return products;
    }

    private List<StripeDiscoveryResponse.DiscoveredCustomer> discoverCustomers(StripeClient stripe, Account account) throws StripeException {
        List<StripeDiscoveryResponse.DiscoveredCustomer> customers = new ArrayList<>();
        CustomerListParams params = CustomerListParams.builder().setLimit(100L).build();
        StripeCollection<com.stripe.model.Customer> collection = stripe.customers().list(params);

        for (com.stripe.model.Customer customer : collection.getData()) {
            boolean mapped = stripeCustomerRepository.existsByStripeCustomerExternalIdAndAccount(customer.getId(), account);
            customers.add(StripeDiscoveryResponse.DiscoveredCustomer.builder()
                    .stripeCustomerId(customer.getId())
                    .name(customer.getName())
                    .email(customer.getEmail())
                    .alreadyMapped(mapped)
                    .build());
        }
        return customers;
    }

    private List<StripeDiscoveryResponse.DiscoveredSubscription> discoverSubscriptions(StripeClient stripe) throws StripeException {
        List<StripeDiscoveryResponse.DiscoveredSubscription> subscriptions = new ArrayList<>();
        SubscriptionListParams params = SubscriptionListParams.builder().setLimit(100L).build();
        StripeCollection<com.stripe.model.Subscription> collection = stripe.subscriptions().list(params);

        for (com.stripe.model.Subscription sub : collection.getData()) {
            boolean mapped = stripeSubscriptionRepository.existsStripeSubscriptionByStripeSubscriptionExternalId(sub.getId());
            String productId = null;
            if (sub.getItems() != null && !sub.getItems().getData().isEmpty()) {
                SubscriptionItem item = sub.getItems().getData().get(0);
                if (item.getPrice() != null && item.getPrice().getProduct() != null) {
                    productId = item.getPrice().getProduct();
                }
            }
            subscriptions.add(StripeDiscoveryResponse.DiscoveredSubscription.builder()
                    .stripeSubscriptionId(sub.getId())
                    .stripeCustomerId(sub.getCustomer())
                    .stripeProductId(productId)
                    .status(sub.getStatus())
                    .alreadyMapped(mapped)
                    .build());
        }
        return subscriptions;
    }

    private void processImport(Account account, StripeImportStartRequest request, StripeImportJob job) {
        // Build product mapping: stripeProductId -> tansoPlan
        Map<String, Plan> productToPlan = new HashMap<>();
        for (StripeImportStartRequest.ProductMapping pm : request.getProductMappings()) {
            Plan plan = planRepository.findByIdAndAccount(pm.getTansoPlanId(), account)
                    .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + pm.getTansoPlanId()));

            // Create bridge entry if not already mapped
            if (!stripeProductPlansRepository.existsByStripeProductExternalIdAndAccount(pm.getStripeProductId(), account)) {
                StripeProduct sp = new StripeProduct();
                sp.setPlan(plan);
                sp.setAccount(account);
                sp.setStripeProductExternalId(pm.getStripeProductId());
                stripeProductPlansRepository.save(sp);
            }
            productToPlan.put(pm.getStripeProductId(), plan);
            job.setProcessedItems(job.getProcessedItems() + 1);
        }

        // Build customer mapping: stripeCustomerId -> tansoCustomer
        Map<String, Customer> customerMap = new HashMap<>();
        if (request.getCustomerMappings() != null) {
            for (StripeImportStartRequest.CustomerMapping cm : request.getCustomerMappings()) {
                Customer tansoCustomer;
                if (cm.getTansoCustomerId() != null) {
                    tansoCustomer = customerService.retrieveCustomer(cm.getTansoCustomerId());
                } else if (cm.isAutoCreate()) {
                    tansoCustomer = autoCreateCustomer(account, cm.getStripeCustomerId());
                } else {
                    job.setFailedItems(job.getFailedItems() + 1);
                    continue;
                }

                // Create bridge entry if not already mapped
                if (!stripeCustomerRepository.existsByStripeCustomerExternalIdAndAccount(cm.getStripeCustomerId(), account)) {
                    StripeCustomer sc = new StripeCustomer();
                    sc.setCustomer(tansoCustomer);
                    sc.setAccount(account);
                    sc.setStripeCustomerExternalId(cm.getStripeCustomerId());
                    sc.setSyncedAt(Instant.now());
                    stripeCustomerRepository.save(sc);
                }
                customerMap.put(cm.getStripeCustomerId(), tansoCustomer);
                job.setProcessedItems(job.getProcessedItems() + 1);
            }
        }

        // Import subscriptions from Stripe
        importSubscriptions(account, productToPlan, customerMap, job);
    }

    private void importSubscriptions(Account account, Map<String, Plan> productToPlan,
                                     Map<String, Customer> customerMap, StripeImportJob job) {
        StripeClient stripe = stripeClientFactory.forAccount(account.getId());
        try {
            SubscriptionListParams params = SubscriptionListParams.builder().setLimit(100L).build();
            StripeCollection<com.stripe.model.Subscription> collection = stripe.subscriptions().list(params);

            for (com.stripe.model.Subscription stripeSub : collection.getData()) {
                try {
                    if (stripeSubscriptionRepository.existsStripeSubscriptionByStripeSubscriptionExternalId(stripeSub.getId())) {
                        job.setProcessedItems(job.getProcessedItems() + 1);
                        continue;
                    }

                    // Resolve customer
                    Customer tansoCustomer = customerMap.get(stripeSub.getCustomer());
                    if (tansoCustomer == null) {
                        StripeCustomer sc = stripeCustomerRepository.findByStripeCustomerExternalIdAndAccount(
                                stripeSub.getCustomer(), account);
                        if (sc != null) {
                            tansoCustomer = sc.getCustomer();
                        }
                    }
                    if (tansoCustomer == null) {
                        log.warn("No mapped customer for Stripe customer {}, skipping subscription {}",
                                stripeSub.getCustomer(), stripeSub.getId());
                        job.setFailedItems(job.getFailedItems() + 1);
                        continue;
                    }

                    // Resolve plan from first subscription item's product
                    Plan plan = resolvePlanFromSubscription(stripeSub, productToPlan);
                    if (plan == null) {
                        log.warn("No mapped plan for subscription {}, skipping", stripeSub.getId());
                        job.setFailedItems(job.getFailedItems() + 1);
                        continue;
                    }

                    // Create Tanso Subscription
                    Subscription tansoSub = new Subscription();
                    tansoSub.setCustomer(tansoCustomer);
                    tansoSub.setPlan(plan);
                    tansoSub.setAccount(account);
                    tansoSub.setIsActive("active".equals(stripeSub.getStatus()));
                    // Stripe SDK v31+: period is per-item, not on Subscription
                    if (stripeSub.getItems() != null && !stripeSub.getItems().getData().isEmpty()) {
                        var firstItem = stripeSub.getItems().getData().get(0);
                        if (firstItem.getCurrentPeriodStart() != null) {
                            tansoSub.setCurrentPeriodStart(Instant.ofEpochSecond(firstItem.getCurrentPeriodStart()));
                        }
                        if (firstItem.getCurrentPeriodEnd() != null) {
                            tansoSub.setCurrentPeriodEnd(Instant.ofEpochSecond(firstItem.getCurrentPeriodEnd()));
                        }
                    }
                    subscriptionRepository.save(tansoSub);

                    // Create bridge entry
                    StripeSubscription bridgeSub = new StripeSubscription();
                    bridgeSub.setSubscription(tansoSub);
                    bridgeSub.setAccount(account);
                    bridgeSub.setStripeSubscriptionExternalId(stripeSub.getId());
                    stripeSubscriptionRepository.save(bridgeSub);

                    // Grant entitlements
                    if (tansoSub.getIsActive()) {
                        entitlementService.processEntitlementsForSubscription(tansoSub);
                    }

                    job.setProcessedItems(job.getProcessedItems() + 1);
                } catch (Exception e) {
                    log.error("Failed to import subscription {}: {}", stripeSub.getId(), e.getMessage(), e);
                    job.setFailedItems(job.getFailedItems() + 1);
                }
            }
        } catch (StripeException e) {
            throw new RuntimeException("Failed to list Stripe subscriptions: " + e.getMessage(), e);
        }
    }

    private Plan resolvePlanFromSubscription(com.stripe.model.Subscription stripeSub, Map<String, Plan> productToPlan) {
        if (stripeSub.getItems() == null || stripeSub.getItems().getData().isEmpty()) {
            return null;
        }
        SubscriptionItem item = stripeSub.getItems().getData().get(0);
        if (item.getPrice() == null || item.getPrice().getProduct() == null) {
            return null;
        }
        return productToPlan.get(item.getPrice().getProduct());
    }

    private Customer autoCreateCustomer(Account account, String stripeCustomerId) {
        CustomerDto dto = new CustomerDto();
        dto.setCustomerReferenceId(stripeCustomerId);
        return customerService.createCustomer(account, dto);
    }

    private int countTotalItems(StripeImportStartRequest request) {
        int count = request.getProductMappings().size();
        if (request.getCustomerMappings() != null) {
            count += request.getCustomerMappings().size();
        }
        return count;
    }

    private StripeImportStatusResponse toStatusResponse(StripeImportJob job) {
        return StripeImportStatusResponse.builder()
                .jobId(job.getId())
                .status(job.getStatus())
                .totalItems(job.getTotalItems())
                .processedItems(job.getProcessedItems())
                .failedItems(job.getFailedItems())
                .errorDetails(job.getErrorDetails())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }

    // ── Auto-Create Import ──────────────────────────────────────────────

    @Override
    @Transactional
    public StripeImportStatusResponse startAutoCreateImport(UUID accountId) {
        Account account = accountRepository.findById(accountId).orElseThrow();

        StripeImportJob job = new StripeImportJob();
        job.setAccount(account);
        job.setStatus("IN_PROGRESS");
        job.setTotalItems(0);
        stripeImportJobRepository.save(job);

        try {
            processAutoCreateImport(account, job);
            job.setStatus("COMPLETED");
        } catch (Exception e) {
            log.error("Auto-create import job {} failed: {}", job.getId(), e.getMessage(), e);
            job.setStatus("FAILED");
            job.setErrorDetails(e.getMessage());
        }

        stripeImportJobRepository.save(job);
        return toStatusResponse(job);
    }

    private void processAutoCreateImport(Account account, StripeImportJob job) {
        StripeClient stripe = stripeClientFactory.forAccount(account.getId());

        // Phase 1 — Products → Plans + Features
        List<StripeDiscoveryResponse.DiscoveredProduct> products;
        try {
            products = discoverProducts(stripe, account);
        } catch (StripeException e) {
            throw new RuntimeException("Failed to discover Stripe products: " + e.getMessage(), e);
        }

        Map<String, Plan> productToPlan = new HashMap<>();
        for (StripeDiscoveryResponse.DiscoveredProduct dp : products) {
            try {
                if (dp.isAlreadyMapped()) {
                    StripeProduct sp = stripeProductPlansRepository
                            .findByStripeProductExternalIdAndAccount(dp.getStripeProductId(), account);
                    if (sp != null) {
                        productToPlan.put(dp.getStripeProductId(), sp.getPlan());
                    }
                } else {
                    Plan plan = autoCreatePlanWithFeature(account, dp);
                    productToPlan.put(dp.getStripeProductId(), plan);
                    job.setProcessedItems(job.getProcessedItems() + 1);
                }
            } catch (Exception e) {
                log.error("Failed to auto-create plan for Stripe product {}: {}", dp.getStripeProductId(), e.getMessage(), e);
                job.setFailedItems(job.getFailedItems() + 1);
            }
        }

        // Phase 2 — Customers
        List<StripeDiscoveryResponse.DiscoveredCustomer> customers;
        try {
            customers = discoverCustomers(stripe, account);
        } catch (StripeException e) {
            throw new RuntimeException("Failed to discover Stripe customers: " + e.getMessage(), e);
        }

        Map<String, Customer> customerMap = new HashMap<>();
        for (StripeDiscoveryResponse.DiscoveredCustomer dc : customers) {
            try {
                if (dc.isAlreadyMapped()) {
                    StripeCustomer sc = stripeCustomerRepository
                            .findByStripeCustomerExternalIdAndAccount(dc.getStripeCustomerId(), account);
                    if (sc != null) {
                        customerMap.put(dc.getStripeCustomerId(), sc.getCustomer());
                    }
                } else {
                    Customer customer = autoCreateCustomerFromStripe(account, dc);
                    customerMap.put(dc.getStripeCustomerId(), customer);
                    job.setProcessedItems(job.getProcessedItems() + 1);
                }
            } catch (Exception e) {
                log.error("Failed to auto-create customer for Stripe customer {}: {}", dc.getStripeCustomerId(), e.getMessage(), e);
                job.setFailedItems(job.getFailedItems() + 1);
            }
        }

        // Set total items: unmapped products + unmapped customers + subscriptions will be added by importSubscriptions
        long unmappedProducts = products.stream().filter(p -> !p.isAlreadyMapped()).count();
        long unmappedCustomers = customers.stream().filter(c -> !c.isAlreadyMapped()).count();
        job.setTotalItems((int) (unmappedProducts + unmappedCustomers));

        // Phase 3 — Subscriptions (reuse existing)
        importSubscriptions(account, productToPlan, customerMap, job);
    }

    private Plan autoCreatePlanWithFeature(Account account, StripeDiscoveryResponse.DiscoveredProduct dp) {
        String baseKey = slugify(dp.getName());
        String description = dp.getDescription() != null ? dp.getDescription() : "Imported from Stripe: " + dp.getName();

        // 1. Create Feature
        String featureKey = ensureUniqueKey(baseKey, account.getId(), true);
        Feature feature = new Feature();
        feature.setAccount(account);
        feature.setKey(featureKey);
        feature.setName(dp.getName());
        feature.setDescription(description);
        feature.setIsEnabled(true);
        featureRepository.save(feature);

        // 2. Create Plan (DRAFT initially)
        String planKey = ensureUniqueKey(baseKey, account.getId(), false);
        Plan plan = new Plan();
        plan.setAccount(account);
        plan.setKey(planKey);
        plan.setName(dp.getName());
        plan.setDescription(description);
        plan.setPriceAmount(BigDecimal.ZERO);
        plan.setIntervalMonths(1);
        plan.setBillingTiming("IN_ADVANCE");
        plan.setStatus("DRAFT");
        planRepository.save(plan);

        // 3. Create PlanFeatureRule
        PlanFeatureRule rule = new PlanFeatureRule();
        rule.setPlan(plan);
        rule.setFeature(feature);
        rule.setIsEnabled(true);
        planFeatureRuleRepository.save(rule);

        // 4. Activate plan (now meets all requirements)
        plan.setStatus("ACTIVE");
        planRepository.save(plan);

        // 5. Create StripeProduct bridge
        StripeProduct sp = new StripeProduct();
        sp.setPlan(plan);
        sp.setAccount(account);
        sp.setStripeProductExternalId(dp.getStripeProductId());
        stripeProductPlansRepository.save(sp);

        return plan;
    }

    private Customer autoCreateCustomerFromStripe(Account account, StripeDiscoveryResponse.DiscoveredCustomer dc) {
        CustomerDto dto = new CustomerDto();
        dto.setCustomerReferenceId(dc.getStripeCustomerId());

        // Enrich with name from Stripe
        if (dc.getName() != null && !dc.getName().isBlank()) {
            String[] parts = dc.getName().trim().split("\\s+", 2);
            dto.setFirstName(parts[0]);
            dto.setLastName(parts.length > 1 ? parts[1] : "");
        } else {
            dto.setFirstName("Stripe");
            dto.setLastName("Customer");
        }

        // Enrich with email from Stripe
        dto.setEmail(dc.getEmail() != null && !dc.getEmail().isBlank()
                ? dc.getEmail()
                : dc.getStripeCustomerId() + "@imported.stripe");

        Customer customer = customerService.createCustomer(account, dto);

        // Create bridge entry
        StripeCustomer sc = new StripeCustomer();
        sc.setCustomer(customer);
        sc.setAccount(account);
        sc.setStripeCustomerExternalId(dc.getStripeCustomerId());
        sc.setSyncedAt(Instant.now());
        stripeCustomerRepository.save(sc);

        return customer;
    }

    private String slugify(String input) {
        if (input == null || input.isBlank()) return "unnamed";
        String slug = input.toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (slug.isEmpty()) return "unnamed";
        return slug.length() > 80 ? slug.substring(0, 80) : slug;
    }

    private String ensureUniqueKey(String baseKey, UUID accountId, boolean isFeature) {
        int maxLength = isFeature ? 255 : 100;
        String candidate = baseKey.length() > maxLength ? baseKey.substring(0, maxLength) : baseKey;

        boolean exists = isFeature
                ? featureRepository.existsByKeyAndAccountId(candidate, accountId)
                : planRepository.existsByKeyAndAccountId(candidate, accountId);
        if (!exists) return candidate;

        for (int i = 2; i <= 100; i++) {
            String suffix = "_" + i;
            String attempt = baseKey.length() + suffix.length() > maxLength
                    ? baseKey.substring(0, maxLength - suffix.length()) + suffix
                    : baseKey + suffix;
            boolean attemptExists = isFeature
                    ? featureRepository.existsByKeyAndAccountId(attempt, accountId)
                    : planRepository.existsByKeyAndAccountId(attempt, accountId);
            if (!attemptExists) return attempt;
        }

        // Fallback: append UUID fragment
        String uuidSuffix = "_" + UUID.randomUUID().toString().substring(0, 8);
        String fallback = baseKey.length() + uuidSuffix.length() > maxLength
                ? baseKey.substring(0, maxLength - uuidSuffix.length()) + uuidSuffix
                : baseKey + uuidSuffix;
        return fallback;
    }
}
