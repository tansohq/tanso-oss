package com.tansoflow.tansocore.service.internal.monetization.implementation;

import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.Event;
import com.tansoflow.tansocore.entity.Invoice;
import com.tansoflow.tansocore.entity.InvoiceItem;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.entity.PlanFeatureRule;
import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.mapper.monetization.InvoiceMapper;
import com.tansoflow.tansocore.model.billing.CreateInvoiceParams;
import com.tansoflow.tansocore.model.billing.InvoiceDto;
import com.tansoflow.tansocore.model.billing.InvoiceItemDto;
import com.tansoflow.tansocore.model.billing.type.InvoiceStatus;
import com.tansoflow.tansocore.model.billing.type.InvoiceType;
import com.tansoflow.tansocore.model.event.events.type.EventType;
import com.tansoflow.tansocore.model.event.service.InvoiceCreatedEvent;
import com.tansoflow.tansocore.model.monetization.pricing.GraduatedPricingModel;
import com.tansoflow.tansocore.model.monetization.pricing.PricingModel;
import com.tansoflow.tansocore.repository.EventRepository;
import com.tansoflow.tansocore.repository.InvoiceItemRepository;
import com.tansoflow.tansocore.repository.InvoiceRepository;
import com.tansoflow.tansocore.repository.PlanFeatureRuleRepository;
import com.tansoflow.tansocore.repository.SubscriptionRepository;
import com.tansoflow.tansocore.service.internal.account.CustomerService;
import com.tansoflow.tansocore.service.internal.monetization.CreditService;
import com.tansoflow.tansocore.service.internal.monetization.EntitlementService;
import com.tansoflow.tansocore.service.internal.monetization.InvoiceService;
import com.tansoflow.tansocore.util.monetization.RuleCalculationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

