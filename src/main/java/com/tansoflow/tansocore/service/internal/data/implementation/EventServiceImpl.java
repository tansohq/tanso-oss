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
package com.tansoflow.tansocore.service.internal.data.implementation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.AccountSetting;
import com.tansoflow.tansocore.entity.CreditPoolSubscription;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.Event;
import com.tansoflow.tansocore.entity.PlanFeatureRule;
import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.integration.stripe.StripeSyncService;
import com.tansoflow.tansocore.mapper.event.EventMapper;
import com.tansoflow.tansocore.model.api.external.StripeMode;
import com.tansoflow.tansocore.model.event.events.EventDto;
import com.tansoflow.tansocore.model.event.events.EventGroupDto;
import com.tansoflow.tansocore.model.event.events.EventIngestionResult;
import com.tansoflow.tansocore.model.event.events.type.EventType;
import com.tansoflow.tansocore.model.monetization.cost.CostModel;
import com.tansoflow.tansocore.model.monetization.cost.DefaultCostConfig;
import com.tansoflow.tansocore.model.monetization.cost.ModelAwareCostModel;
import com.tansoflow.tansocore.model.monetization.cost.SimpleCostModel;
import com.tansoflow.tansocore.model.monetization.pricing.PricingModel;
import com.tansoflow.tansocore.model.monetization.pricing.SimpleUsageModel;
import com.tansoflow.tansocore.repository.AccountRepository;
import com.tansoflow.tansocore.repository.AccountSettingRepository;
import com.tansoflow.tansocore.repository.CreditPoolSubscriptionRepository;
import com.tansoflow.tansocore.repository.CustomerRepository;
import com.tansoflow.tansocore.repository.EntitlementRepository;
import com.tansoflow.tansocore.repository.EventRepository;
import com.tansoflow.tansocore.entity.ModelPricing;
import com.tansoflow.tansocore.entity.StripeCustomer;
import com.tansoflow.tansocore.repository.ModelPricingRepository;
import com.tansoflow.tansocore.repository.StripeCustomerRepository;
import com.tansoflow.tansocore.repository.FeatureRepository;
import com.tansoflow.tansocore.repository.InvoiceRepository;
import com.tansoflow.tansocore.repository.PlanFeatureRuleRepository;
import com.tansoflow.tansocore.repository.SubscriptionRepository;
import com.tansoflow.tansocore.service.internal.data.EventService;
import com.tansoflow.tansocore.service.internal.monetization.CreditService;
import com.tansoflow.tansocore.util.ModelPricingResolver;
import com.tansoflow.tansocore.util.monetization.RuleCalculationUtil;
import com.tansoflow.tansocore.model.customer.CustomerSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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

@Service
@Slf4j
public class EventServiceImpl implements EventService {
    private final EventRepository eventRepository;
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final FeatureRepository featureRepository;
    private final EntitlementRepository entitlementRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanFeatureRuleRepository planFeatureRuleRepository;
    private final EventMapper eventMapper;
    private final com.tansoflow.tansocore.service.client.ClientEntitlementService clientEntitlementService;
    private final com.tansoflow.tansocore.property.AppProperty appProperty;
    private final AccountSettingRepository accountSettingRepository;
    private final StripeSyncService stripeSyncService;
    private final CreditService creditService;
    private final CreditPoolSubscriptionRepository creditPoolSubscriptionRepository;
    private final InvoiceRepository invoiceRepository;
    private final EventService self;
    private final ObjectMapper objectMapper;
    private final StripeCustomerRepository stripeCustomerRepository;
    private final ModelPricingRepository modelPricingRepository;
    private final ModelPricingResolver modelPricingResolver;
    private final EntityManager entityManager;

