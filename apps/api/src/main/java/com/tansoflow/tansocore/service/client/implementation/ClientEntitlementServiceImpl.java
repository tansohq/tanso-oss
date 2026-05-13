package com.tansoflow.tansocore.service.client.implementation;

import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.Feature;
import com.tansoflow.tansocore.entity.PlanFeatureRule;
import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.model.credit.CreditPoolDto;
import com.tansoflow.tansocore.model.entitlement.api.EntitlementEvaluationRequest;
import com.tansoflow.tansocore.model.entitlement.api.UsageContext;
import com.tansoflow.tansocore.model.entitlement.response.CustomerEntitlementsResponse;
import com.tansoflow.tansocore.model.entitlement.response.EntitlementResponse;
import com.tansoflow.tansocore.model.event.events.EventDto;
import com.tansoflow.tansocore.model.event.events.type.EventType;
import com.tansoflow.tansocore.model.monetization.pricing.PricingModel;
import com.tansoflow.tansocore.repository.EntitlementRepository;
import com.tansoflow.tansocore.repository.EventRepository;
import com.tansoflow.tansocore.repository.FeatureRepository;
import com.tansoflow.tansocore.repository.PlanFeatureRuleRepository;
import com.tansoflow.tansocore.repository.SubscriptionRepository;
import com.tansoflow.tansocore.service.client.ClientEntitlementService;
import com.tansoflow.tansocore.service.internal.account.CustomerService;
import com.tansoflow.tansocore.service.internal.data.EventService;
import com.tansoflow.tansocore.service.internal.monetization.CreditService;
import com.tansoflow.tansocore.service.internal.monetization.EntitlementService;
import com.tansoflow.tansocore.util.monetization.RuleCalculationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ClientEntitlementServiceImpl implements ClientEntitlementService {
    private final EntitlementService entitlementService;
    private final CreditService creditService;
    private final CustomerService customerService;
    private final EventService eventService;
    private final FeatureRepository featureRepository;
    private final EntitlementRepository entitlementRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanFeatureRuleRepository planFeatureRuleRepository;
    private final EventRepository eventRepository;

    /**
     * Retrieves a "has-a" entitlement check on a feature key belonging to a customerUuid under a tenant account
     *
     * @param referenceCustomerId - reference customer id belonging to the tenant (tables.customers.reference_id)
     * @param accountUuid         - tenant account uuid
     * @param featureKey          - feature key
     */
    @Override
    @Transactional
    public EntitlementResponse checkEntitlement(String referenceCustomerId, String accountUuid, String featureKey) {
        return checkEntitlement(referenceCustomerId, accountUuid, featureKey, true);
    }

    @Override
    @Transactional
    public EntitlementResponse checkEntitlement(String referenceCustomerId, String accountUuid, String featureKey, boolean recordEvent) {
        log.info("Checking entitlement for customerUuid={}, planUuid={}, accountUuid={}",
                referenceCustomerId, featureKey, accountUuid);

        EntitlementResponse entitlementResponse = new EntitlementResponse();
        entitlementResponse.setReferenceCustomerId(referenceCustomerId);
        entitlementResponse.setFeatureKey(featureKey);

        Customer customer = customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(referenceCustomerId, accountUuid);

        boolean isEntitled = entitlementService.isEntitled(featureKey, customer);
        String denyReason = "Entitlement is revoked";
        UsageInfo usageInfo = resolveUsageInfo(customer, featureKey, accountUuid);
        if (isEntitled && usageInfo != null && usageInfo.exceeded()) {
            isEntitled = false;
            denyReason = "Usage limit exceeded";
        }
        if (isEntitled && isCreditHardLimitReached(customer, featureKey, accountUuid)) {
            isEntitled = false;
            denyReason = "Credit limit reached";
        }
        handleEntitlement(entitlementResponse, isEntitled, denyReason);
        populateUsage(entitlementResponse, usageInfo);
        entitlementResponse.setCredit(resolveCreditInfo(customer, featureKey, accountUuid));

        if (recordEvent) {
            // Record ENTITLEMENT_CHECKED event
            try {
                EventDto eventDto = new EventDto();
                eventDto.setAccountId(UUID.fromString(accountUuid));
                eventDto.setCustomerId(customer.getId());
                eventDto.setEventName(featureKey);
                eventDto.setEventType(EventType.ENTITLEMENT_CHECKED);
                eventDto.setOccurredAt(Instant.now());
                eventDto.setEventIdempotencyKey(UUID.randomUUID().toString());

                HashMap<String, Object> properties = new HashMap<>();
                properties.put("featureKey", featureKey);
                properties.put("isEntitled", isEntitled);

                // Set standardized usage fields
                eventDto.setUsageUnits(java.math.BigDecimal.ZERO);

                // Search for feature UUID
                featureRepository.findByKeyAndAccountId(featureKey, UUID.fromString(accountUuid))
                        .ifPresent(f -> eventDto.setFeatureId(f.getId()));

                if (isEntitled) {
                    // Search for entitlement UUID
                    entitlementRepository.findFirstByCustomerAndFeatureKeyAndRevokedAtIsNullOrderByCreatedAtDesc(customer, featureKey)
                            .ifPresent(e -> eventDto.setEntitlementId(e.getId()));
                }

                eventDto.setProperties(properties);

                eventService.createEvent(eventDto);
            } catch (Exception e) {
                log.error("Failed to record entitlement check event", e);
            }
        }

        return entitlementResponse;
    }

    @Override
    @Transactional
    public EntitlementResponse evaluateEntitlement(String accountUuid, EntitlementEvaluationRequest request) {
        log.info("Checking and simulating entitlement for customerReferenceId={}, featureKey={}, accountUuid={}",
                request.getCustomerReferenceId(), request.getFeatureKey(), accountUuid);

        EntitlementResponse entitlementResponse = new EntitlementResponse();
        entitlementResponse.setReferenceCustomerId(request.getCustomerReferenceId());
        entitlementResponse.setFeatureKey(request.getFeatureKey());

        Customer customer = customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(request.getCustomerReferenceId(), accountUuid);

        boolean isEntitled = entitlementService.isEntitled(request.getFeatureKey(), customer);
        String denyReason = "Entitlement is revoked";
        UsageInfo usageInfo = resolveUsageInfo(customer, request.getFeatureKey(), accountUuid);
        if (isEntitled && usageInfo != null && usageInfo.exceeded()) {
            isEntitled = false;
            denyReason = "Usage limit exceeded";
        }
        if (isEntitled && isCreditHardLimitReached(customer, request.getFeatureKey(), accountUuid)) {
            isEntitled = false;
            denyReason = "Credit limit reached";
        }

        // 4a. Projection check: if usage context provides usageUnits, simulate whether it would exceed
        UsageContext usage = request.getUsage();
        boolean wouldExceedLimit = false;
        BigDecimal projectedUsage = null;
        BigDecimal projectedRemaining = null;

        if (usage != null && usage.getUsageUnits() != null && usageInfo != null && usageInfo.limit() != null) {
            projectedUsage = usageInfo.used().add(usage.getUsageUnits());
            projectedRemaining = usageInfo.limit().subtract(projectedUsage).max(BigDecimal.ZERO);
            wouldExceedLimit = projectedUsage.compareTo(usageInfo.limit()) > 0;

            if (isEntitled && wouldExceedLimit) {
                isEntitled = false;
                denyReason = "Proposed usage would exceed limit";
            }
        }

        handleEntitlement(entitlementResponse, isEntitled, denyReason);
        populateUsage(entitlementResponse, usageInfo);
        entitlementResponse.setCredit(resolveCreditInfo(customer, request.getFeatureKey(), accountUuid));

        // 4d. Populate simulation response
        if (usage != null && usage.getUsageUnits() != null && usageInfo != null && usageInfo.limit() != null) {
            EntitlementResponse.Simulation simulation = new EntitlementResponse.Simulation();
            simulation.setRequestedUsage(usage.getUsageUnits());
            simulation.setProjectedUsage(projectedUsage);
            simulation.setProjectedRemaining(projectedRemaining);
            simulation.setWouldExceedLimit(wouldExceedLimit);
            entitlementResponse.setSimulation(simulation);
        }

        // Record ENTITLEMENT_CHECKED event with metadata (dry-run: zero usage)
        try {
            EventDto eventDto = new EventDto();
            eventDto.setAccountId(UUID.fromString(accountUuid));
            eventDto.setCustomerId(customer.getId());
            eventDto.setEventName(request.getFeatureKey());
            eventDto.setEventType(EventType.ENTITLEMENT_CHECKED);
            eventDto.setOccurredAt(Instant.now());

            String idempotencyKey = (request.getContext() != null && request.getContext().getIdempotencyKey() != null)
                    ? request.getContext().getIdempotencyKey()
                    : UUID.randomUUID().toString();
            eventDto.setEventIdempotencyKey(idempotencyKey);

            HashMap<String, Object> properties = new HashMap<>();
            properties.put("featureKey", request.getFeatureKey());
            properties.put("isEntitled", isEntitled);

            // Search for feature UUID
            featureRepository.findByKeyAndAccountId(request.getFeatureKey(), UUID.fromString(accountUuid))
                    .ifPresent(f -> eventDto.setFeatureId(f.getId()));

            if (isEntitled) {
                // Search for entitlement UUID
                entitlementRepository.findFirstByCustomerAndFeatureKeyAndRevokedAtIsNullOrderByCreatedAtDesc(customer, request.getFeatureKey())
                        .ifPresent(e -> eventDto.setEntitlementId(e.getId()));
            }

            // 4b. Zero out real usage — event is for audit only, not cumulative tracking
            eventDto.setUsageUnits(BigDecimal.ZERO);

            if (usage != null) {
                if (usage.getEventName() != null) {
                    properties.put("trackEventName", usage.getEventName());
                }

                if (usage.getMeta() != null) {
                    eventDto.setMeta(usage.getMeta());
                }
            }

            eventDto.setProperties(properties);

            // 4c. Add simulation context to the event
            HashMap<String, Object> context = new HashMap<>();
            if (request.getContext() != null) {
                context.put("requestId", request.getContext().getIdempotencyKey());
                context.put("flowId", request.getContext().getFlowId());
            }
            if (usage != null && usage.getUsageUnits() != null) {
                context.put("sys_simulation", true);
                context.put("sys_simulated_usage", usage.getUsageUnits());
                context.put("sys_simulated_would_exceed", wouldExceedLimit);
            }
            eventDto.setContext(context);

            String flowId = (request.getContext() != null && request.getContext().getFlowId() != null)
                    ? request.getContext().getFlowId()
                    : UUID.randomUUID().toString();
            eventDto.setFlowId(flowId);
            entitlementResponse.setFlowId(flowId);

            eventService.createEvent(eventDto);
        } catch (Exception e) {
            log.error("Failed to record entitlement check and track event", e);
        }

        return entitlementResponse;
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerEntitlementsResponse getCustomerEntitlements(String referenceCustomerId, String accountUuid) {
        log.info("Getting all entitlements for customerReferenceId={}, accountUuid={}", referenceCustomerId, accountUuid);

        Customer customer = customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(referenceCustomerId, accountUuid);
        List<com.tansoflow.tansocore.entity.Entitlement> entitlements = entitlementRepository.findAllByCustomerAndRevokedAtIsNull(customer);

        Map<UUID, List<com.tansoflow.tansocore.entity.Entitlement>> grouped = new LinkedHashMap<>();
        for (com.tansoflow.tansocore.entity.Entitlement e : entitlements) {
            UUID subId = e.getSubscription() != null ? e.getSubscription().getId() : null;
            grouped.computeIfAbsent(subId, k -> new ArrayList<>()).add(e);
        }

        List<CustomerEntitlementsResponse.SubscriptionEntitlements> subscriptionList = new ArrayList<>();
        for (Map.Entry<UUID, List<com.tansoflow.tansocore.entity.Entitlement>> entry : grouped.entrySet()) {
            CustomerEntitlementsResponse.SubscriptionEntitlements sub = new CustomerEntitlementsResponse.SubscriptionEntitlements();
            sub.setSubscriptionId(entry.getKey());
            sub.setEntitlements(entry.getValue().stream().map(e -> {
                CustomerEntitlementsResponse.EntitlementSummary summary = new CustomerEntitlementsResponse.EntitlementSummary();
                summary.setFeatureKey(e.getFeatureKey());
                summary.setAllowed(true);
                return summary;
            }).collect(Collectors.toList()));
            subscriptionList.add(sub);
        }

        CustomerEntitlementsResponse response = new CustomerEntitlementsResponse();
        response.setReferenceCustomerId(referenceCustomerId);
        response.setSubscriptions(subscriptionList);
        return response;
    }

    private void handleEntitlement(EntitlementResponse entitlementResponse, boolean isEntitled, String denyReason) {
        EntitlementResponse.meta meta = new EntitlementResponse.meta();

        if (!isEntitled) {
            EntitlementResponse.meta.reason reason = new EntitlementResponse.meta.reason();
            reason.setDescription(denyReason);
            meta.setReason(reason);
            entitlementResponse.setMeta(meta);
            entitlementResponse.setAllowed(false);
        } else {
            entitlementResponse.setAllowed(true);
        }
    }

    private void populateUsage(EntitlementResponse response, UsageInfo usageInfo) {
        if (usageInfo != null) {
            EntitlementResponse.Usage usage = new EntitlementResponse.Usage();
            usage.setUsed(usageInfo.used());
            if (usageInfo.limit() != null) {
                usage.setLimit(usageInfo.limit());
                usage.setRemaining(usageInfo.limit().subtract(usageInfo.used()).max(BigDecimal.ZERO));
            }
            response.setUsage(usage);
        }
    }

    private record UsageInfo(boolean exceeded, BigDecimal used, BigDecimal limit, PricingModel pricingModel) {}

    private UsageInfo resolveUsageInfo(Customer customer, String featureKey, String accountUuid) {
        try {
            List<Subscription> subscriptions = subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId());
            Subscription activeSub = subscriptions.stream()
                    .filter(Subscription::getIsActive)
                    .findFirst()
                    .orElse(null);
            if (activeSub == null) return null;

            UUID featureId = featureRepository.findByKeyAndAccountId(featureKey, UUID.fromString(accountUuid))
                    .map(Feature::getId)
                    .orElse(null);
            if (featureId == null) return null;

            PlanFeatureRule rule = planFeatureRuleRepository.findPlanFeatureRuleByPlan_IdAndFeature_Id(
                    activeSub.getPlan().getId(), featureId);
            if (rule == null) return null;

            PricingModel pricingModel = RuleCalculationUtil.extractPricingModel(rule);
            if (pricingModel == null) return null;

            Instant since = pricingModel.isAccumulateMode()
                    ? activeSub.getCreatedAt()
                    : activeSub.getCurrentPeriodStart();
            BigDecimal cumulative = eventRepository.sumUsageUnitsByCustomerAndFeatureIdSince(
                    customer.getId(), featureId, since);
            boolean exceeded = RuleCalculationUtil.isMaxUsageExceeded(pricingModel, cumulative);
            return new UsageInfo(exceeded, cumulative, pricingModel.getMaxUsage(), pricingModel);
        } catch (Exception e) {
            log.error("Failed to resolve usage info for customer {} and feature {}: {}", customer.getId(), featureKey, e.getMessage());
            // Fail closed: report usage as exceeded so access is denied rather than granted on error
            return new UsageInfo(true, BigDecimal.ZERO, BigDecimal.ZERO, null);
        }
    }

    private EntitlementResponse.Credit resolveCreditInfo(Customer customer, String featureKey, String accountUuid) {
        try {
            List<Subscription> subscriptions = subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId());
            Subscription activeSub = subscriptions.stream()
                    .filter(Subscription::getIsActive)
                    .findFirst()
                    .orElse(null);
            if (activeSub == null) return null;

            UUID featureId = featureRepository.findByKeyAndAccountId(featureKey, UUID.fromString(accountUuid))
                    .map(Feature::getId)
                    .orElse(null);
            if (featureId == null) return null;

            PlanFeatureRule rule = planFeatureRuleRepository.findPlanFeatureRuleByPlan_IdAndFeature_Id(
                    activeSub.getPlan().getId(), featureId);
            if (rule == null || rule.getCreditModel() == null) return null;

            String denomination = rule.getCreditModel().getDenomination();
            List<CreditPoolDto> pools = creditService.getCreditPoolsByCustomer(customer.getId().toString(), accountUuid);
            if (pools == null || pools.isEmpty()) return null;

            BigDecimal balance = BigDecimal.ZERO;
            BigDecimal totalGranted = BigDecimal.ZERO;
            BigDecimal totalConsumed = BigDecimal.ZERO;
            boolean hasMatch = false;

            for (CreditPoolDto pool : pools) {
                if (denomination.equals(pool.getDenomination())) {
                    hasMatch = true;
                    balance = balance.add(pool.getBalance() != null ? pool.getBalance() : BigDecimal.ZERO);
                    totalGranted = totalGranted.add(pool.getTotalGranted() != null ? pool.getTotalGranted() : BigDecimal.ZERO);
                    totalConsumed = totalConsumed.add(pool.getTotalConsumed() != null ? pool.getTotalConsumed() : BigDecimal.ZERO);
                }
            }

            if (!hasMatch) return null;

            EntitlementResponse.Credit credit = new EntitlementResponse.Credit();
            credit.setDenomination(denomination);
            credit.setBalance(balance);
            credit.setTotalGranted(totalGranted);
            credit.setTotalConsumed(totalConsumed);
            credit.setHardLimit(rule.getCreditModel().getHardLimit());
            return credit;
        } catch (Exception e) {
            log.error("Failed to resolve credit info for customer {} and feature {}: {}",
                    customer.getId(), featureKey, e.getMessage());
            return null;
        }
    }

    private boolean isCreditHardLimitReached(Customer customer, String featureKey, String accountUuid) {
        try {
            List<Subscription> subscriptions = subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId());
            Subscription activeSub = subscriptions.stream()
                    .filter(Subscription::getIsActive)
                    .findFirst()
                    .orElse(null);
            if (activeSub == null) return false;

            UUID featureId = featureRepository.findByKeyAndAccountId(featureKey, UUID.fromString(accountUuid))
                    .map(Feature::getId)
                    .orElse(null);
            if (featureId == null) return false;

            PlanFeatureRule rule = planFeatureRuleRepository.findPlanFeatureRuleByPlan_IdAndFeature_Id(
                    activeSub.getPlan().getId(), featureId);
            if (rule == null || rule.getCreditModel() == null) return false;

            String denomination = rule.getCreditModel().getDenomination();
            return !creditService.checkHardLimitForSubscription(
                    activeSub.getId(), UUID.fromString(accountUuid), denomination, BigDecimal.ONE);
        } catch (Exception e) {
            log.error("Failed to check credit hard limit for customer {} and feature {}: {}",
                    customer.getId(), featureKey, e.getMessage());
            // Fail closed: assume limit reached on error
            return true;
        }
    }

}