@Service
@Slf4j
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {
    private final InvoiceRepository invoiceRepository;
    private final InvoiceMapper invoiceMapper;
    private final CustomerService customerService;
    private final SubscriptionRepository subscriptionRepository;
    private final EntitlementService entitlementService;
    private final CreditService creditService;
    private final InvoiceItemRepository invoiceItemRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final EventRepository eventRepository;
    private final PlanFeatureRuleRepository planFeatureRuleRepository;

    @Transactional
    @Override
    public Invoice createNewInvoice(CreateInvoiceParams invoiceParams) {
        Subscription sub = requireNonNull(invoiceParams.subscription(), "subscription");
        requireNonNull(invoiceParams.dueDate(), "dueDate");
        requireNonNull(invoiceParams.status(), "status");
        requireNonNull(invoiceParams.periodStart(), "periodStart");
        requireNonNull(invoiceParams.periodEnd(), "periodEnd");

        // We use a list to collect usage items since buildInvoice is now calling calculateAndAddUsageCosts
        // which could be modified to return them, but for now let's handle it by re-calculating or 
        // passing them through a context if needed.
        // Actually, let's keep it simple: calculateAndAddUsageCosts will be called by buildInvoice.
        // We'll need a way to save the items.
        
        Invoice invoice = buildInvoice(invoiceParams);
        Invoice saved = invoiceRepository.saveAndFlush(invoice);

        // Create base price line item for regular and initial invoices
        if (InvoiceType.REGULAR.name().equals(saved.getType()) || InvoiceType.IN_ADVANCE_INITIAL.name().equals(saved.getType())) {
            BigDecimal baseAmount = sub.getPlan().getPriceAmount();
            if (baseAmount != null && baseAmount.compareTo(BigDecimal.ZERO) != 0) {
                InvoiceItem basePriceItem = new InvoiceItem();
                basePriceItem.setInvoice(saved);
                basePriceItem.setAccount(sub.getCustomer().getAccount());
                basePriceItem.setChargeAmount(baseAmount);
                basePriceItem.setDescription("Plan base price: " + sub.getPlan().getName());
                invoiceItemRepository.save(basePriceItem);
            }
        }

        // If it's usage-based, we need to save the items we calculated.
        // Skip for ADJUSTMENT invoices — their amounts are caller-controlled (proration).
        InvoiceType savedType = invoiceParams.type() != null ? invoiceParams.type() : InvoiceType.REGULAR;
        if (hasUsageRules(sub.getPlan()) &&
            invoiceParams.periodStart().isBefore(Instant.now()) &&
            savedType != InvoiceType.ADJUSTMENT) {
            saveUsageItems(saved);
        }

        log.info("Saved invoice {} for customer {} and plan {}",
                saved.getId(),
                sub.getCustomer().getId(),
                sub.getPlan().getId()
        );

        eventPublisher.publishEvent(
                new InvoiceCreatedEvent(sub.getCustomer().getAccount().getId(), saved.getId(), saved.getType())
        );

        return saved;
    }

    private boolean hasUsageRules(Plan plan) {
        return planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(List.of(plan))
                .stream()
                .anyMatch(rule -> {
                    PricingModel model = RuleCalculationUtil.extractPricingModel(rule);
                    return model != null && ("usage".equals(model.getModel()) || "graduated".equals(model.getModel()));
                });
    }

    private record FeatureChargeResult(BigDecimal charge, String featureKey, BigDecimal usageUnits, String model) {}

    private List<FeatureChargeResult> calculateFeatureCharges(Subscription sub, Instant start, Instant end) {
        UUID subscriptionId = sub.getId();
        List<Event> events = new ArrayList<>(eventRepository.findEventsForBillingBySubscription(
                sub.getCustomer().getId(),
                subscriptionId,
                List.of(EventType.ENTITLEMENT_CHECKED, EventType.CLIENT_TRACKED),
                start,
                end));

        // Smart fallback: include untagged legacy events only if customer has exactly 1 active subscription
        // (avoids double-counting for multi-sub customers)
        long activeSubCount = subscriptionRepository.findSubscriptionsByCustomer_Id(sub.getCustomer().getId())
                .stream().filter(Subscription::getIsActive).count();
        if (activeSubCount <= 1) {
            events.addAll(eventRepository.findEventsForBillingUntagged(
                    sub.getCustomer().getId(),
                    List.of(EventType.ENTITLEMENT_CHECKED, EventType.CLIENT_TRACKED),
                    start,
                    end));
        }

        if (events.isEmpty()) {
            return Collections.emptyList();
        }

        // Fetch rules once upfront for feature resolution and charge calculation
        List<PlanFeatureRule> rules = planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(List.of(sub.getPlan()));

        // Build lookup maps by feature key
        Map<String, PlanFeatureRule> ruleByFeatureKey = rules.stream()
                .filter(r -> r.getFeature() != null)
                .collect(Collectors.toMap(r -> r.getFeature().getKey(), r -> r, (a, b) -> a));

        Map<UUID, String> featureIdToKey = rules.stream()
                .filter(r -> r.getFeature() != null && r.getFeature().getId() != null)
                .collect(Collectors.toMap(r -> r.getFeature().getId(), r -> r.getFeature().getKey(), (a, b) -> a));

        // Group events by feature key: resolve via featureId first, fall back to eventName
        Map<String, List<Event>> eventsByFeatureKey = new HashMap<>();
        for (Event event : events) {
            String key = null;
            if (event.getFeatureId() != null) {
                key = featureIdToKey.get(event.getFeatureId());
            }
            if (key == null && event.getEventName() != null && ruleByFeatureKey.containsKey(event.getEventName())) {
                key = event.getEventName();
            }
            if (key != null) {
                eventsByFeatureKey.computeIfAbsent(key, k -> new ArrayList<>()).add(event);
            }
        }

        List<FeatureChargeResult> results = new ArrayList<>();

        for (Map.Entry<String, List<Event>> entry : eventsByFeatureKey.entrySet()) {
            String featureKey = entry.getKey();
            List<Event> featureEvents = entry.getValue();

            PlanFeatureRule rule = ruleByFeatureKey.get(featureKey);

            if (rule == null) continue;

            PricingModel pricingModel = RuleCalculationUtil.extractPricingModel(rule);
            if (pricingModel == null) continue;

            BigDecimal totalUsageForFeature = featureEvents.stream()
                    .map(e -> e.getUsageUnits() != null ? e.getUsageUnits() : BigDecimal.ONE)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal featureCharge;

            if (pricingModel.isAccumulateMode()) {
                // Non-resetting billing: bill only the period delta but use cumulative total for graduated tiers
                UUID featureId = rule.getFeature().getId();
                BigDecimal cumulativeTotal;
                if (activeSubCount <= 1) {
                    // Include untagged legacy events in cumulative total to match the events list
                    cumulativeTotal = eventRepository.sumUsageUnitsForSubscriptionOrUntaggedSince(
                            sub.getCustomer().getId(), sub.getId(), featureId, sub.getCreatedAt(), end);
                } else {
                    cumulativeTotal = eventRepository.sumUsageUnitsBySubscriptionAndFeatureIdSince(
                            sub.getCustomer().getId(), sub.getId(), featureId, sub.getCreatedAt(), end);
                }

                if (pricingModel instanceof GraduatedPricingModel graduatedModel) {
                    featureCharge = graduatedModel.calculateIncrementalCost(cumulativeTotal, totalUsageForFeature);
                } else {
                    // Simple usage: flat rate, so accumulate mode has same result as reset mode
                    featureCharge = pricingModel.calculateCost(totalUsageForFeature);
                }
            } else {
                // Default (reset) mode
                if (pricingModel instanceof GraduatedPricingModel) {
                    // Graduated pricing must be recalculated at billing time
                    // because cost per unit depends on total period usage position
                    featureCharge = pricingModel.calculateCost(totalUsageForFeature);
                } else {
                    // Simple usage: sum per-event revenue
                    featureCharge = featureEvents.stream()
                            .map(e -> e.getRevenueAmount() != null ? e.getRevenueAmount() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                }
            }

            if (featureCharge.compareTo(BigDecimal.ZERO) != 0) {
                results.add(new FeatureChargeResult(featureCharge, rule.getFeature().getKey(), totalUsageForFeature, pricingModel.getModel()));
            }
        }
        return results;
    }

    private List<InvoiceItem> calculateUsageItems(Invoice invoice) {
        List<FeatureChargeResult> charges = calculateFeatureCharges(
                invoice.getSubscription(), invoice.getInvoicePeriodStart(), invoice.getInvoicePeriodEnd());

        List<InvoiceItem> usageItems = new ArrayList<>();
        for (FeatureChargeResult result : charges) {
            InvoiceItem item = new InvoiceItem();
            item.setInvoice(invoice);
            item.setAccount(invoice.getAccount());
            item.setChargeAmount(result.charge());
            item.setDescription(String.format("Usage for %s: %s units (Model: %s)", result.featureKey(), result.usageUnits(), result.model()));
            usageItems.add(item);
        }
        return usageItems;
    }

    @Override
    public BigDecimal calculateUsageChargeForPeriod(Subscription subscription, Instant periodStart, Instant periodEnd) {
        List<FeatureChargeResult> charges = calculateFeatureCharges(subscription, periodStart, periodEnd);
        return charges.stream()
                .map(FeatureChargeResult::charge)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public boolean planHasAccumulateModeFeatures(Plan plan) {
        return planFeatureRuleRepository.getPlanFeatureRuleByPlanIn(List.of(plan))
                .stream()
                .anyMatch(rule -> {
                    PricingModel model = RuleCalculationUtil.extractPricingModel(rule);
                    return model != null && model.isAccumulateMode();
                });
    }

    private void saveUsageItems(Invoice invoice) {
        invoiceItemRepository.deleteUsageItemsByInvoice(invoice);
        List<InvoiceItem> items = calculateUsageItems(invoice);
        if (!items.isEmpty()) {
            invoiceItemRepository.saveAll(items);
        }
    }

    private Invoice buildInvoice(CreateInvoiceParams p) {
        Subscription sub = p.subscription();

        BigDecimal baseAmount = (p.amount() != null)
                ? p.amount()
                : sub.getPlan().getPriceAmount();

        String currency = (p.currency() != null) ? p.currency() : "USD";
        InvoiceType type = (p.type() != null) ? p.type() : InvoiceType.REGULAR;

        Instant dueAt = p.dueDate().atStartOfDay(ZoneOffset.UTC).toInstant();

        Invoice invoice = new Invoice();
        invoice.setSubscription(sub);
        invoice.setAccount(sub.getCustomer().getAccount());
        invoice.setStatus(p.status().name());
        invoice.setAmount(baseAmount); // Will be updated if usage is added
        invoice.setCurrency(currency);
        invoice.setDueDate(dueAt);
        invoice.setType(type.name());
        invoice.setInvoicePeriodStart(p.periodStart());
        invoice.setInvoicePeriodEnd(p.periodEnd());

        // For usage-based billing in arrears, we tally up the costs.
        // Skip for ADJUSTMENT invoices — their amounts are explicitly computed by the caller (e.g., proration).
        if (hasUsageRules(sub.getPlan()) &&
            p.periodStart().isBefore(Instant.now()) &&
            type != InvoiceType.ADJUSTMENT) {
            calculateAndAddUsageCosts(invoice);
        }

        return invoice;
    }

    private void calculateAndAddUsageCosts(Invoice invoice) {
        log.info("Calculating usage costs for invoice period {} to {}", 
                invoice.getInvoicePeriodStart(), invoice.getInvoicePeriodEnd());

        // Reset amount to base amount before adding usage costs to allow for re-calculation
        invoice.setAmount(invoice.getSubscription().getPlan().getPriceAmount());

        List<InvoiceItem> usageItems = calculateUsageItems(invoice);
        
        if (!usageItems.isEmpty()) {
            BigDecimal totalUsageAmount = usageItems.stream()
                    .map(InvoiceItem::getChargeAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            invoice.setAmount(invoice.getAmount().add(totalUsageAmount));
            log.info("Added {} usage items totaling {}", usageItems.size(), totalUsageAmount);
        } else {
            log.info("No usage events found for customer {}", invoice.getSubscription().getCustomer().getId());
        }
    }

    @Override
    @Transactional
    public InvoiceDto createNewInvoice(Subscription subscription, LocalDate dueDate, InvoiceStatus status) {
        String currency = subscription.getPlan().getCurrency() != null ? subscription.getPlan().getCurrency() : "USD";
        return invoiceMapper.invoiceEntityToInvoiceDto(createNewInvoice(new CreateInvoiceParams(subscription, dueDate, status, subscription.getCurrentPeriodStart(), subscription.getCurrentPeriodEnd(), subscription.getPlan().getPriceAmount(), currency, InvoiceType.REGULAR)));
    }

    @Override
    @Transactional
    public InvoiceDto createNewInvoice(Subscription subscription, LocalDate dueDate, InvoiceStatus status, InvoiceType type) {
        String currency = subscription.getPlan().getCurrency() != null ? subscription.getPlan().getCurrency() : "USD";
        return invoiceMapper.invoiceEntityToInvoiceDto(createNewInvoice(new CreateInvoiceParams(subscription, dueDate, status, subscription.getCurrentPeriodStart(), subscription.getCurrentPeriodEnd(), subscription.getPlan().getPriceAmount(), currency, type)));
    }

    @Override
    @Transactional
    public InvoiceDto createNewInvoice(
            Subscription subscription,
            LocalDate dueDate,
            BigDecimal amount,
            InvoiceStatus status,
            Instant periodStart,
            Instant periodEnd
    ) {
        String currency = subscription.getPlan().getCurrency() != null ? subscription.getPlan().getCurrency() : "USD";
        return invoiceMapper.invoiceEntityToInvoiceDto(createNewInvoice(new CreateInvoiceParams(
                subscription, dueDate, status, periodStart, periodEnd, amount, currency, InvoiceType.REGULAR
        )));
    }


    @Override
    public List<InvoiceDto> retrieveInvoicesByExternalClientCustomerId(String externalClientCustomerId, String accountId) {
        Customer customer = customerService
                .retrieveCustomerByExternalClientCustomerIdAndAccount(externalClientCustomerId, accountId);

        List<UUID> subscriptionIds = subscriptionRepository.findSubscriptionsByCustomer(customer)
                .stream()
                .map(Subscription::getId).toList();
        List<Invoice> invoices = invoiceRepository.getInvoicesBySubscriptionIdIn(subscriptionIds);
        return invoiceMapper.invoiceEntityListToInvoiceDtoList(invoices);
    }

    @Override
    public List<InvoiceDto> retrieveInvoicesByAccount(String accountId) {
        List<Invoice> invoices = invoiceRepository.getInvoicesByAccount_Id(UUID.fromString(accountId));
        return invoiceMapper.invoiceEntityListToInvoiceDtoList(invoices);
    }

    @Override
    public List<InvoiceDto> retrieveOnlyDueInvoicesByAccount(String accountId) {
        List<Invoice> invoices = invoiceRepository.getInvoicesByAccount_IdAndStatus(UUID.fromString(accountId),
                InvoiceStatus.DUE.name());
        return invoiceMapper.invoiceEntityListToInvoiceDtoList(invoices);
    }

    @Override
    public Invoice retrieveCurrentlyDueBySubscription(Subscription subscription) {
        return invoiceRepository.getCurrentlyDueInvoiceBySubscription(subscription);
    }

    @Override
    @Transactional(readOnly = true)
    public Invoice retrieveInvoiceByInvoiceIdAndAccount(String invoiceId, String accountId) {
        return invoiceRepository.findByIdAndAccount(UUID.fromString(invoiceId), UUID.fromString(accountId));
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceDto retrieveInvoiceById(String invoiceId, String accountId) {
        Invoice invoice = invoiceRepository.findByIdAndAccount(UUID.fromString(invoiceId), UUID.fromString(accountId));
        if (invoice == null) {
            throw new IllegalArgumentException("Invoice not found with id: " + invoiceId);
        }
        InvoiceDto dto = invoiceMapper.invoiceEntityToInvoiceDto(invoice);
        List<InvoiceItem> items = invoiceItemRepository.findAllByInvoice(invoice);
        List<InvoiceItemDto> itemDtos = invoiceMapper.invoiceItemEntityListToInvoiceItemDtoList(items);
        dto.setItems(itemDtos);
        return dto;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markInvoiceAsPaid(String invoiceId) {
        Invoice invoice = invoiceRepository.findById(UUID.fromString(invoiceId)).orElseThrow(() -> new IllegalArgumentException("Invoice not found with id: " + invoiceId));
        markInvoiceAsPaid(invoice);
    }

    @Override
    @Transactional
    public void markInvoiceAsPaid(Invoice invoice) {
        invoice.setStatus(InvoiceStatus.PAID.name());
        invoiceRepository.save(invoice);

        // Activate subscription when the initial in-advance invoice is paid
        if (InvoiceType.IN_ADVANCE_INITIAL.name().equals(invoice.getType())) {
            Subscription subscription = invoice.getSubscription();
            subscription.setIsActive(true);
            subscriptionRepository.save(subscription);

            eventPublisher.publishEvent(new com.tansoflow.tansocore.model.event.service.SubscriptionActivatedEvent(
                    subscription.getAccount().getId(), subscription.getId()));
        }

        entitlementService.processEntitlementsForSubscription(invoice.getSubscription());
        creditService.processCreditGrantsForSubscription(invoice.getSubscription());
    }

    @Override
    public Invoice retrieveInitialInvoiceForSubscription(Subscription subscription) {
        return invoiceRepository.getInitialInvoiceForSubscription(subscription);
    }

    @Override
    public boolean existsInvoiceForPeriod(Subscription sub, Instant start, Instant end) {
        return invoiceRepository
                .existsBySubscriptionAndInvoicePeriodStartAndInvoicePeriodEndAndType(sub, start, end, InvoiceType.REGULAR.name());
    }


    @Override
    public void processCancelledInvoices() {
        Pageable pageable = PageRequest.of(0, 500);
        int pageCount = 0;
        while (true) {
            Page<Invoice> page = invoiceRepository.getInvoicesByStatus(InvoiceStatus.CANCELLED.name(), pageable);
            if (page.isEmpty()) break;

            log.info("Processing cancelled invoices page {} size: {}", pageCount, page.getNumberOfElements());
            for (Invoice invoice : page.getContent()) {
                if (invoice.getInvoicePeriodStart().isBefore(LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant())) {
                    entitlementService.processEntitlementRevokeForSubscription(invoice.getSubscription());
                    invoice.setStatus(InvoiceStatus.CANCELLED_PROCESSED.name());
                    invoice.getSubscription().setIsActive(false);
                    subscriptionRepository.save(invoice.getSubscription());
                }
                invoiceRepository.save(invoice);
            }

            if (!page.hasNext()) break;
            if (pageCount++ >= 10000) {
                log.error("Aborting cancelled invoice processing: exceeded max pages");
                break;
            }
            pageable = page.nextPageable();
        }
    }

    @Override
    @Transactional
    public void processPendingInvoices() {
        Pageable pageable = PageRequest.of(0, 500);
        int pageCount = 0;
        while (true) {
            Page<Invoice> page = invoiceRepository.getInvoicesByStatusExcludingFullSyncPaged(InvoiceStatus.PENDING.name(), pageable);
            if (page.isEmpty()) break;

            log.info("Processing pending invoices page {} size: {}", pageCount, page.getNumberOfElements());
            processPendingInvoices(page.getContent());

            if (!page.hasNext()) break;
            if (pageCount++ >= 10000) {
                log.error("Aborting pending invoice processing: exceeded max pages");
                break;
            }
            pageable = page.nextPageable();
        }
    }

    @Override
    @Transactional
    public void processDueInvoices() {
        Pageable pageable = PageRequest.of(0, 500);
        int pageCount = 0;
        while (true) {
            Page<Invoice> page = invoiceRepository.getInvoicesByStatusExcludingFullSyncPaged(InvoiceStatus.DUE.name(), pageable);
            if (page.isEmpty()) break;

            log.info("Processing due invoices page {} size: {}", pageCount, page.getNumberOfElements());
            processDueInvoices(page.getContent());

            if (!page.hasNext()) break;
            if (pageCount++ >= 10000) {
                log.error("Aborting due invoice processing: exceeded max pages");
                break;
            }
            pageable = page.nextPageable();
        }
    }

    @Transactional
    @Override
    public Invoice createAdjustmentInvoice(Plan subscribedPlan, Plan newPlan, Subscription currentSubscription, BigDecimal ratio, Instant now) {
        // This is the remaining charge calculated from the current plan
        BigDecimal oldRemaining = ratio
                .multiply(subscribedPlan.getPriceAmount())
                .setScale(2, RoundingMode.HALF_UP);

        // This is the remaining charge calculated from the new plan
        BigDecimal newRemaining = ratio
                .multiply(newPlan.getPriceAmount())
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal credit = oldRemaining.negate();

        BigDecimal net =  newRemaining.add(credit);

        String currency = newPlan.getCurrency() != null ? newPlan.getCurrency() : "USD";

        CreateInvoiceParams params = new CreateInvoiceParams(currentSubscription,
                LocalDate.ofInstant(now, ZoneOffset.UTC),
                InvoiceStatus.DUE,
                now,
                currentSubscription.getCurrentPeriodEnd(),
                net, currency,
                InvoiceType.ADJUSTMENT);

        Invoice adjustmentInvoice = createNewInvoice(params);

        InvoiceItem oldPlanItem = new InvoiceItem();
        oldPlanItem.setAccount(currentSubscription.getAccount());
        oldPlanItem.setDescription("Credit from old plan: " + subscribedPlan.getName());
        oldPlanItem.setChargeAmount(credit);

        InvoiceItem newPlanItem = new InvoiceItem();
        newPlanItem.setAccount(currentSubscription.getAccount());
        newPlanItem.setDescription("Credit from new plan: " + newPlan.getName());
        newPlanItem.setChargeAmount(newRemaining);



        Invoice savedInvoice = invoiceRepository.saveAndFlush(adjustmentInvoice);
        oldPlanItem.setInvoice(savedInvoice);
        newPlanItem.setInvoice(savedInvoice);

        invoiceItemRepository.save(oldPlanItem);
        invoiceItemRepository.save(newPlanItem);

        return savedInvoice;
    }

    @Override
    public boolean hasPastDueInvoice(Subscription sub) {
        return invoiceRepository.existsBySubscriptionAndStatus(sub, InvoiceStatus.PAST_DUE.name());
    }

    private boolean isInvoiceOverdue(Invoice invoice, LocalDate processingDate) {
        return invoice.getDueDate().plus(invoice.getSubscription().getGracePeriodDays(), ChronoUnit.DAYS)
                .isBefore(processingDate
                        .atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    /**
     * Processes a collection of pending invoices to check their due status. If an invoice is overdue,
     * its status is updated to "DUE" and saved to the repository. Logs information for invoices
     * that are not overdue.
     *
     * @param pendingInvoices a collection of invoices that are pending and need to be processed based on their due dates
     */
    private void processPendingInvoices(Collection<Invoice> pendingInvoices) {
        LocalDate processingDate = LocalDate.now();
        Instant processingDateInstant = processingDate.atStartOfDay(ZoneOffset.UTC).toInstant();

        for (Invoice invoice : pendingInvoices) {
            // Re-calculate usage for PENDING invoices to ensure they are up to date
            if (hasUsageRules(invoice.getSubscription().getPlan())) {
                calculateAndAddUsageCosts(invoice);
                saveUsageItems(invoice);
                invoiceRepository.save(invoice);
            }

            if (invoice.getDueDate().isBefore(processingDateInstant) || invoice.getDueDate().equals(processingDateInstant)) {
                if (isInvoiceOverdue(invoice, processingDate)) {
                    invoice.setStatus(InvoiceStatus.DUE.name());
                    invoiceRepository.save(invoice);
                } else {
                    log.debug("Invoice is not overdue yet for id: {}", invoice.getId());
                }
            }
        }
    }

    @Override
    @Transactional
    public void voidOutstandingInvoicesForSubscription(Subscription subscription) {
        List<Invoice> outstanding = invoiceRepository.findOutstandingInvoicesBySubscription(subscription);
        for (Invoice invoice : outstanding) {
            invoice.setStatus(InvoiceStatus.VOID.name());
            invoiceRepository.save(invoice);
            log.info("Voided invoice {} for cancelled subscription {}", invoice.getId(), subscription.getId());
        }
    }

    @Override
    @Transactional
    public Invoice createCreditInvoice(Subscription subscription, BigDecimal creditAmount, Instant periodStart, Instant periodEnd) {
        CreateInvoiceParams params = new CreateInvoiceParams(
                subscription,
                LocalDate.ofInstant(Instant.now(), ZoneOffset.UTC),
                InvoiceStatus.PAID,
                periodStart,
                periodEnd,
                creditAmount,
                "USD",
                InvoiceType.CREDIT
        );

        Invoice creditInvoice = createNewInvoice(params);
        log.info("Created credit invoice {} of {} for subscription {}", creditInvoice.getId(), creditAmount, subscription.getId());
        return creditInvoice;
    }

    @Override
    @Transactional
    public void syncInvoiceFromStripe(com.tansoflow.tansocore.entity.Invoice tansoInvoice, BigDecimal amount,
                                       Instant periodStart, Instant periodEnd,
                                       List<SyncLineItem> lineItems) {
        tansoInvoice.setAmount(amount);
        if (periodStart != null) {
            tansoInvoice.setInvoicePeriodStart(periodStart);
        }
        if (periodEnd != null) {
            tansoInvoice.setInvoicePeriodEnd(periodEnd);
        }
        invoiceRepository.save(tansoInvoice);

        invoiceItemRepository.deleteAllByInvoice(tansoInvoice);

        for (SyncLineItem lineItem : lineItems) {
            InvoiceItem item = new InvoiceItem();
            item.setInvoice(tansoInvoice);
            item.setAccount(tansoInvoice.getAccount());
            item.setChargeAmount(lineItem.chargeAmount());
            item.setDescription(lineItem.description());
            invoiceItemRepository.save(item);
        }

        log.info("STRIPE_INTEGRATION: Synced Tanso invoice {} with {} line items, amount={}",
                tansoInvoice.getId(), lineItems.size(), amount);
    }

    /**
     * Processes the collection of due invoices by checking their due dates and
     * updating their statuses if they are overdue. If an invoice is past due,
     * its status is updated, it is saved in the repository, and the entitlement
     * associated with the invoice's subscription is revoked.
     *
     * @param dueInvoices the collection of invoices to be processed. Each invoice
     *                    is checked to see if it is overdue based on the current date.
     */
    private void processDueInvoices(Collection<Invoice> dueInvoices) {
        LocalDate processingDate = LocalDate.now();
        Instant processingDateInstant = processingDate.atStartOfDay(ZoneOffset.UTC).toInstant();

        // checks if any of the invoices is past due
        for (Invoice invoice : dueInvoices) {
            // Re-calculate usage for DUE invoices that have usage rules (e.g., hybrid plans in advance)
            if (hasUsageRules(invoice.getSubscription().getPlan())) {
                calculateAndAddUsageCosts(invoice);
                saveUsageItems(invoice);
                invoiceRepository.save(invoice);
            }

            if (invoice.getDueDate().isBefore(processingDateInstant)) {
                if (isInvoiceOverdue(invoice, processingDate)) {
                    invoice.setStatus(InvoiceStatus.PAST_DUE.name());
                    invoiceRepository.save(invoice);
                    entitlementService.processEntitlementRevokeForSubscription(invoice.getSubscription());
                } else {
                    log.debug("Invoice is not past due yet for id: {}", invoice.getId());
                }
            }
        }
    }

}