    public EventServiceImpl(
            EventRepository eventRepository,
            AccountRepository accountRepository,
            CustomerRepository customerRepository,
            FeatureRepository featureRepository, EntitlementRepository entitlementRepository,
            SubscriptionRepository subscriptionRepository,
            PlanFeatureRuleRepository planFeatureRuleRepository,
            EventMapper eventMapper,
            @Lazy com.tansoflow.tansocore.service.client.ClientEntitlementService clientEntitlementService,
            com.tansoflow.tansocore.property.AppProperty appProperty,
            AccountSettingRepository accountSettingRepository,
            @Lazy StripeSyncService stripeSyncService,
            @Lazy CreditService creditService,
            CreditPoolSubscriptionRepository creditPoolSubscriptionRepository,
            InvoiceRepository invoiceRepository,
            @Lazy EventService self,
            ObjectMapper objectMapper,
            StripeCustomerRepository stripeCustomerRepository,
            ModelPricingRepository modelPricingRepository,
            ModelPricingResolver modelPricingResolver,
            EntityManager entityManager) {
        this.eventRepository = eventRepository;
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.featureRepository = featureRepository;
        this.entitlementRepository = entitlementRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.planFeatureRuleRepository = planFeatureRuleRepository;
        this.eventMapper = eventMapper;
        this.clientEntitlementService = clientEntitlementService;
        this.appProperty = appProperty;
        this.accountSettingRepository = accountSettingRepository;
        this.stripeSyncService = stripeSyncService;
        this.creditService = creditService;
        this.creditPoolSubscriptionRepository = creditPoolSubscriptionRepository;
        this.invoiceRepository = invoiceRepository;
        this.self = self;
        this.objectMapper = objectMapper;
        this.stripeCustomerRepository = stripeCustomerRepository;
        this.modelPricingRepository = modelPricingRepository;
        this.modelPricingResolver = modelPricingResolver;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public EventIngestionResult createEvent(EventDto eventDto) {
        log.info("Creating event: {}", eventDto);

        EventIngestionResult.EventIngestionResultBuilder resultBuilder = EventIngestionResult.builder();

        Account account = accountRepository.findById(eventDto.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Account not found with id: " + eventDto.getAccountId()));

        // Resolve stripeCustomerId via stripe_customers bridge table
        if (eventDto.getCustomerId() == null && eventDto.getStripeCustomerId() != null) {
            StripeCustomer sc = stripeCustomerRepository.findByStripeCustomerExternalIdAndAccount(
                    eventDto.getStripeCustomerId(), account);
            if (sc != null) {
                eventDto.setCustomerId(sc.getCustomer().getId());
            } else {
                Customer autoCreated = autoCreateCustomerForStripe(account, eventDto.getStripeCustomerId());
                if (autoCreated != null) {
                    eventDto.setCustomerId(autoCreated.getId());
                    resultBuilder.customerAutoCreated(true);
                    log.info("Auto-created customer with Stripe link for stripeCustomerId: {} and accountId: {}",
                            eventDto.getStripeCustomerId(), account.getId());
                }
            }
        }

        // Resolve customerReferenceId to customerId if provided, auto-creating if needed
        if (eventDto.getCustomerId() == null && eventDto.getCustomerReferenceId() != null) {
            customerRepository.getCustomerByReferenceIdAndAccountId(eventDto.getCustomerReferenceId(), account.getId())
                    .ifPresentOrElse(
                            customer -> eventDto.setCustomerId(customer.getId()),
                            () -> {
                                Customer autoCreated = autoCreateCustomer(account, eventDto.getCustomerReferenceId());
                                if (autoCreated != null) {
                                    eventDto.setCustomerId(autoCreated.getId());
                                    resultBuilder.customerAutoCreated(true);
                                    log.info("Auto-created customer for referenceId: {} and accountId: {}",
                                            eventDto.getCustomerReferenceId(), account.getId());
                                }
                            }
                    );
        }

        // Auto-generate idempotency key if not provided
        if (eventDto.getEventIdempotencyKey() == null || eventDto.getEventIdempotencyKey().isBlank()) {
            eventDto.setEventIdempotencyKey(UUID.randomUUID().toString());
        }

        if (eventDto.getContext() == null) {
            eventDto.setContext(new HashMap<>());
        }

        eventDto.getContext().put("processedOn", Instant.now().toString());

        // Auto-detect provider from model name if not explicitly set
        if (eventDto.getModelProvider() == null && eventDto.getModel() != null) {
            String detected = com.tansoflow.tansocore.util.ModelProviderResolver.resolveProvider(eventDto.getModel());
            if (detected != null) {
                eventDto.setModelProvider(detected);
            }
        }

        // Normalize input/output tokens ↔ costUnits for backward compatibility
        if (eventDto.getInputTokens() != null || eventDto.getOutputTokens() != null) {
            BigDecimal in = eventDto.getInputTokens() != null ? eventDto.getInputTokens() : BigDecimal.ZERO;
            BigDecimal out = eventDto.getOutputTokens() != null ? eventDto.getOutputTokens() : BigDecimal.ZERO;
            if (eventDto.getCostUnits() == null) {
                eventDto.setCostUnits(in.add(out));
            }
        } else if (eventDto.getCostUnits() != null) {
            eventDto.setInputTokens(eventDto.getCostUnits());
            eventDto.setOutputTokens(BigDecimal.ZERO);
        }

        // Dogfooding: Track usage for Tanso Platform if this is NOT an entitlement check event itself
        // to avoid infinite recursion and only track actual business value events.
        if (appProperty.isDogfoodingEnabled()
                && !appProperty.getMasterAccountId().equals(account.getId().toString())
                && eventDto.getEventType() != com.tansoflow.tansocore.model.event.events.type.EventType.ENTITLEMENT_CHECKED) {

            trackTansoPlatformUsage(account);
        }

        // Resolve featureId from featureKey/entitlementId before native ID check
        if (eventDto.getFeatureId() == null) {
            if (eventDto.getEntitlementId() != null) {
                entitlementRepository.findById(eventDto.getEntitlementId()).flatMap(entitlement ->
                        featureRepository.findByKeyAndAccountId(entitlement.getFeatureKey(), account.getId()))
                        .ifPresent(f -> eventDto.setFeatureId(f.getId()));
            } else if (eventDto.getFeatureKey() != null) {
                featureRepository.findByKeyAndAccountId(eventDto.getFeatureKey(), account.getId())
                        .ifPresentOrElse(
                                f -> eventDto.setFeatureId(f.getId()),
                                () -> {
                                    // Auto-create feature when events arrive with an unknown featureKey
                                    com.tansoflow.tansocore.entity.Feature autoFeature = autoCreateFeature(account, eventDto.getFeatureKey());
                                    if (autoFeature != null) {
                                        eventDto.setFeatureId(autoFeature.getId());
                                    }
                                }
                        );
            }
        }

        // Resolve native IDs — check which referenced entities exist in this account
        resolveNativeIds(eventDto, account);

        boolean billingEligible = Boolean.TRUE.equals(eventDto.getCustomerIsNative())
                && Boolean.TRUE.equals(eventDto.getFeatureIsNative());

        // Attempt to resolve a feature and calculate cost if missing (billing pipeline)
        ResolvedEventContext resolvedCtx = billingEligible
                ? resolveAndCalculateCost(eventDto, account)
                : null;

        // Resolve credit denomination from the feature's PlanFeatureRule
        String creditDenomination = null;
        if (billingEligible && resolvedCtx != null && resolvedCtx.subscription() != null && eventDto.getFeatureId() != null) {
            PlanFeatureRule rule = planFeatureRuleRepository.findPlanFeatureRuleByPlan_IdAndFeature_Id(
                    resolvedCtx.subscription().getPlan().getId(), eventDto.getFeatureId());
            if (rule != null && rule.getCreditModel() != null) {
                creditDenomination = rule.getCreditModel().getDenomination();
            }
        }

        // Max usage enforcement
        if (billingEligible && resolvedCtx != null && resolvedCtx.pricingModel() != null && resolvedCtx.pricingModel().hasMaxUsage()
                && eventDto.getCustomerId() != null && eventDto.getFeatureId() != null && resolvedCtx.subscription() != null) {
            BigDecimal cumulative = eventRepository.sumUsageUnitsBySubscriptionAndFeatureIdSince(
                    eventDto.getCustomerId(), eventDto.getSubscriptionId(), eventDto.getFeatureId(), resolvedCtx.subscription().getCreatedAt(), Instant.now());
            if (cumulative.compareTo(resolvedCtx.pricingModel().getMaxUsage()) >= 0) {
                log.info("Max usage limit reached for customer {} on feature {}: cumulative={}, max={}",
                        eventDto.getCustomerId(), eventDto.getFeatureId(), cumulative, resolvedCtx.pricingModel().getMaxUsage());
                // Zero out usage and cost — event is still stored for analytics
                eventDto.setUsageUnits(BigDecimal.ZERO);
                eventDto.setCostAmount(BigDecimal.ZERO);
                eventDto.setRevenueAmount(BigDecimal.ZERO);
                eventDto.getContext().put("usage_limit_exceeded", true);
                eventDto.getContext().put("usage_limit_max", resolvedCtx.pricingModel().getMaxUsage());
                eventDto.getContext().put("usage_limit_cumulative", cumulative);
                resultBuilder.usageLimitExceeded(true)
                        .message("Usage limit of " + resolvedCtx.pricingModel().getMaxUsage() + " exceeded. Event recorded but zeroed out.");
            }
        }

        // Cost fallback chain: event costAmount > account DefaultCostConfig > global model pricing
        applyDefaultCostIfMissing(eventDto, account);
        applyGlobalModelPricingIfMissing(eventDto);

        Event event = eventMapper.eventDtoToEventEntity(eventDto);
        event.setAccount(account);

        try {
            eventRepository.saveAndFlush(event);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.warn("Concurrent insert detected for event idempotency key: {}", eventDto.getEventIdempotencyKey());
            return resultBuilder.build();
        }

        // Synchronous credit deduction for events with usage (includes atomic hard limit check)
        if (billingEligible) {
            try {
                deductCreditsForEvent(eventDto, event, account, creditDenomination);
            } catch (IllegalStateException e) {
                if (e.getMessage() != null && e.getMessage().contains("hard limit")) {
                    return resultBuilder.message("Credit pool depleted — hard limit active").build();
                }
                throw e;
            }

            // Forward usage to Stripe Meters for STRIPE_INTEGRATION accounts (credit-covered quantity excluded)
            forwardToStripeMeterIfNeeded(eventDto, account);
        }

        return resultBuilder.build();
    }

    private void applyDefaultCostIfMissing(EventDto eventDto, Account account) {
        if (eventDto.getCostAmount() != null) return;
        if (eventDto.getUsageUnits() == null && eventDto.getCostUnits() == null) return;

        AccountSetting setting = accountSettingRepository.findAccountSettingById(account.getId());
        if (setting == null || setting.getDefaultCostConfig() == null) return;

        ObjectMapper safeMapper = objectMapper.copy()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);
        DefaultCostConfig config = safeMapper.convertValue(
                setting.getDefaultCostConfig(), DefaultCostConfig.class);

        BigDecimal cost = config.calculateCostAmount(
                eventDto.getUsageUnits(), eventDto.getModel(), eventDto.getCostUnits(),
                eventDto.getInputTokens(), eventDto.getOutputTokens());
        if (cost != null) {
            eventDto.setCostAmount(cost.setScale(6, RoundingMode.HALF_UP));
            if (eventDto.getCostUnit() == null && config.getCostUnit() != null) {
                eventDto.setCostUnit(config.getCostUnit().toUpperCase());
            }
            Map<String, Object> context = eventDto.getContext();
            context.put("sys_cost_source", "account_default");
            if (eventDto.getModel() != null) context.put("sys_model", eventDto.getModel());
            if (eventDto.getModelProvider() != null) context.put("sys_model_provider", eventDto.getModelProvider());
        }
    }

    private void applyGlobalModelPricingIfMissing(EventDto eventDto) {
        if (eventDto.getCostAmount() != null) return;
        if (eventDto.getModel() == null) return;

        BigDecimal units = eventDto.getCostUnits() != null ? eventDto.getCostUnits() : eventDto.getUsageUnits();
        if (units == null || units.compareTo(BigDecimal.ZERO) == 0) return;

        ModelPricingResolver.ResolvedPricing resolved = modelPricingResolver.resolve(eventDto.getModel());
        if (resolved == null) return;
        ModelPricing pricing = resolved.pricing();

        // Two-rate calculation: (inputTokens * inputRate + outputTokens * outputRate) / 1,000,000
        BigDecimal inTokens = eventDto.getInputTokens() != null ? eventDto.getInputTokens() : units;
        BigDecimal outTokens = eventDto.getOutputTokens() != null ? eventDto.getOutputTokens() : BigDecimal.ZERO;
        BigDecimal cost = inTokens.multiply(pricing.getInputCostPerMillion())
                .add(outTokens.multiply(pricing.getOutputCostPerMillion()))
                .divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP);
        eventDto.setCostAmount(cost.setScale(6, RoundingMode.HALF_UP));

        // Auto-detect provider from pricing table
        if (eventDto.getModelProvider() == null) {
            eventDto.setModelProvider(pricing.getProvider());
        }

        Map<String, Object> context = eventDto.getContext();
        if (context == null) {
            context = new HashMap<>();
            eventDto.setContext(context);
        }
        context.put("sys_cost_source", "global_model_pricing");
        context.put("sys_model", eventDto.getModel());
        context.put("sys_model_provider", pricing.getProvider());
        if (resolved.fuzzyMatched()) {
            context.put("sys_model_resolved_from", pricing.getModel());
            context.put("sys_model_fuzzy_matched", true);
        }
    }

