package com.tansoflow.tansocore.service.internal.monetization.implementation;

import com.tansoflow.tansocore.entity.AccountSetting;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.Invoice;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.entity.PlanCreditAllocation;
import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.entity.SubscriptionScheduledChange;
import com.tansoflow.tansocore.integration.stripe.StripeSyncService;
import com.tansoflow.tansocore.mapper.monetization.InvoiceMapper;
import com.tansoflow.tansocore.mapper.monetization.SubscriptionMapper;
import com.tansoflow.tansocore.mapper.monetization.SubscriptionScheduledChangeMapper;
import com.tansoflow.tansocore.model.api.external.StripeMode;
import com.tansoflow.tansocore.model.billing.CreateInvoiceParams;
import com.tansoflow.tansocore.model.billing.InvoiceDto;
import com.tansoflow.tansocore.model.billing.type.InvoiceStatus;
import com.tansoflow.tansocore.model.billing.type.InvoiceType;
import com.tansoflow.tansocore.model.event.service.InvoicePaidEvent;
import com.tansoflow.tansocore.model.event.service.SubscriptionActivatedEvent;
import com.tansoflow.tansocore.model.event.service.SubscriptionPlanChangedEvent;
import com.tansoflow.tansocore.model.event.service.SubscriptionCancelledEvent;
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.model.plan.BillingTiming;
import com.tansoflow.tansocore.model.plan.PlanStatus;
import com.tansoflow.tansocore.model.subscription.SubscriptionDto;
import com.tansoflow.tansocore.model.subscription.SubscriptionScheduledChangeDto;
import com.tansoflow.tansocore.model.subscription.SubscriptionScheduledChanges;
import com.tansoflow.tansocore.model.subscription.request.ClientSubscriptionRequest;
import com.tansoflow.tansocore.model.subscription.request.SubscriptionRequest;
import com.tansoflow.tansocore.model.subscription.response.SubscribedCustomerResponse;
import com.tansoflow.tansocore.model.subscription.type.CancelModes;
import com.tansoflow.tansocore.model.subscription.type.SubscriptionScheduledChangeStatus;
import com.tansoflow.tansocore.model.subscription.type.SubscriptionScheduledChangeType;
import com.tansoflow.tansocore.repository.PlanCreditAllocationRepository;
import com.tansoflow.tansocore.repository.SubscriptionRepository;
import com.tansoflow.tansocore.repository.SubscriptionScheduledChangeRepository;
import com.tansoflow.tansocore.service.internal.account.AccountService;
import com.tansoflow.tansocore.service.internal.account.implementation.CustomerServiceImpl;
import com.tansoflow.tansocore.service.internal.monetization.CreditService;
import com.tansoflow.tansocore.service.internal.monetization.EntitlementService;
import com.tansoflow.tansocore.service.internal.monetization.InvoiceService;
import com.tansoflow.tansocore.service.internal.monetization.PlanService;
import com.tansoflow.tansocore.service.internal.monetization.SubscriptionService;
import com.tansoflow.tansocore.util.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {
    private final static int MAX_PAGES_SAFETY_FACTOR = 10000;

    private final SubscriptionRepository subscriptionRepository;
    private final PlanService planService;
    private final CustomerServiceImpl customerService;
    private final SubscriptionMapper subscriptionMapper;
    private final SubscriptionScheduledChangeMapper subscriptionScheduledChangeMapper;
    private final EntitlementService entitlementService;
    private final InvoiceService invoiceService;
    private final SubscriptionScheduledChangeRepository subscriptionScheduledChangeRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final StripeSyncService stripeSyncService;
    private final AccountService accountService;
    private final InvoiceMapper invoiceMapper;
    private final CreditService creditService;
    private final PlanCreditAllocationRepository planCreditAllocationRepository;

    @Transactional
    @Override
    public SubscribedCustomerResponse clientSubscribeCustomer(ClientSubscriptionRequest request, String accountId) {
        Customer customer = customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(request.getCustomerReferenceId(), accountId);
        Plan plan = planService.retrievePlan(customer.getAccount(), UUID.fromString(request.getPlanId()));

        return subscribe(customer, plan, accountId);
    }

    @Transactional
    @Override
    public SubscribedCustomerResponse subscribeCustomer(SubscriptionRequest request, String accountId) {
        Customer customer = customerService.validateAndRetrieveCustomer(request.getCustomerId(), accountId);
        Plan plan = planService.retrievePlan(customer.getAccount(), UUID.fromString(request.getPlanId()));

        return subscribe(customer, plan, accountId);
    }

    @Transactional
    @Override
    public SubscribedCustomerResponse subscribe(Customer customer, Plan plan, String accountId) {
        if (!PlanStatus.ACTIVE.name().equals(plan.getStatus())) {
            throw new IllegalArgumentException("Cannot subscribe to plan: status is " + plan.getStatus() + ", only ACTIVE plans accept subscriptions");
        }

        // STRIPE_INTEGRATION and STRIPE_DRIVEN accounts: only one active subscription per customer (Stripe meters are customer-scoped)
        AccountSetting accountSetting = accountService.retrieveAccountSettings(accountId);
        if (accountSetting != null && (accountSetting.getStripeMode().isStripeIntegration() || accountSetting.getStripeMode() == StripeMode.STRIPE_DRIVEN)) {
            long activeCount = subscriptionRepository.findSubscriptionsByCustomer_Id(customer.getId())
                    .stream().filter(Subscription::getIsActive).count();
            if (activeCount >= 1) {
                throw new IllegalArgumentException(
                        "STRIPE_INTEGRATION accounts only support one active subscription per customer. " +
                        "Customer " + customer.getId() + " already has an active subscription.");
            }
        }

        SubscribedCustomerResponse response = new SubscribedCustomerResponse();

        // STRIPE_INTEGRATION + paid IN_ADVANCE: use Stripe Checkout Session instead of creating
        // a subscription upfront. No dangling subscriptions if the customer doesn't pay.
        // The customer.subscription.created webhook will create the Tanso subscription after checkout.
        boolean isStripeIntegration = accountSetting != null && accountSetting.getStripeMode().isStripeIntegration();
        if (isStripeIntegration
                && plan.getBillingTiming().equals(BillingTiming.IN_ADVANCE.name())
                && plan.getPriceAmount().compareTo(BigDecimal.ZERO) > 0) {
            try {
                var checkoutDto = stripeSyncService.createSubscriptionCheckoutSession(
                        UUID.fromString(accountId), customer.getId(), plan.getId());
                response.setCheckoutUrl(checkoutDto.getPaymentLink());
                return response;
            } catch (Exception e) {
                log.error("Failed to create Stripe checkout session for customer {} plan {}: {}",
                        customer.getId(), plan.getId(), e.getMessage(), e);
                throw new RuntimeException("Failed to create checkout session", e);
            }
        }

        Subscription subscription = createSubscription(customer.getId().toString(), plan.getId().toString(), accountId);

        LocalDate setTime = LocalDate.now();

        subscription.setCurrentPeriodStart(setTime
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant());

        subscription.setCurrentPeriodEnd(setTime
                .plusMonths(plan.getIntervalMonths())
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant());

        subscription.setBillingAnchorDay((short) setTime.getDayOfMonth());

        subscriptionRepository.saveAndFlush(subscription);

        InvoiceDto invoice = null;
        LocalDate dueDate;

        if (plan.getBillingTiming().equals(BillingTiming.IN_ADVANCE.name())) {
            dueDate = LocalDate.now();
            if (plan.getPriceAmount().compareTo(BigDecimal.ZERO) > 0) {
                // Paid IN_ADVANCE: subscription stays inactive until the initial invoice is paid.
                subscription.setIsActive(false);
                subscriptionRepository.saveAndFlush(subscription);
            }
            if (isStripeIntegration) {
                // STRIPE_INTEGRATION: Stripe auto-generates the invoice from the subscription.
                // The invoice.created webhook will mirror it back to Tanso.
                // For free plans, markInvoiceAsPaid is handled after the webhook mirrors the invoice.
            } else {
                invoice = invoiceService.createNewInvoice(subscription, dueDate, InvoiceStatus.DUE, InvoiceType.IN_ADVANCE_INITIAL);
                if (plan.getPriceAmount().compareTo(BigDecimal.ZERO) == 0) {
                    log.info("Plan {} is free, marking invoice as paid and granting entitlements immediately for subscription {}", plan.getId(), subscription.getId());
                    Invoice invoiceEntity = invoiceService.retrieveInvoiceByInvoiceIdAndAccount(invoice.getId(), accountId);
                    invoiceService.markInvoiceAsPaid(invoiceEntity);
                }
            }
        } else {
            // IN_ARREARS
            if (isStripeIntegration) {
                // STRIPE_INTEGRATION: Stripe auto-generates invoices from the subscription.
                // The invoice.created webhook will mirror it back to Tanso.
            } else {
                LocalDate baseDueDate = LocalDate.now().plusMonths(plan.getIntervalMonths());
                short anchorDay = subscription.getBillingAnchorDay();
                dueDate = DateUtils.calculateNextBillingDate(baseDueDate, anchorDay);
                invoice = invoiceService.createNewInvoice(subscription, dueDate, InvoiceStatus.PENDING);
            }
            entitlementService.processEntitlementsForSubscription(subscription);
            creditService.processCreditGrantsForSubscription(subscription);
        }

        response.setSubscription(subscriptionMapper.subscriptionEntityToSubscriptionDto(subscription));
        response.setInvoice(invoice);

        // Publish event for Stripe subscription creation.
        // For paid IN_ADVANCE plans, defer until the initial invoice is paid — EXCEPT for STRIPE_INTEGRATION,
        // where we create the Stripe subscription immediately so its auto-generated invoice
        // becomes the payment vehicle (avoids duplicate standalone invoices).
        boolean paidInAdvance = plan.getBillingTiming().equals(BillingTiming.IN_ADVANCE.name())
                && plan.getPriceAmount().compareTo(BigDecimal.ZERO) > 0;
        if (!paidInAdvance || isStripeIntegration) {
            eventPublisher.publishEvent(new SubscriptionActivatedEvent(
                    subscription.getAccount().getId(), subscription.getId()));
        }

        return response;
    }

    @Override
    public Subscription createSubscription(String customerUuid, String planUuid, String accountId) {
        Subscription subscription = new Subscription();
        Customer customer = customerService.validateAndRetrieveCustomer(customerUuid, accountId);
        Plan plan = planService.retrievePlan(customer.getAccount(), UUID.fromString(planUuid));

        subscription.setCustomer(customer);
        subscription.setPlan(plan);
        subscription.setIntervalMonths(plan.getIntervalMonths());
        subscription.setAccount(customer.getAccount());
        subscription.setIsActive(true);
        log.info("Saved subscription for customer {} and plan {}", customerUuid, planUuid);

        return subscription;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionDto> getSubscriptionsByCustomer(String customerUuid, String accountId) {
        Customer customer = customerService.validateAndRetrieveCustomer(customerUuid, accountId);

        List<Subscription> subscriptions = subscriptionRepository.findSubscriptionsByCustomer(customer);
        List<SubscriptionDto> subscriptionDtos = subscriptionMapper.subscriptionEntityListToSubscriptionDtoList(subscriptions);

        // Exit early if no scheduled changes exist
        if (!subscriptionScheduledChangeRepository.existsSubscriptionScheduledChangeBySubscriptionIn(subscriptions)) {
            return subscriptionDtos;
        }

        // Fetch all scheduled changes for these subscriptions
        List<SubscriptionScheduledChange> changes =
                subscriptionScheduledChangeRepository
                        .findSubscriptionScheduledChangesByStatusAndSubscriptionIsIn(SubscriptionScheduledChangeStatus.PENDING.name(), subscriptions);

        // Build lookup table: subscriptionId -> list of changes
        Map<UUID, List<SubscriptionScheduledChange>> changesBySubscription = changes.stream()
                .collect(Collectors.groupingBy(sc -> sc.getSubscription().getId()));

        // Enrich DTOs
        for (SubscriptionDto dto : subscriptionDtos) {
            UUID dtoId = UUID.fromString(dto.getId());

            List<SubscriptionScheduledChange> dtoChanges = changesBySubscription.get(dtoId);
            if (dtoChanges == null || dtoChanges.isEmpty()) {
                continue;
            }

            // Set the primary scheduled change (usually only one pending at a time)
            dto.setScheduledChange(subscriptionScheduledChangeMapper.toDto(dtoChanges.getFirst()));

            // Convert entity → your outbound model (Keep metadata hack for backward compatibility if needed)
            List<SubscriptionScheduledChanges> serializedChanges = dtoChanges.stream()
                    .map(sc -> {
                        SubscriptionScheduledChanges c = new SubscriptionScheduledChanges();
                        c.setChangeStatus(sc.getStatus());
                        c.setEffectiveAt(sc.getEffectiveAt().toString());
                        c.setChangeType(sc.getType());
                        return c;
                    })
                    .toList();

            // Attach metadata
            if (dto.getMetadata() == null) {
                dto.setMetadata(new HashMap<>());
            }

            dto.getMetadata().put("SubscriptionScheduledChanges", Collections.singletonList(serializedChanges));
        }

        return subscriptionDtos;
    }


    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionDto> getSubscriptionsByAccount(String accountId) {
        List<Customer> customers = customerService.retrieveCustomersByAccountId(accountId);
        List<Subscription> subscriptions = subscriptionRepository.findSubscriptionsByCustomerIn(customers);
        return subscriptionMapper.subscriptionEntityListToSubscriptionDtoList(subscriptions);
    }

    @Override
    @Transactional
    public void deleteSubscription(Subscription subscription) {
        Customer customer = subscription.getCustomer();
        entitlementService.deleteEntitlementsBySubscriptionAndCustomer(customer, subscription);
        subscriptionRepository.delete(subscription);
        log.info("Deleted subscription for customer {} and plan {}", customer.getId(), subscription.getPlan().getId());
    }

    @Override
    @Transactional
    public SubscriptionDto editSubscriptionById(SubscriptionRequest request, String subscriptionId, String accountId) {
        Subscription subscription = getSubscriptionById(subscriptionId, accountId);
        subscriptionMapper.updateSubscriptionEntity(request, subscription);
        subscriptionRepository.save(subscription);
        return subscriptionMapper.subscriptionEntityToSubscriptionDto(subscription);
    }

    @Override
    @Transactional(readOnly = true)
    public Subscription getSubscriptionById(String subscriptionId, String accountId) {
        Subscription subscription = subscriptionRepository.findById(UUID.fromString(subscriptionId))
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found with id: " + subscriptionId));
        if (!subscription.getCustomer().getAccount().getId().toString().equals(accountId)) {
            throw new ResourceNotFoundException("Subscription " + subscriptionId + " not found for account " + accountId);
        }
        return subscription;
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionDto getSubscriptionDtoById(String subscriptionId, String accountId) {
        Subscription subscription = getSubscriptionById(subscriptionId, accountId);
        return subscriptionMapper.subscriptionEntityToSubscriptionDto(subscription);
    }

    /**
     * Cancels the subscription with the specified cancellation mode. The cancellation can either
     * be immediate or at the end of the current billing period. This operation updates the
     * subscription status, processes required entitlement changes, and adjusts invoices as needed.
     *
     * @param subscriptionId the unique identifier of the subscription to be canceled
     * @param cancelMode     the mode of cancellation (e.g., "IMMEDIATE" or "END_OF_PERIOD"); if null or empty, defaults to "END_OF_PERIOD"
     * @param accountId      the unique identifier of the account associated with the subscription
     * @throws ResourceNotFoundException if no subscription is found with the provided subscription ID and account ID
     */
    @Transactional
    @Override
    public void cancelSubscription(String subscriptionId, String cancelMode, String accountId) {
        Subscription subscription = subscriptionRepository.findSubscriptionByUuidAndAccountId(UUID.fromString(subscriptionId), UUID.fromString(accountId));
        Instant instantNow = Instant.now();

        if (subscription == null) {
            throw new ResourceNotFoundException("Subscription not found with id: " + subscriptionId);
        }

        subscription.setCancelledAt(instantNow);

        if (cancelMode == null || cancelMode.isBlank()) {
            cancelMode = "END_OF_PERIOD";
        }

        switch (CancelModes.valueOf(cancelMode)) {
            case IMMEDIATE -> {
                Instant originalPeriodEnd = subscription.getCurrentPeriodEnd();
                subscription.setCurrentPeriodEnd(instantNow);
                subscription.setCancelMode(CancelModes.IMMEDIATE.name());
                subscription.setCancelEffectiveAt(instantNow);
                subscription.setIsActive(false);

                // Void any outstanding DUE/PENDING invoices for the current period
                invoiceService.voidOutstandingInvoicesForSubscription(subscription);

                // For IN_ADVANCE billing, create a prorated credit since the customer already paid
                if (BillingTiming.IN_ADVANCE.name().equals(subscription.getPlan().getBillingTiming())) {
                    long totalMillis = Duration.between(subscription.getCurrentPeriodStart(), originalPeriodEnd).toMillis();
                    long usedMillis = Duration.between(subscription.getCurrentPeriodStart(), instantNow).toMillis();
                    if (totalMillis > 0 && usedMillis < totalMillis) {
                        BigDecimal unusedRatio = BigDecimal.valueOf(totalMillis - usedMillis)
                                .divide(BigDecimal.valueOf(totalMillis), 8, RoundingMode.HALF_UP);
                        BigDecimal creditAmount = unusedRatio
                                .multiply(subscription.getPlan().getPriceAmount())
                                .setScale(2, RoundingMode.HALF_UP)
                                .negate();

                        if (creditAmount.compareTo(BigDecimal.ZERO) < 0) {
                            invoiceService.createCreditInvoice(subscription, creditAmount, instantNow, originalPeriodEnd);
                        }
                    }
                }

                entitlementService.processEntitlementRevokeForSubscription(subscription);

                // Claw back remaining PLAN_INCLUDED credits (purchased/promotional survive)
                try {
                    creditService.clawBackPlanIncludedCredits(subscription.getId(), subscription.getAccount().getId());
                } catch (Exception e) {
                    log.error("Failed to claw back credits for subscription {}: {}", subscription.getId(), e.getMessage(), e);
                }
            }
            case END_OF_PERIOD -> {
                subscription.setCancelEffectiveAt(subscription.getCurrentPeriodEnd());
                subscription.setCancelMode(CancelModes.END_OF_PERIOD.name());
            }
        }

        subscriptionRepository.save(subscription);

        eventPublisher.publishEvent(new SubscriptionCancelledEvent(
                subscription.getAccount().getId(), subscription.getId(), cancelMode));
    }

    @Transactional
    @Override
    public void activateSubscription(String subscriptionId, String accountId) {
        Subscription subscription = getSubscriptionById(subscriptionId, accountId);

        if (subscription.getIsActive()) {
            throw new IllegalArgumentException("Subscription " + subscriptionId + " is already active");
        }

        Invoice initialInvoice = invoiceService.retrieveInitialInvoiceForSubscription(subscription);
        if (initialInvoice == null) {
            throw new IllegalArgumentException("No initial invoice found for subscription " + subscriptionId);
        }

        // markInvoiceAsPaid handles: setting isActive=true, granting entitlements, credits, and publishing SubscriptionActivatedEvent
        invoiceService.markInvoiceAsPaid(initialInvoice);
        log.info("Activated subscription {} via initial invoice {}", subscriptionId, initialInvoice.getId());
    }

    /**
     * Handles the payment of a subscription invoice by updating the subscription's current period
     * and marking the invoice as paid.
     *
     * @param invoiceId the unique identifier of the invoice that has been paid
     * @param accountId the unique identifier of the account associated with the invoice
     * @throws ResourceNotFoundException if the subscription associated with the invoice cannot be found
     */
    @Transactional
    @Override
    public void subscriptionInvoicePaid(String invoiceId, String accountId) {
        Invoice invoice = invoiceService.retrieveInvoiceByInvoiceIdAndAccount(invoiceId, accountId);
        Subscription subscription = invoice.getSubscription();

        if (subscription == null) {
            throw new ResourceNotFoundException("Subscription not found for invoice id: " + invoiceId);
        }

        switch (invoice.getSubscription().getPlan().getBillingTiming()) {
            case "IN_ADVANCE" -> {
                subscription.setCurrentPeriodStart(invoice.getInvoicePeriodStart());
                subscription.setCurrentPeriodEnd(invoice.getInvoicePeriodEnd());
            }
            case "IN_ARREARS" -> {
                Instant newPeriodStart = invoice.getInvoicePeriodEnd();
                Instant newPeriodEnd = newPeriodStart
                        .atOffset(ZoneOffset.UTC)
                        .plusMonths(subscription.getIntervalMonths())
                        .toInstant();

                subscription.setCurrentPeriodStart(newPeriodStart);
                subscription.setCurrentPeriodEnd(newPeriodEnd);
            }

        }
        invoiceService.markInvoiceAsPaid(invoice);
        subscriptionRepository.save(subscription);

        eventPublisher.publishEvent(new InvoicePaidEvent(UUID.fromString(accountId)
                , UUID.fromString(invoiceId)));
    }

    @Override
    @Transactional
    public void processSubscriptionCycles() {
        int pageCount = 0;
        Pageable pageable = PageRequest.of(0, 500);

        while (true) {
            Page<Subscription> page =
                    subscriptionRepository.findActiveNeedingRollover(Instant.now(), pageable);

            if (page.isEmpty()) {
                break;
            }

            for (Subscription sub : page.getContent()) {
                try {
                    processSingleSubscriptionCycle(sub.getId());
                } catch (Exception e) {
                    log.error("Failed cycle rollover for subscription {} {}", sub.getId(), e.getMessage(), e);
                }
            }

            if (!page.hasNext()) {
                break;
            }

            if (pageCount++ >= MAX_PAGES_SAFETY_FACTOR) {
                log.error("Aborting cycle job: exceeded maxPages={}", MAX_PAGES_SAFETY_FACTOR);
                break;
            }

            pageable = page.nextPageable();
        }
    }

    @Transactional
    public void processSingleSubscriptionCycle(UUID subscriptionId) {
        Instant now = Instant.now();

        // Use findById with a lock or ensure the query is fresh
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalStateException("Subscription not found: " + subscriptionId));

        // 1) verify still needs rollover (job might have picked stale data or another node might have processed it). 
        if (!Boolean.TRUE.equals(subscription.getIsActive())
                || subscription.getCancelledAt() != null
                || subscription.getCurrentPeriodEnd().isAfter(now)) {
            return;
        }

        boolean hasPastDue = invoiceService.hasPastDueInvoice(subscription);
        if (hasPastDue) {
            log.info("Skipping rollover for subscription {} because it has a PAST_DUE invoice",
                    subscription.getId());
            return;
        }

        // At this point:
        // - scheduled downgrades should have already been applied by processScheduledDowngrades()
        // - plan reflects the correct plan for the *next* period
        Plan plan = subscription.getPlan();

        // 3) Compute next period
        Instant newPeriodStart = subscription.getCurrentPeriodEnd();
        Instant newPeriodEnd = newPeriodStart
                .atOffset(ZoneOffset.UTC)
                .plusMonths(plan.getIntervalMonths())
                .toInstant();

        subscription.setCurrentPeriodStart(newPeriodStart);
        subscription.setCurrentPeriodEnd(newPeriodEnd);

        // 4) Avoid double invoices if job reruns:
        //    check if there is already an invoice for [newPeriodStart, newPeriodEnd]
        boolean invoiceExists = invoiceService.existsInvoiceForPeriod(subscription, newPeriodStart, newPeriodEnd);
        if (!invoiceExists) {
            // IN_ADVANCE vs IN_ARREARS drives initial status and due date
            LocalDate dueDate;
            InvoiceStatus initialStatus;

            if (BillingTiming.IN_ADVANCE.name().equals(plan.getBillingTiming())) {
                // invoice for upcoming period, due now (or at newPeriodStart if you prefer)
                dueDate = LocalDate.now(ZoneOffset.UTC);
                initialStatus = InvoiceStatus.DUE;
            } else {
                // IN_ARREARS: invoice will be due at the end of the period that will just start
                LocalDate baseDueDate = newPeriodEnd.atOffset(ZoneOffset.UTC).toLocalDate();
                short anchorDay = subscription.getBillingAnchorDay();
                dueDate = DateUtils.calculateNextBillingDate(baseDueDate, anchorDay);
                initialStatus = InvoiceStatus.PENDING;
            }

            String currency = plan.getCurrency() != null ? plan.getCurrency() : "USD";
            CreateInvoiceParams params = new CreateInvoiceParams(subscription,
                    dueDate,
                    initialStatus,
                    newPeriodStart,
                    newPeriodEnd,
                    subscription.getPlan().getPriceAmount(),
                    currency,
                    InvoiceType.REGULAR);

            Invoice nextInvoice = invoiceService.createNewInvoice(params);
            invoiceMapper.invoiceEntityToInvoiceDto(nextInvoice);

            // Sync to Stripe if enabled (only for PAYMENT_PASS_THROUGH; STRIPE_INTEGRATION is excluded by the query)
            AccountSetting accountSetting = accountService.retrieveAccountSettings(subscription.getAccount().getId().toString());
            if (accountSetting != null && accountSetting.isStripeEnabled()) {
                try {
                    stripeSyncService.syncNewInvoice(nextInvoice.getId(), subscription.getAccount().getId());
                } catch (Exception e) {
                    log.error("Failed to sync new invoice to stripe for subscription {}: {}", subscription.getId(), e.getMessage(), e);
                }
            }

            if (initialStatus == InvoiceStatus.DUE && plan.getPriceAmount().compareTo(BigDecimal.ZERO) == 0) {
                log.info("Rollover: Plan {} is free, auto-paying invoice for subscription {}", plan.getId(), subscription.getId());
                // Find the invoice we just created.
                // markInvoiceAsPaid handles entitlements and credit grants
                if (nextInvoice != null) {
                    invoiceService.markInvoiceAsPaid(nextInvoice);
                }
            }
        }

        // Credit rollover + re-grant for the new period
        // For IN_ARREARS: credits need to be granted here since there is no invoice payment trigger
        // For IN_ADVANCE free plans: already handled by markInvoiceAsPaid above
        // For IN_ADVANCE paid plans: will be handled when the invoice is paid externally
        if (BillingTiming.IN_ARREARS.name().equals(plan.getBillingTiming())) {
            try {
                creditService.applyRolloverPoliciesForSubscription(subscription.getId());
                creditService.processCreditGrantsForSubscription(subscription);
            } catch (Exception e) {
                log.error("Failed to process credit grants on rollover for subscription {}: {}",
                        subscription.getId(), e.getMessage(), e);
            }
        }

        subscriptionRepository.save(subscription);
    }


    @Override
    @Transactional
    public void processCancellations() {
        List<Subscription> expiredSubs = subscriptionRepository.findExpiredSubscriptionsForCancellation();
        for (Subscription sub : expiredSubs) {
            try {
                creditService.clawBackPlanIncludedCredits(sub.getId(), sub.getAccount().getId());
            } catch (Exception e) {
                log.error("Failed to claw back credits for subscription {}: {}", sub.getId(), e.getMessage(), e);
            }
            sub.setIsActive(false);
            if (sub.getCancelledAt() == null) {
                sub.setCancelledAt(Instant.now());
            }
            subscriptionRepository.save(sub);
        }
        log.info("Deactivated {} expired subscriptions with credit clawback", expiredSubs.size());
    }

    /**
     * Upgrades the user's current subscription plan to a new plan, adjusting for any applicable proration
     * based on the remaining time in the current billing period.
     *
     * @param currentSubscriptionId the current subscription object containing details of the user's active subscription
     * @param newPlanId             the new plan to which the subscription should be upgraded
     */
    @Transactional
    @Override
    public void upgradeSubscription(String currentSubscriptionId, String accountId, String newPlanId, boolean grantNow) {
        Subscription currentSubscription = subscriptionRepository
                .findSubscriptionByUuidAndAccountId(UUID.fromString(currentSubscriptionId), UUID.fromString(accountId));
        Plan subscribedPlan = currentSubscription.getPlan();
        Plan newPlan = planService.retrievePlan(currentSubscription.getAccount(), UUID.fromString(newPlanId));
        if (!PlanStatus.ACTIVE.name().equals(newPlan.getStatus())) {
            throw new IllegalArgumentException("Cannot subscribe to plan: status is " + newPlan.getStatus() + ", only ACTIVE plans accept subscriptions");
        }
        SubscriptionScheduledChange scheduledChange = new SubscriptionScheduledChange();
        Instant now = Instant.now();

        long totalTimeMills = Duration.between(currentSubscription.getCurrentPeriodStart(),
                currentSubscription.getCurrentPeriodEnd()).toMillis();
        long remainingTimeMills = Duration.between(now,
                currentSubscription.getCurrentPeriodEnd()).toMillis();

        BigDecimal ratio = BigDecimal.valueOf(remainingTimeMills)
                .divide(BigDecimal.valueOf(totalTimeMills), 8, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO)
                .min(BigDecimal.ONE);

        ratio = ratio.max(BigDecimal.ZERO).min(BigDecimal.ONE);

        AccountSetting upgAccountSetting = accountService.retrieveAccountSettings(accountId);
        boolean isStripeIntegrationUpgrade = upgAccountSetting != null && upgAccountSetting.getStripeMode().isStripeIntegration();

        if (grantNow) {
            if (isStripeIntegrationUpgrade) {
                // STRIPE_INTEGRATION: defer plan swap until proration invoice is paid.
                // Stripe handles proration via CREATE_PRORATIONS. The invoice.paid webhook
                // will fulfill this upgrade (swap plan, grant entitlements, credits).
                cancelScheduledChangesForSubscription(currentSubscription.getId(), currentSubscription.getAccount().getId());
                scheduledChange.setStatus(SubscriptionScheduledChangeStatus.PENDING.name());
                scheduledChange.setType(SubscriptionScheduledChangeType.UPGRADE.name());
                scheduledChange.setEffectiveAt(now);
                scheduledChange.setSubscription(currentSubscription);
                scheduledChange.setFromPlan(subscribedPlan);
                scheduledChange.setToPlan(newPlan);
                subscriptionScheduledChangeRepository.save(scheduledChange);
            } else if (subscribedPlan.getBillingTiming().equals(BillingTiming.IN_ADVANCE.name())) {
                if (newPlan.getBillingTiming().equals(BillingTiming.IN_ADVANCE.name())) {
                    Invoice adjustedInvoice = invoiceService.createAdjustmentInvoice(subscribedPlan, newPlan, currentSubscription, ratio, now);

                    currentSubscription.setPlan(newPlan);
                    subscriptionRepository.save(currentSubscription);

                    entitlementService.processEntitlementsForSubscription(currentSubscription);
                    grantCreditDeltaForUpgrade(subscribedPlan, newPlan, currentSubscription);

                    scheduledChange.setStatus(SubscriptionScheduledChangeStatus.COMPLETED.name());
                    scheduledChange.setType(SubscriptionScheduledChangeType.UPGRADE.name());
                    scheduledChange.setEffectiveAt(now);
                    scheduledChange.setSubscription(currentSubscription);
                    scheduledChange.setFromPlan(subscribedPlan);
                    scheduledChange.setToPlan(newPlan);
                    scheduledChange.setAdjustmentInvoice(adjustedInvoice);
                    scheduledChange.setFulfilledAt(now);

                    subscriptionScheduledChangeRepository.save(scheduledChange);
                    cancelScheduledChangesForSubscription(currentSubscription.getId(), currentSubscription.getAccount().getId());
                }
            } else if (subscribedPlan.getBillingTiming().equals(BillingTiming.IN_ARREARS.name())) {
                    currentSubscription.setPlan(newPlan);
                    subscriptionRepository.save(currentSubscription);

                    entitlementService.processEntitlementsForSubscription(currentSubscription);
                    grantCreditDeltaForUpgrade(subscribedPlan, newPlan, currentSubscription);

                    scheduledChange.setStatus(SubscriptionScheduledChangeStatus.COMPLETED.name());
                    scheduledChange.setType(SubscriptionScheduledChangeType.UPGRADE.name());
                    scheduledChange.setEffectiveAt(now);
                    scheduledChange.setSubscription(currentSubscription);
                    scheduledChange.setFromPlan(subscribedPlan);
                    scheduledChange.setToPlan(newPlan);
                    scheduledChange.setFulfilledAt(now);

                    subscriptionScheduledChangeRepository.save(scheduledChange);
                    cancelScheduledChangesForSubscription(currentSubscription.getId(), currentSubscription.getAccount().getId());
            }
        }

        // Notify Stripe of the plan change so the subscription price is updated.
        // Upgrades happen mid-cycle and should prorate; downgrades are scheduled at period end.
        eventPublisher.publishEvent(new SubscriptionPlanChangedEvent(
                currentSubscription.getAccount().getId(), currentSubscription.getId(), true));
    }

    /**
     * Compares credit allocations between old and new plans, and grants the delta
     * for any denomination where the new plan provides more credits.
     * Existing unused credits are kept — only the difference is topped up.
     */
    private void grantCreditDeltaForUpgrade(Plan oldPlan, Plan newPlan, Subscription subscription) {
        List<PlanCreditAllocation> oldAllocations = planCreditAllocationRepository.findByPlanIdAndDeletedAtIsNull(oldPlan.getId());
        List<PlanCreditAllocation> newAllocations = planCreditAllocationRepository.findByPlanIdAndDeletedAtIsNull(newPlan.getId());

        Map<String, BigDecimal> oldAmounts = oldAllocations.stream()
                .collect(Collectors.toMap(a -> a.getCreditModel().getDenomination(), PlanCreditAllocation::getCreditAmount));
        Map<String, BigDecimal> newAmounts = newAllocations.stream()
                .collect(Collectors.toMap(a -> a.getCreditModel().getDenomination(), PlanCreditAllocation::getCreditAmount));

        UUID accountId = subscription.getAccount().getId();

        for (var entry : newAmounts.entrySet()) {
            String denom = entry.getKey();
            BigDecimal newAmount = entry.getValue();
            BigDecimal oldAmount = oldAmounts.getOrDefault(denom, BigDecimal.ZERO);
            BigDecimal delta = newAmount.subtract(oldAmount);

            if (delta.compareTo(BigDecimal.ZERO) > 0) {
                creditService.grantDeltaCredits(subscription, denom, delta, accountId);
                log.info("Upgrade credit delta: +{} {} for subscription {}", delta, denom, subscription.getId());
            }
            // If delta <= 0 (downgrade), do nothing — keep existing credits, grant fewer next cycle
        }
        // Denominations in old but NOT in new → do nothing (keep existing pool, just stop granting next cycle)
    }

    @Override
    @Transactional
    public void scheduleDowngradeSubscription(String currentSubscriptionId, String accountId, String changePlan) {
        Subscription subscription = subscriptionRepository.findSubscriptionByUuidAndAccountId(UUID.fromString(currentSubscriptionId), UUID.fromString(accountId));
        Plan newPlan = planService.retrievePlan(subscription.getAccount(), UUID.fromString(changePlan));
        if (!PlanStatus.ACTIVE.name().equals(newPlan.getStatus())) {
            throw new IllegalArgumentException("Cannot subscribe to plan: status is " + newPlan.getStatus() + ", only ACTIVE plans accept subscriptions");
        }

        if (subscription.getPlan().equals(newPlan)) {
            throw new IllegalArgumentException("Cannot downgrade to the same plan");
        }
        cancelScheduledChangesForSubscription(subscription.getId(), subscription.getAccount().getId());

        SubscriptionScheduledChange scheduledChange = new SubscriptionScheduledChange();
        scheduledChange.setSubscription(subscription);
        scheduledChange.setFromPlan(subscription.getPlan());
        scheduledChange.setToPlan(newPlan);
        scheduledChange.setStatus(SubscriptionScheduledChangeStatus.PENDING.name());
        scheduledChange.setType(SubscriptionScheduledChangeType.DOWNGRADE.name());
        scheduledChange.setEffectiveAt(subscription.getCurrentPeriodEnd());

        subscriptionScheduledChangeRepository.save(scheduledChange);
    }

    @Override
    public void processScheduledDowngrades() {
        int pageCount = 0;
        Pageable pageable = PageRequest.of(0, 500);

        while (true) {
            Page<SubscriptionScheduledChange> page =
                    subscriptionScheduledChangeRepository.findScheduledSubscriptionsForDowngrade(Instant.now(), pageable);

            if (page.isEmpty()) {
                break; // termination immediately since we have none to process
            }

            for (SubscriptionScheduledChange ssc : page.getContent()) {
                try {
                    downgradeSubscription(ssc);
                } catch (Exception e) {
                    log.error("Failed downgrade for ssc {} {}", ssc.getId(), e.getMessage(), e);
                }
            }

            if (!page.hasNext()) {
                break; // we just processed the last page
            }

            if (pageCount++ >= MAX_PAGES_SAFETY_FACTOR) {
                log.error("Aborting downgrade job: exceeded maxPages={}", MAX_PAGES_SAFETY_FACTOR);
                break;
            }

            pageable = page.nextPageable();
        }
    }

    @Transactional
    @Override
    public void cancelScheduledChangesForSubscription(UUID subscriptionId, UUID accountId) {
        Subscription subscription = subscriptionRepository.findSubscriptionByUuidAndAccountId(subscriptionId, accountId);
        if (subscription == null) {
            log.warn("Subscription not found with id: {} and account id: {}", subscriptionId, accountId);
            return;
        }
        subscriptionScheduledChangeRepository.cancelAllScheduledChanges(subscription);
    }

    @Override
    public void cancelScheduledSubscriptionCancellation(String subscriptionId, String accountId) {
        Subscription subscription = subscriptionRepository.findSubscriptionByUuidAndAccountId(UUID.fromString(subscriptionId), UUID.fromString(accountId));
        if (subscription == null) {
            throw new IllegalArgumentException("Subscription not found with id: " + subscriptionId);
        }

        if (subscription.getIsActive() == false) {
            throw new IllegalArgumentException("Subscription is already cancelled");
        }

        subscription.setCancelMode(null);
        subscription.setCancelEffectiveAt(null);
        subscription.setCancelledAt(null);
        subscriptionRepository.save(subscription);
    }

    @Override
    public List<SubscriptionScheduledChangeDto> getScheduledChangesByAccount(String accountId) {
        List<SubscriptionScheduledChange> changes = subscriptionScheduledChangeRepository.findAllPendingChangesByAccountId(UUID.fromString(accountId));
        return subscriptionScheduledChangeMapper.toDtoList(changes);
    }

    @Override
    public List<SubscriptionDto> getScheduledCancellationsByAccount(String accountId) {
        List<Subscription> subscriptions = subscriptionRepository.findAllScheduledCancellationsByAccountId(UUID.fromString(accountId));
        return subscriptionMapper.subscriptionEntityListToSubscriptionDtoList(subscriptions);
    }

    private void downgradeSubscription(SubscriptionScheduledChange ssc) {
        UUID changeId = ssc.getId();
        Instant now = Instant.now();

        if (ssc.getSubscription().getPlan().equals(ssc.getToPlan())) {
            // already changed mark as CANCELLED
            return;
        }

        // 1) Type & status guard rails
        if (!Objects.equals(ssc.getType(), SubscriptionScheduledChangeType.DOWNGRADE.name())) {
            log.warn("SSC {} is not a DOWNGRADE (type={}), skipping.", changeId, ssc.getType());
            return;
        }

        if (Objects.equals(ssc.getStatus(), SubscriptionScheduledChangeStatus.COMPLETED.name())
                || Objects.equals(ssc.getStatus(), SubscriptionScheduledChangeStatus.CANCELLED.name())) {
            log.debug("SSC {} already terminal (status={}), skipping.", changeId, ssc.getStatus());
            return;
        }

        if (ssc.getEffectiveAt().isAfter(now)) {
            log.debug("SSC {} effective_at {} is in the future, skipping.", changeId, ssc.getEffectiveAt());
            return; // extra safety; shouldn't happen with your query, but harmless
        }

        Subscription subscription = ssc.getSubscription();

        // Void outstanding invoices at the old plan price before swapping
        invoiceService.voidOutstandingInvoicesForSubscription(subscription);

        subscription.setPlan(ssc.getToPlan());
        subscriptionRepository.save(subscription);

        // Reconcile entitlements to match the new (downgraded) plan
        entitlementService.processEntitlementsForSubscription(subscription);

        ssc.setStatus(SubscriptionScheduledChangeStatus.COMPLETED.name());
        ssc.setFulfilledAt(now);
        subscriptionScheduledChangeRepository.save(ssc);

        // Notify Stripe of the plan change. Downgrades happen at period boundary — no proration.
        eventPublisher.publishEvent(new SubscriptionPlanChangedEvent(
                subscription.getAccount().getId(), subscription.getId(), false));
    }

}