    private com.tansoflow.tansocore.entity.Feature autoCreateFeature(Account account, String featureKey) {
        try {
            com.tansoflow.tansocore.entity.Feature feature = new com.tansoflow.tansocore.entity.Feature();
            feature.setAccount(account);
            feature.setKey(featureKey);
            feature.setName(featureKey.replace("_", " ").replace("-", " "));
            feature.setDescription("Auto-created from event");
            feature.setIsEnabled(true);
            feature.setIsDeleted(false);
            feature.setMetadata(new HashMap<>());
            return featureRepository.saveAndFlush(feature);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.info("Concurrent feature creation for key: {}, retrying lookup", featureKey);
            return featureRepository.findByKeyAndAccountId(featureKey, account.getId()).orElse(null);
        }
    }

    private Customer autoCreateCustomer(Account account, String referenceId) {
        try {
            Customer customer = new Customer();
            customer.setAccount(account);
            customer.setExternalClientCustomerId(referenceId);
            customer.setSource(CustomerSource.EVENT_AUTO_CREATED);
            return customerRepository.saveAndFlush(customer);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Race condition: another thread created the same customer — retry lookup
            log.info("Concurrent customer creation for referenceId: {}, retrying lookup", referenceId);
            return customerRepository.getCustomerByReferenceIdAndAccountId(referenceId, account.getId())
                    .orElse(null);
        }
    }

    private Customer autoCreateCustomerForStripe(Account account, String stripeCustomerId) {
        try {
            // Create customer without externalClientCustomerId — linked via stripe_customers bridge
            Customer customer = new Customer();
            customer.setAccount(account);
            customer.setSource(CustomerSource.EVENT_AUTO_CREATED);
            customerRepository.saveAndFlush(customer);

            // Create stripe_customers bridge entry
            StripeCustomer sc = new StripeCustomer();
            sc.setAccount(account);
            sc.setCustomer(customer);
            sc.setStripeCustomerExternalId(stripeCustomerId);
            sc.setSyncedAt(Instant.now());
            stripeCustomerRepository.save(sc);

            return customer;
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Race condition: another thread created the same customer — retry lookup
            log.info("Concurrent customer creation for stripeCustomerId: {}, retrying lookup", stripeCustomerId);
            StripeCustomer existing = stripeCustomerRepository.findByStripeCustomerExternalIdAndAccount(
                    stripeCustomerId, account);
            return existing != null ? existing.getCustomer() : null;
        }
    }

    private void resolveNativeIds(EventDto eventDto, Account account) {
        if (eventDto.getCustomerId() != null) {
            eventDto.setCustomerIsNative(
                    customerRepository.existsByIdAndAccountId(eventDto.getCustomerId(), account.getId()));
        }
        if (eventDto.getFeatureId() != null) {
            eventDto.setFeatureIsNative(featureRepository.findByIdAndAccount(eventDto.getFeatureId(), account)
                    .isPresent());
        }
        if (eventDto.getSubscriptionId() != null) {
            eventDto.setSubscriptionIsNative(
                    subscriptionRepository.findSubscriptionByUuidAndAccountId(eventDto.getSubscriptionId(), account.getId()) != null);
        }
        if (eventDto.getEntitlementId() != null) {
            eventDto.setEntitlementIsNative(
                    entitlementRepository.findByIdAndAccountId(eventDto.getEntitlementId(), account.getId()).isPresent());
        }
        if (eventDto.getInvoiceId() != null) {
            eventDto.setInvoiceIsNative(
                    invoiceRepository.findByIdAndAccount(eventDto.getInvoiceId(), account.getId()) != null);
        }
    }

    private record ResolvedEventContext(Subscription subscription, PricingModel pricingModel) {}

    private void forwardToStripeMeterIfNeeded(EventDto eventDto, Account account) {
        if (eventDto.getFeatureId() == null || eventDto.getCustomerId() == null
                || eventDto.getUsageUnits() == null
                || eventDto.getUsageUnits().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        try {
            AccountSetting setting = accountSettingRepository.findAccountSettingById(account.getId());
            if (setting == null || (!setting.getStripeMode().isStripeIntegration() && setting.getStripeMode() != StripeMode.STRIPE_DRIVEN)) {
                return;
            }

            // Skip forwarding for accumulate-mode features — tanso-core handles billing directly
            Subscription activeSub = subscriptionRepository.findSubscriptionsByCustomer_Id(eventDto.getCustomerId())
                    .stream()
                    .filter(Subscription::getIsActive)
                    .findFirst()
                    .orElse(null);

            if (activeSub != null) {
                PlanFeatureRule rule = planFeatureRuleRepository.findPlanFeatureRuleByPlan_IdAndFeature_Id(
                        activeSub.getPlan().getId(), eventDto.getFeatureId());
                if (rule != null) {
                    PricingModel pricingModel = RuleCalculationUtil.extractPricingModel(rule);
                    if (pricingModel != null && pricingModel.isAccumulateMode()) {
                        log.debug("Skipping Stripe meter forwarding for accumulate-mode feature {} on account {}",
                                eventDto.getFeatureId(), account.getId());
                        return;
                    }
                }
            }

            // Credit-covered usage must NOT be forwarded to Stripe Meters — only overage flows to Stripe
            BigDecimal usageToForward = eventDto.getUsageUnits();
            Object creditDeducted = eventDto.getContext() != null ? eventDto.getContext().get("credit_deducted") : null;
            if (creditDeducted != null) {
                BigDecimal deducted = new BigDecimal(creditDeducted.toString());
                usageToForward = usageToForward.subtract(deducted).max(BigDecimal.ZERO);
                if (usageToForward.compareTo(BigDecimal.ZERO) <= 0) {
                    log.debug("All usage covered by credits for event on feature {}, skipping Stripe meter", eventDto.getFeatureId());
                    return;
                }
            }

            Instant timestamp = eventDto.getOccurredAt() != null ? eventDto.getOccurredAt() : Instant.now();
            stripeSyncService.forwardUsageToStripeMeter(
                    eventDto.getFeatureId(),
                    eventDto.getCustomerId(),
                    account.getId(),
                    usageToForward,
                    timestamp
            );
        } catch (Exception e) {
            log.error("Failed to forward usage to Stripe Meter for account {}: {}", account.getId(), e.getMessage(), e);
            eventDto.getContext().put("stripe_meter_forward_failed", true);
        }
    }

    /**
     * Deducts credits for the event from linked credit pools (FIFO by draw priority).
     * Any credit-covered usage is tracked in event context for downstream exclusion from Stripe meters.
     */
    private void deductCreditsForEvent(EventDto eventDto, Event event, Account account, String creditDenomination) {
        if (creditDenomination == null) return; // no credit model → no deduction

        if (eventDto.getSubscriptionId() == null || eventDto.getUsageUnits() == null
                || eventDto.getUsageUnits().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        try {
            List<CreditPoolSubscription> links = creditPoolSubscriptionRepository
                    .findBySubscriptionIdAndCreditPool_DenominationOrderByDrawPriority(
                            eventDto.getSubscriptionId(), creditDenomination);

            if (links.isEmpty()) return;

            // Atomic hard limit check: sum available balance across all pools with hard limits
            BigDecimal totalAvailable = BigDecimal.ZERO;
            boolean anyHardLimit = false;
            for (CreditPoolSubscription link : links) {
                var pool = link.getCreditPool();
                if (pool.getHardLimit()) {
                    anyHardLimit = true;
                    totalAvailable = totalAvailable.add(pool.getBalance());
                }
            }
            if (anyHardLimit && totalAvailable.compareTo(eventDto.getUsageUnits()) < 0) {
                eventDto.getContext().put("credit_blocked", true);
                event.setContext(eventDto.getContext());
                eventRepository.save(event);
                throw new IllegalStateException("Credit pool depleted — hard limit active for denomination " + creditDenomination);
            }

            // Credit deduction assumes 1:1 mapping between usageUnits and credit denomination.
            // Future: add creditConversionRate to PlanCreditAllocation for N:1 conversion.
            BigDecimal remainingToDeduct = eventDto.getUsageUnits();
            BigDecimal totalDeducted = BigDecimal.ZERO;

            for (CreditPoolSubscription link : links) {
                if (remainingToDeduct.compareTo(BigDecimal.ZERO) <= 0) break;

                var pool = link.getCreditPool();
                if (!"ACTIVE".equals(pool.getStatus()) || pool.getBalance().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                BigDecimal deductAmount = remainingToDeduct.min(pool.getBalance());

                // Respect per-subscription draw limit
                if (link.getDrawLimit() != null) {
                    BigDecimal available = link.getDrawLimit().subtract(link.getTotalDrawn());
                    deductAmount = deductAmount.min(available.max(BigDecimal.ZERO));
                }

                if (deductAmount.compareTo(BigDecimal.ZERO) <= 0) continue;

                var deductionRequest = new com.tansoflow.tansocore.model.credit.request.CreditDeductionRequest();
                deductionRequest.setCreditPoolId(pool.getId().toString());
                deductionRequest.setAmount(deductAmount);
                deductionRequest.setSubscriptionId(eventDto.getSubscriptionId().toString());
                deductionRequest.setCustomerId(eventDto.getCustomerId() != null ? eventDto.getCustomerId().toString() : null);
                deductionRequest.setEventId(event.getId());
                deductionRequest.setDescription("Usage deduction for event " + event.getId());

                creditService.deductCredits(deductionRequest, account.getId().toString());

                // Update draw tracking
                link.setTotalDrawn(link.getTotalDrawn().add(deductAmount));
                creditPoolSubscriptionRepository.save(link);

                totalDeducted = totalDeducted.add(deductAmount);
                remainingToDeduct = remainingToDeduct.subtract(deductAmount);
            }

            if (totalDeducted.compareTo(BigDecimal.ZERO) > 0) {
                eventDto.getContext().put("credit_deducted", totalDeducted);
                eventDto.getContext().put("credit_remaining_usage", remainingToDeduct);
                event.setContext(eventDto.getContext());
                eventRepository.save(event); // persist credit context back to DB
                log.info("Deducted {} credits for event {}, {} usage remains for billing",
                        totalDeducted, event.getId(), remainingToDeduct);
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to deduct credits for event {}: {}", event.getId(), e.getMessage(), e);
            throw new RuntimeException("Credit deduction failed for event " + event.getId(), e);
        }
    }

    private void trackTansoPlatformUsage(Account clientAccount) {
        try {
            // 1. Entitlement check / simulation (no real usage recorded)
            com.tansoflow.tansocore.model.entitlement.api.EntitlementEvaluationRequest checkRequest =
                    new com.tansoflow.tansocore.model.entitlement.api.EntitlementEvaluationRequest();
            checkRequest.setCustomerReferenceId(clientAccount.getId().toString());
            checkRequest.setFeatureKey("feature_events_ingested");

            com.tansoflow.tansocore.model.entitlement.api.UsageContext usageCtx =
                    new com.tansoflow.tansocore.model.entitlement.api.UsageContext();
            usageCtx.setUsageUnits(BigDecimal.ONE);
            checkRequest.setUsage(usageCtx);

            clientEntitlementService.evaluateEntitlement(appProperty.getMasterAccountId(), checkRequest);

            // 2. Record actual usage via a separate CLIENT_TRACKED event
            UUID masterAccountId = UUID.fromString(appProperty.getMasterAccountId());
            Customer platformCustomer = customerRepository.getCustomerByReferenceIdAndAccountId(
                    clientAccount.getId().toString(), masterAccountId).orElse(null);

            if (platformCustomer != null) {
                EventDto usageEvent = new EventDto();
                usageEvent.setAccountId(masterAccountId);
                usageEvent.setCustomerId(platformCustomer.getId());
                usageEvent.setEventName("feature_events_ingested");
                usageEvent.setEventType(com.tansoflow.tansocore.model.event.events.type.EventType.CLIENT_TRACKED);
                usageEvent.setOccurredAt(Instant.now());
                usageEvent.setEventIdempotencyKey(UUID.randomUUID().toString());
                usageEvent.setUsageUnits(BigDecimal.ONE);

                Map<String, Object> context = new HashMap<>();
                context.put("sys_dogfooding", true);
                context.put("source_account", clientAccount.getId().toString());
                usageEvent.setContext(context);

                self.createEvent(usageEvent);
            }
        } catch (Exception e) {
            log.error("Failed to track Tanso Platform usage for account {}: {}", clientAccount.getId(), e.getMessage(), e);
        }
    }

    private ResolvedEventContext resolveAndCalculateCost(EventDto eventDto, Account account) {
        // featureId is already resolved in createEvent before native ID check

        if (eventDto.getFeatureId() != null && eventDto.getCustomerId() != null) {
            Subscription activeSub = null;

            // When subscriptionId is provided, use that specific subscription
            if (eventDto.getSubscriptionId() != null) {
                activeSub = subscriptionRepository.findById(eventDto.getSubscriptionId())
                        .filter(Subscription::getIsActive)
                        .orElse(null);
            }

            // Fall back to first active subscription for the customer
            if (activeSub == null) {
                List<Subscription> subscriptions = subscriptionRepository.findSubscriptionsByCustomer_Id(eventDto.getCustomerId());
                activeSub = subscriptions.stream()
                        .filter(Subscription::getIsActive)
                        .findFirst()
                        .orElse(null);
            }

            if (activeSub != null) {
                // Stamp subscriptionId so the event is scoped to this subscription
                if (eventDto.getSubscriptionId() == null) {
                    eventDto.setSubscriptionId(activeSub.getId());
                }

                PlanFeatureRule rule = planFeatureRuleRepository.findPlanFeatureRuleByPlan_IdAndFeature_Id(
                        activeSub.getPlan().getId(), eventDto.getFeatureId());

                if (rule != null && rule.getValue() != null) {
                    PricingModel pricingModel = RuleCalculationUtil.extractPricingModel(rule);
                    CostModel costModel = RuleCalculationUtil.extractCostModel(rule);

                    if (pricingModel != null) {
                        // 1. Resolve Usage Unit Type from Rule if missing
                        if (eventDto.getUsageUnitType() == null && pricingModel.getUsageUnitType() != null) {
                            eventDto.setUsageUnitType(pricingModel.getUsageUnitType());
                        }

                        // 2. Resolve Cost Unit (COGS) from CostModel
                        if (eventDto.getCostUnit() == null && costModel instanceof SimpleCostModel simpleCostModel && simpleCostModel.getCostUnit() != null) {
                            eventDto.setCostUnit(simpleCostModel.getCostUnit().toUpperCase());
                        } else if (eventDto.getCostUnit() == null && costModel instanceof ModelAwareCostModel mac && mac.getCostUnit() != null) {
                            eventDto.setCostUnit(mac.getCostUnit().toUpperCase());
                        }

                        // 3. Resolve Revenue Unit from PricingModel
                        if (eventDto.getRevenueUnit() == null && pricingModel instanceof SimpleUsageModel simpleModel && simpleModel.getCostUnit() != null) {
                            eventDto.setRevenueUnit(simpleModel.getCostUnit().toUpperCase());
                        }

                        // 4. Calculate revenue (from PricingModel) and cost (from CostModel)
                        if (eventDto.getUsageUnits() != null) {
                            try {
                                BigDecimal usageUnits = eventDto.getUsageUnits();

                                // Calculate revenue from PricingModel — always
                                if (eventDto.getRevenueAmount() == null) {
                                    BigDecimal charge = RuleCalculationUtil.calculateRevenueAmount(usageUnits, rule);
                                    eventDto.setRevenueAmount(charge.setScale(2, RoundingMode.HALF_UP));
                                }

                                // Calculate cost (COGS) from CostModel — prefer typed CostInput, fall back to legacy meta
                                if (eventDto.getCostAmount() == null) {
                                    BigDecimal cost;
                                    if (eventDto.getModel() != null || eventDto.getCostUnits() != null) {
                                        cost = RuleCalculationUtil.calculateCostAmount(
                                                usageUnits, rule, eventDto.getModel(), eventDto.getCostUnits(),
                                                eventDto.getInputTokens(), eventDto.getOutputTokens());
                                    } else {
                                        cost = RuleCalculationUtil.calculateCostAmount(usageUnits, rule, eventDto.getMeta());
                                    }
                                    if (cost != null) {
                                        eventDto.setCostAmount(cost.setScale(6, RoundingMode.HALF_UP));
                                    }
                                }

                                // Add system tracking to context
                                Map<String, Object> context = eventDto.getContext();
                                context.put("sys_applied_rule_id", rule.getId().toString());
                                context.put("sys_captured_unit_price", pricingModel.getRate());
                                context.put("sys_pricing_model", pricingModel.getModel());
                                if (costModel != null) {
                                    context.put("sys_cost_model", costModel.getEffectiveModel());
                                    context.put("sys_cost_params", costModel.getCostParameters());
                                }

                                // Stamp model info from typed CostInput fields
                                if (eventDto.getModel() != null) {
                                    context.put("sys_model", eventDto.getModel());
                                }
                                if (eventDto.getModelProvider() != null) {
                                    context.put("sys_model_provider", eventDto.getModelProvider());
                                }

                                if (eventDto.getRevenueUnit() == null) {
                                    eventDto.setRevenueUnit("CURRENCY"); // Final fallback
                                }
                                log.info("Calculated revenue={} cost={} for event",
                                        eventDto.getRevenueAmount(), eventDto.getCostAmount());
                            } catch (Exception e) {
                                log.error("Failed to calculate cost for event: {}", e.getMessage(), e);
                                eventDto.getContext().put("sys_cost_calculation_failed", "true");
                            }
                        }

                        return new ResolvedEventContext(activeSub, pricingModel);
                    }
                }
            }
        }
        return null;
    }

    @Override
    public Page<EventDto> getEvents(UUID accountId, Instant start, Instant end, String customerReferenceId, UUID planId, UUID featureId, EventType eventType, String model, String modelProvider, String eventName, Pageable pageable) {
        log.info("Fetching events for account: {} with filters", accountId);
        Specification<Event> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("account").get("id"), accountId));

            if (start != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), start));
            }
            if (end != null) {
                predicates.add(cb.lessThan(root.get("occurredAt"), end));
            }
            if (featureId != null) {
                predicates.add(cb.equal(root.get("featureId"), featureId));
            }
            if (eventType != null) {
                predicates.add(cb.equal(root.get("eventType"), eventType));
            }
            if (model != null && !model.isBlank()) {
                predicates.add(cb.equal(root.get("model"), model));
            }
            if (modelProvider != null && !modelProvider.isBlank()) {
                predicates.add(cb.equal(root.get("modelProvider"), modelProvider));
            }
            if (eventName != null && !eventName.isBlank()) {
                String escaped = eventName.toLowerCase()
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_");
                predicates.add(cb.like(cb.lower(root.get("eventName")), "%" + escaped + "%", '\\'));
            }
            if (customerReferenceId != null && !customerReferenceId.isBlank() && query != null) {
                jakarta.persistence.criteria.Subquery<UUID> customerSubquery = query.subquery(UUID.class);
                jakarta.persistence.criteria.Root<Customer> customerRoot = customerSubquery.from(Customer.class);
                customerSubquery.select(customerRoot.get("id"));
                customerSubquery.where(
                        cb.equal(customerRoot.get("externalClientCustomerId"), customerReferenceId),
                        cb.equal(customerRoot.get("account").get("id"), accountId)
                );
                predicates.add(root.get("customerId").in(customerSubquery));
            }
            if (planId != null && query != null) {
                jakarta.persistence.criteria.Subquery<UUID> subscriptionSubquery = query.subquery(UUID.class);
                jakarta.persistence.criteria.Root<Subscription> subscriptionRoot = subscriptionSubquery.from(Subscription.class);
                subscriptionSubquery.select(subscriptionRoot.get("id"));
                subscriptionSubquery.where(
                        cb.equal(subscriptionRoot.get("plan").get("id"), planId),
                        cb.equal(subscriptionRoot.get("account").get("id"), accountId)
                );
                predicates.add(root.get("subscriptionId").in(subscriptionSubquery));
            }

            // Important: Handle count query for pagination
            assert query != null;
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("account", JoinType.LEFT);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return eventRepository.findAll(spec, pageable)
                .map(eventMapper::eventEntityToEventDto);
    }

    @Override
    public List<EventGroupDto> getGroupedEvents(UUID accountId, String groupBy, Instant start, Instant end,
                                                 String customerReferenceId, UUID planId, UUID featureId,
                                                 EventType eventType, String model, String modelProvider, String eventName) {
        log.info("Fetching grouped events for account: {}, groupBy: {}", accountId, groupBy);

        var cb = entityManager.getCriteriaBuilder();
        var query = cb.createQuery(Object[].class);
        var root = query.from(Event.class);

        // Build predicates (same as getEvents)
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("account").get("id"), accountId));
        if (start != null) predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), start));
        if (end != null) predicates.add(cb.lessThan(root.get("occurredAt"), end));
        if (featureId != null) predicates.add(cb.equal(root.get("featureId"), featureId));
        if (eventType != null) predicates.add(cb.equal(root.get("eventType"), eventType));
        if (model != null && !model.isBlank()) predicates.add(cb.equal(root.get("model"), model));
        if (modelProvider != null && !modelProvider.isBlank()) predicates.add(cb.equal(root.get("modelProvider"), modelProvider));
        if (eventName != null && !eventName.isBlank()) {
            String escaped = eventName.toLowerCase()
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
            predicates.add(cb.like(cb.lower(root.get("eventName")), "%" + escaped + "%", '\\'));
        }
        if (customerReferenceId != null && !customerReferenceId.isBlank()) {
            var customerSubquery = query.subquery(UUID.class);
            var customerRoot = customerSubquery.from(Customer.class);
            customerSubquery.select(customerRoot.get("id"));
            customerSubquery.where(
                    cb.equal(customerRoot.get("externalClientCustomerId"), customerReferenceId),
                    cb.equal(customerRoot.get("account").get("id"), accountId)
            );
            predicates.add(root.get("customerId").in(customerSubquery));
        }
        if (planId != null) {
            var subscriptionSubquery = query.subquery(UUID.class);
            var subscriptionRoot = subscriptionSubquery.from(Subscription.class);
            subscriptionSubquery.select(subscriptionRoot.get("id"));
            subscriptionSubquery.where(
                    cb.equal(subscriptionRoot.get("plan").get("id"), planId),
                    cb.equal(subscriptionRoot.get("account").get("id"), accountId)
            );
            predicates.add(root.get("subscriptionId").in(subscriptionSubquery));
        }

        // Determine group-by field
        // UUID fields (customerId, featureId) must be kept as-is for Hibernate; we convert to String in Java
        boolean isUuidGroup = "CUSTOMER".equalsIgnoreCase(groupBy) || "FEATURE".equalsIgnoreCase(groupBy);
        jakarta.persistence.criteria.Expression<?> groupExpression = switch (groupBy.toUpperCase()) {
            case "MODEL" -> root.get("model");
            case "MODEL_PROVIDER" -> root.get("modelProvider");
            case "CUSTOMER" -> root.get("customerId");
            case "FEATURE" -> root.get("featureId");
            case "EVENT_NAME" -> root.get("eventName");
            default -> throw new IllegalArgumentException("Invalid groupBy: " + groupBy);
        };

        // Add non-null filter for the group field to avoid null groups
        predicates.add(cb.isNotNull(groupExpression));

        query.multiselect(
                groupExpression,
                cb.count(root),
                cb.coalesce(cb.sum(root.get("costAmount")), BigDecimal.ZERO),
                cb.coalesce(cb.sum(root.get("revenueAmount")), BigDecimal.ZERO),
                cb.coalesce(cb.sum(root.get("usageUnits")), BigDecimal.ZERO),
                cb.greatest(root.<Instant>get("occurredAt"))
        );
        query.where(predicates.toArray(new Predicate[0]));
        query.groupBy(groupExpression);
        query.orderBy(cb.desc(cb.coalesce(cb.sum(root.get("costAmount")), BigDecimal.ZERO)));

        var results = entityManager.createQuery(query).getResultList();

        // Resolve labels and reference IDs for UUID-based groups
        Map<String, String> labelMap = new HashMap<>();
        Map<String, String> keyMap = new HashMap<>(); // maps internal ID -> filterable key (e.g., customerReferenceId)
        if ("CUSTOMER".equalsIgnoreCase(groupBy)) {
            var customerIds = results.stream()
                    .map(r -> r[0] instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(r[0])))
                    .toList();
            if (!customerIds.isEmpty()) {
                customerRepository.findAllById(customerIds).forEach(c -> {
                    String name = ((c.getFirstName() != null ? c.getFirstName() : "") + " " +
                                   (c.getLastName() != null ? c.getLastName() : "")).trim();
                    labelMap.put(c.getId().toString(), name.isEmpty() ? c.getExternalClientCustomerId() : name);
                    // Use externalClientCustomerId as the groupKey so drill-down works with customerReferenceId filter
                    keyMap.put(c.getId().toString(), c.getExternalClientCustomerId());
                });
            }
        } else if ("FEATURE".equalsIgnoreCase(groupBy)) {
            var featureIds = results.stream()
                    .map(r -> r[0] instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(r[0])))
                    .toList();
            if (!featureIds.isEmpty()) {
                featureRepository.findAllById(featureIds).forEach(f ->
                        labelMap.put(f.getId().toString(), f.getName() != null ? f.getName() : f.getKey())
                );
            }
        }

        return results.stream().map(r -> {
            String rawKey = r[0] instanceof UUID uuid ? uuid.toString() : String.valueOf(r[0]);
            String key = keyMap.getOrDefault(rawKey, rawKey);
            String label = labelMap.getOrDefault(rawKey, key);
            // Hibernate may return java.sql.Timestamp for MAX(instant) aggregates
            Instant lastOccurred = r[5] instanceof java.sql.Timestamp ts ? ts.toInstant()
                    : r[5] instanceof Instant i ? i : null;
            return new EventGroupDto(
                    key,
                    label,
                    (Long) r[1],
                    (BigDecimal) r[2],
                    (BigDecimal) r[3],
                    (BigDecimal) r[4],
                    lastOccurred
            );
        }).toList();
    }

    @Override
    public Page<EventDto> getEventsByAccountId(UUID accountId, Pageable pageable) {
        log.info("Fetching events for account: {}", accountId);
        return eventRepository.findByAccountId(accountId, pageable)
                .map(eventMapper::eventEntityToEventDto);
    }

    @Override
    public boolean existsByEventIdempotencyKey(UUID accountId, String eventIdempotencyKey) {
        return eventRepository.existsByAccountIdAndEventIdempotencyKey(accountId, eventIdempotencyKey);
    }



}
