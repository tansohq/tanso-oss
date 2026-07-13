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

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.AccountSetting;
import com.tansoflow.tansocore.entity.CreditPoolSubscription;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.ExternalApiKey;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.entity.StripeCustomer;
import com.tansoflow.tansocore.entity.StripeProduct;
import com.tansoflow.tansocore.entity.StripeSubscription;
import com.tansoflow.tansocore.entity.StripeWebhookEvent;
import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.integration.stripe.StripeWebhook;
import com.tansoflow.tansocore.model.api.external.ExternalApiKeyType;
import com.tansoflow.tansocore.model.api.external.StripeMode;
import com.tansoflow.tansocore.model.billing.type.InvoiceStatus;
import com.tansoflow.tansocore.model.event.service.InvoicePaidEvent;
import com.tansoflow.tansocore.repository.AccountRepository;
import com.tansoflow.tansocore.repository.AccountSettingRepository;
import com.tansoflow.tansocore.repository.CreditPoolSubscriptionRepository;
import com.tansoflow.tansocore.repository.ExternalApiKeyRepository;
import com.tansoflow.tansocore.repository.StripeCustomerRepository;
import com.tansoflow.tansocore.repository.StripeProductPlansRepository;
import com.tansoflow.tansocore.repository.StripeSubscriptionRepository;
import com.tansoflow.tansocore.repository.StripeWebhookEventRepository;
import com.tansoflow.tansocore.repository.SubscriptionRepository;
import com.tansoflow.tansocore.repository.SubscriptionScheduledChangeRepository;
import com.tansoflow.tansocore.entity.SubscriptionScheduledChange;
import com.tansoflow.tansocore.model.subscription.type.SubscriptionScheduledChangeStatus;
import com.tansoflow.tansocore.model.customer.CustomerDto;
import com.tansoflow.tansocore.service.internal.account.CustomerService;
import com.tansoflow.tansocore.service.internal.monetization.CreditService;
import com.tansoflow.tansocore.service.internal.monetization.EntitlementService;
import com.tansoflow.tansocore.service.internal.monetization.InvoiceService;
import com.tansoflow.tansocore.service.internal.monetization.PlanService;
import com.tansoflow.tansocore.service.internal.monetization.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class StripeWebhookImpl implements StripeWebhook {
    private final StripeSyncServiceImpl stripeSyncService;
    private final ApplicationEventPublisher eventPublisher;
    private final ExternalApiKeyRepository externalApiKeyRepository;
    private final InvoiceService invoiceService;
    private final SubscriptionService subscriptionService;
    private final AccountSettingRepository accountSettingRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final EntitlementService entitlementService;
    private final StripeWebhookEventRepository stripeWebhookEventRepository;
    private final StripeSubscriptionRepository stripeSubscriptionRepository;
    private final CreditService creditService;
    private final CreditPoolSubscriptionRepository creditPoolSubscriptionRepository;
    private final StripeCustomerRepository stripeCustomerRepository;
    private final StripeProductPlansRepository stripeProductPlansRepository;
    private final AccountRepository accountRepository;
    private final CustomerService customerService;
    private final PlanService planService;
    private final SubscriptionScheduledChangeRepository subscriptionScheduledChangeRepository;

    @Override
    @Transactional
    public void ingestWebhookRequest(String body, HttpHeaders headers, String accountId) throws Exception {
        ExternalApiKey externalApiKey = externalApiKeyRepository
                .findExternalApiKeyByKeyTypeAndAccount(ExternalApiKeyType.WEBHOOK_SECRET_SIGNING.name(), UUID.fromString(accountId));

        String signingSecret = externalApiKey.getKeyValue();

        Event event;

        String sigHeader = headers.getFirst("Stripe-Signature");

        if (sigHeader == null || sigHeader.isBlank()) {
            log.error("Webhook rejected: missing Stripe-Signature header for account {}", accountId);
            throw new SignatureVerificationException("Missing Stripe-Signature header", sigHeader);
        }

        try {
            event = Webhook.constructEvent(body, sigHeader, signingSecret);
        } catch (SignatureVerificationException e) {
            log.error("Webhook error while validating signature.");
            throw e;
        }

        // Idempotency check: skip if we've already processed this event
        if (stripeWebhookEventRepository.existsById(event.getId())) {
            log.info("Duplicate webhook event {} already processed, skipping", event.getId());
            return;
        }

        StripeWebhookEvent webhookEvent = new StripeWebhookEvent();
        webhookEvent.setEventId(event.getId());
        webhookEvent.setEventType(event.getType());
        stripeWebhookEventRepository.save(webhookEvent);

        StripeMode stripeMode = getStripeMode(UUID.fromString(accountId));

        // Deserialize the nested object inside the event
        try {
            switch (event.getType()) {
                case "invoice.created" -> {
                    Invoice invoice = deserializeEvent(event, Invoice.class);
                    log.info("Invoice created. Invoice Id: {}", invoice.getId());

                    if (stripeMode.isStripeIntegration()) {
                        handleFullSyncInvoiceCreated(invoice, accountId);
                    } else if (stripeMode == StripeMode.STRIPE_DRIVEN) {
                        handleStripeDrivenInvoiceCreated(invoice, accountId);
                    } else {
                        handleInvoiceCreated(invoice);
                    }
                }
                case "invoice.paid", "invoice.payment_succeeded" -> {
                    Invoice invoice = deserializeEvent(event, Invoice.class);
                    log.info("Invoice paid or payment succeeded. Invoice Id: {}", invoice.getId());

                    if (stripeMode.isStripeIntegration()) {
                        handleFullSyncInvoicePaid(invoice, accountId);
                    } else if (stripeMode == StripeMode.STRIPE_DRIVEN) {
                        handleStripeDrivenInvoicePaid(invoice, accountId);
                    } else {
                        handlePaymentInvoicePaid(invoice);
                    }
                }
                case "invoice.payment_failed" -> {
                    Invoice invoice = deserializeEvent(event, Invoice.class);
                    log.info("Invoice payment failed. Invoice Id: {}", invoice.getId());

                    if (stripeMode.isStripeIntegration()) {
                        handleFullSyncInvoicePaymentFailed(invoice);
                    } else if (stripeMode == StripeMode.PAYMENT_PASS_THROUGH) {
                        handlePaymentPassThroughInvoicePaymentFailed(invoice);
                    } else if (stripeMode == StripeMode.STRIPE_DRIVEN) {
                        handleStripeDrivenInvoicePaymentFailed(invoice);
                    }
                }
                case "customer.subscription.created" -> {
                    com.stripe.model.Subscription stripeSub = deserializeEvent(event, com.stripe.model.Subscription.class);
                    log.info("Subscription created. Subscription Id: {}", stripeSub.getId());

                    if (stripeMode == StripeMode.STRIPE_DRIVEN) {
                        handleStripeDrivenSubscriptionCreated(stripeSub, accountId);
                    } else if (stripeMode.isStripeIntegration()) {
                        handleStripeIntegrationSubscriptionCreated(stripeSub, accountId);
                    }
                }
                case "customer.subscription.deleted" -> {
                    com.stripe.model.Subscription stripeSub = deserializeEvent(event, com.stripe.model.Subscription.class);
                    log.info("Subscription deleted. Subscription Id: {}", stripeSub.getId());

                    if (stripeMode.isStripeIntegration()) {
                        handleFullSyncSubscriptionDeleted(stripeSub, accountId);
                    } else if (stripeMode == StripeMode.STRIPE_DRIVEN) {
                        handleStripeDrivenSubscriptionDeleted(stripeSub);
                    }
                }
                case "customer.subscription.updated" -> {
                    com.stripe.model.Subscription stripeSub = deserializeEvent(event, com.stripe.model.Subscription.class);
                    log.info("Subscription updated. Subscription Id: {}", stripeSub.getId());

                    if (stripeMode.isStripeIntegration()) {
                        handleFullSyncSubscriptionUpdated(stripeSub, accountId);
                    } else if (stripeMode == StripeMode.STRIPE_DRIVEN) {
                        handleStripeDrivenSubscriptionUpdated(stripeSub);
                    }
                }
                case "customer.created" -> {
                    com.stripe.model.Customer stripeCustomer = deserializeEvent(event, com.stripe.model.Customer.class);
                    log.info("Customer created. Customer Id: {}", stripeCustomer.getId());

                    if (stripeMode == StripeMode.STRIPE_DRIVEN) {
                        handleStripeDrivenCustomerCreated(stripeCustomer, accountId);
                    }
                }
                case "checkout.session.completed" -> {
                    Session session = deserializeEvent(event, Session.class);
                    handleSessionsComplete(session);
                }
                default -> log.info("Unhandled event type: {}", event.getType());
            }
        } catch (Exception e) {
            log.error("Error occurred while deserializing stripe object:", e);
            throw e;
        }

    }

    private void handleInvoiceCreated(Invoice invoiceObject) {
        String accountId = invoiceObject.getMetadata().get("tanso_account_id");
        String subscriptionId = invoiceObject.getMetadata().get("tanso_subscription_id");

        if (accountId == null) {
            if (invoiceObject.getParent() != null && invoiceObject.getParent().getSubscriptionDetails() != null) {
                accountId = invoiceObject.getParent().getSubscriptionDetails().getMetadata().get("tanso_account_id");
                subscriptionId = invoiceObject.getParent().getSubscriptionDetails().getMetadata().get("tanso_subscription_id");
            }
        }

        if (accountId == null) return;

        Subscription subscription = subscriptionService.getSubscriptionById(subscriptionId, accountId);
        if (subscription == null) {
            log.warn("PAYMENT_PASS_THROUGH: Subscription {} not found for account {}, skipping invoice.created", subscriptionId, accountId);
            return;
        }

        if (!stripeSyncService.stripeInvoiceLinked(invoiceObject.getId())) {
            com.tansoflow.tansocore.entity.Invoice invoice = invoiceService.retrieveCurrentlyDueBySubscription(subscription);
            if (invoice == null) {
                log.warn("PAYMENT_PASS_THROUGH: No DUE invoice found for subscription {}, Stripe invoice {} may have arrived before Tanso finished creating it",
                        subscriptionId, invoiceObject.getId());
                return;
            }

            stripeSyncService.saveStripeInvoice(invoiceObject.getId(), invoice.getId().toString(), accountId);
        }

    }

    private void handleSessionsComplete(Session session) throws StripeException {
        if ("setup".equals(session.getMode())) {
            String setupIntentId = session.getSetupIntent();
            if (setupIntentId == null) throw new IllegalStateException("Missing setup_intent on session");
            String accountId = session.getMetadata().get("tanso_account_id");

            stripeSyncService.syncNewPaymentAsDefault(setupIntentId, accountId, session.getCustomer());
        } else if ("subscription".equals(session.getMode())) {
            log.info("Checkout session completed in subscription mode (session: {}). " +
                    "Subscription will be handled by customer.subscription.created webhook.", session.getId());
        }
    }

    @Transactional
    protected void handlePaymentInvoicePaid(Invoice invoiceObject) {
        String accountId = invoiceObject.getMetadata().get("tanso_account_id");
        String subscriptionId = invoiceObject.getMetadata().get("tanso_subscription_id");
        String invoiceId = invoiceObject.getMetadata().get("tanso_invoice_id");

        if (accountId == null) {
            // Fallback for older metadata or different structure
            if (invoiceObject.getParent() != null && invoiceObject.getParent().getSubscriptionDetails() != null) {
                accountId = invoiceObject.getParent().getSubscriptionDetails().getMetadata().get("tanso_account_id");
                subscriptionId = invoiceObject.getParent().getSubscriptionDetails().getMetadata().get("tanso_subscription_id");
            }
        }

        if (accountId == null) {
            log.error("Missing account_id in metadata for stripe invoice {}", invoiceObject.getId());
            return;
        }

        Subscription subscription = subscriptionService.getSubscriptionById(subscriptionId, accountId);

        if (invoiceId != null) {
            eventPublisher.publishEvent(new InvoicePaidEvent(UUID.fromString(accountId), UUID.fromString(invoiceId)));
            return;
        }

        if (!stripeSyncService.stripeInvoiceLinked(invoiceObject.getId())) {
            com.tansoflow.tansocore.entity.Invoice invoice = invoiceService.retrieveCurrentlyDueBySubscription(subscription);
            invoiceId = invoice.getId().toString();

            stripeSyncService.saveStripeInvoice(invoiceObject.getId(), invoiceId, accountId);
        } else {
            invoiceId = stripeSyncService.retrieveStripeInvoiceLinkedData(invoiceObject.getId()).getInvoice().getId().toString();
        }

        if (invoiceId == null) {
            log.error("Invoice not found for {}", invoiceObject.getId());
            throw new IllegalStateException("Invoice does not exist to link to stripe_invoice: " + invoiceObject.getId());
        }

        if (!stripeSyncService.isSubscriptionLinked(subscription)) {
            stripeSyncService.syncStripeSubscriptionTansoSubscription(invoiceObject.getParent().getSubscriptionDetails().getSubscription(), subscriptionId, accountId);
        }

        eventPublisher.publishEvent(new InvoicePaidEvent(UUID.fromString(accountId), UUID.fromString(invoiceId)));
    }

    /**
     * For PAYMENT_PASS_THROUGH: When a Stripe invoice payment fails, mark the linked Tanso invoice as PAST_DUE.
     */
    @Transactional
    protected void handlePaymentPassThroughInvoicePaymentFailed(Invoice invoiceObject) {
        String accountId = extractMetadata(invoiceObject, "tanso_account_id");
        String invoiceId = extractMetadata(invoiceObject, "tanso_invoice_id");

        if (accountId == null) {
            log.warn("PAYMENT_PASS_THROUGH: invoice.payment_failed missing tanso_account_id for stripe invoice {}", invoiceObject.getId());
            return;
        }

        // Try to resolve the Tanso invoice via metadata or linked data
        if (invoiceId == null && stripeSyncService.stripeInvoiceLinked(invoiceObject.getId())) {
            var linked = stripeSyncService.retrieveStripeInvoiceLinkedData(invoiceObject.getId());
            if (linked != null && linked.getInvoice() != null) {
                invoiceId = linked.getInvoice().getId().toString();
            }
        }

        if (invoiceId == null) {
            log.warn("PAYMENT_PASS_THROUGH: Could not resolve Tanso invoice for failed Stripe invoice {}", invoiceObject.getId());
            return;
        }

        com.tansoflow.tansocore.entity.Invoice tansoInvoice = invoiceService.retrieveInvoiceByInvoiceIdAndAccount(invoiceId, accountId);
        if (tansoInvoice != null) {
            tansoInvoice.setStatus(InvoiceStatus.PAST_DUE.name());
            log.info("PAYMENT_PASS_THROUGH: Marked Tanso invoice {} as PAST_DUE from Stripe invoice payment failure {}",
                    invoiceId, invoiceObject.getId());
        }
    }

    /**
     * FULL_SYNC Webhook Handler for resolving Subscription from Stripe Invoice metadata
     */
    private Subscription resolveSubscription(Invoice stripeInvoice, String accountId) {
        String subscriptionId = extractMetadata(stripeInvoice, "tanso_subscription_id");
        if (subscriptionId != null) {
            try {
                Subscription sub = subscriptionService.getSubscriptionById(subscriptionId, accountId);
                if (sub != null) {
                    return sub;
                }
            } catch (Exception e) {
                log.warn("Subscription {} not found for FULL_SYNC invoice mirror: {}", subscriptionId, e.getMessage());
            }
        }

        // Fallback: look up via StripeSubscription table using the Stripe subscription ID on the invoice
        String stripeSubId = extractStripeSubscriptionId(stripeInvoice);
        if (stripeSubId != null) {
            StripeSubscription stripeSub = stripeSubscriptionRepository
                    .findStripeSubscriptionByStripeSubscriptionExternalId(stripeSubId);
            if (stripeSub != null) {
                return stripeSub.getSubscription();
            }
        }

        return null;
    }

    /**
     * For FULL_SYNC: When Stripe creates an invoice (from a subscription), mirror it in Tanso.
     * For accumulate-mode plans: freeze the draft, calculate the correct charge, add a line item,
     * finalize, and pay the invoice programmatically.
     */
    @Transactional
    protected void handleFullSyncInvoiceCreated(Invoice stripeInvoice, String accountId) {
        Subscription subscription = resolveSubscription(stripeInvoice, accountId);
        if (subscription == null) {
            log.warn("FULL_SYNC invoice.created: could not resolve subscription for stripe invoice {}", stripeInvoice.getId());
            return;
        }

        // Extract period dates from the Stripe invoice to keep tanso subscription in sync.
        // This fixes stale period dates for FULL_SYNC subscriptions since SubscriptionCycleJob
        // excludes FULL_SYNC and no other code path updates periods.
        Instant periodStart = subscription.getCurrentPeriodStart();
        Instant periodEnd = subscription.getCurrentPeriodEnd();
        Instant[] stripePeriod = extractPeriodFromStripeInvoice(stripeInvoice);
        if (stripePeriod != null) {
            periodStart = stripePeriod[0];
            periodEnd = stripePeriod[1];
            subscription.setCurrentPeriodStart(periodStart);
            subscription.setCurrentPeriodEnd(periodEnd);
            subscriptionRepository.save(subscription);
            log.info("FULL_SYNC: Updated subscription {} period to [{}, {}] from Stripe invoice",
                    subscription.getId(), periodStart, periodEnd);
        }

        if (stripeSyncService.stripeInvoiceLinked(stripeInvoice.getId())) {
            updateLinkedInvoiceFromStripe(stripeInvoice, periodStart, periodEnd);
            return;
        }

        if (invoiceService.planHasAccumulateModeFeatures(subscription.getPlan())) {
            handleAccumulateModeInvoiceCreated(stripeInvoice, subscription, accountId, periodStart, periodEnd);
        } else {
            // Non-accumulate: mirror Stripe's amountDue as-is (existing behavior)
            BigDecimal amount = BigDecimal.valueOf(stripeInvoice.getAmountDue()).movePointLeft(2);

            var invoiceDto = invoiceService.createNewInvoice(subscription, LocalDate.now(ZoneOffset.UTC), amount, InvoiceStatus.DUE,
                    periodStart, periodEnd);
            stripeSyncService.saveStripeInvoice(stripeInvoice.getId(), invoiceDto.getId(), accountId);

            log.info("FULL_SYNC: Mirrored Stripe invoice {} to Tanso invoice {}", stripeInvoice.getId(), invoiceDto.getId());
        }
    }

    private void updateLinkedInvoiceFromStripe(Invoice stripeInvoice, Instant periodStart, Instant periodEnd) {
        var stripeInvoiceEntity = stripeSyncService.retrieveStripeInvoiceLinkedData(stripeInvoice.getId());
        com.tansoflow.tansocore.entity.Invoice tansoInvoice = stripeInvoiceEntity.getInvoice();

        BigDecimal amount = BigDecimal.valueOf(stripeInvoice.getAmountDue()).movePointLeft(2);

        List<InvoiceService.SyncLineItem> lineItems = new ArrayList<>();
        if (stripeInvoice.getLines() != null && stripeInvoice.getLines().getData() != null) {
            for (var line : stripeInvoice.getLines().getData()) {
                BigDecimal lineAmount = BigDecimal.valueOf(line.getAmount()).movePointLeft(2);
                String description = line.getDescription() != null ? line.getDescription() : "Stripe line item";
                lineItems.add(new InvoiceService.SyncLineItem(lineAmount, description));
            }
        }

        invoiceService.syncInvoiceFromStripe(tansoInvoice, amount, periodStart, periodEnd, lineItems);
        log.info("FULL_SYNC: Updated linked Tanso invoice {} from Stripe invoice {}", tansoInvoice.getId(), stripeInvoice.getId());
    }

    private void handleAccumulateModeInvoiceCreated(Invoice stripeInvoice, Subscription subscription,
                                                     String accountId, Instant periodStart, Instant periodEnd) {
        UUID accountUuid = UUID.fromString(accountId);
        try {
            // Freeze the draft so it doesn't auto-finalize
            stripeSyncService.disableAutoAdvanceOnStripeInvoice(stripeInvoice.getId(), accountUuid);

            // Apply rollover policy on period boundary (before granting new credits)
            applyCreditRolloverForSubscription(subscription);

            // Extract base price from plan
            BigDecimal basePriceAmount = subscription.getPlan().getPriceAmount();
            boolean hasBasePrice = basePriceAmount != null && basePriceAmount.compareTo(BigDecimal.ZERO) > 0;

            // Calculate the correct charge using tanso-core's accumulate logic
            BigDecimal totalCharge = invoiceService.calculateUsageChargeForPeriod(
                    subscription, periodStart, periodEnd);

            // Apply credit offset: deduct from pool, reduce charge
            BigDecimal creditOffset = applyCreditOffsetForSubscription(
                    subscription, totalCharge, accountUuid);
            BigDecimal netCharge = totalCharge.subtract(creditOffset);

            String currency = subscription.getPlan().getCurrency() != null
                    ? subscription.getPlan().getCurrency() : "USD";

            // Add base price line item to the Stripe draft
            if (hasBasePrice) {
                stripeSyncService.addLineItemToDraftInvoice(
                        stripeInvoice.getId(), accountUuid, basePriceAmount, currency,
                        "Plan base price: " + subscription.getPlan().getName());
            }

            // Add the net charge line item to the Stripe draft
            if (netCharge.compareTo(BigDecimal.ZERO) > 0) {
                stripeSyncService.addLineItemToDraftInvoice(
                        stripeInvoice.getId(), accountUuid, netCharge, currency,
                        "Accumulated usage charge (tanso-calculated)");
            }

            // Add credit offset as a negative line item for transparency
            if (creditOffset.compareTo(BigDecimal.ZERO) > 0) {
                stripeSyncService.addLineItemToDraftInvoice(
                        stripeInvoice.getId(), accountUuid, creditOffset.negate(), currency,
                        "Credit applied");
            }

            // Finalize and charge the customer
            stripeSyncService.finalizeAndPayStripeInvoice(stripeInvoice.getId(), accountUuid);

            // Mirror in tanso-core: total = base price + net usage charge
            BigDecimal totalInvoiceAmount = netCharge.add(hasBasePrice ? basePriceAmount : BigDecimal.ZERO);
            var invoiceDto = invoiceService.createNewInvoice(subscription, LocalDate.now(ZoneOffset.UTC), totalInvoiceAmount, InvoiceStatus.DUE,
                    periodStart, periodEnd);
            stripeSyncService.saveStripeInvoice(stripeInvoice.getId(), invoiceDto.getId(), accountId);

            log.info("FULL_SYNC (accumulate): Mirrored Stripe invoice {} to Tanso invoice {} with total {} (base: {}, usage: {}, credit offset: {})",
                    stripeInvoice.getId(), invoiceDto.getId(), totalInvoiceAmount,
                    hasBasePrice ? basePriceAmount : BigDecimal.ZERO, netCharge, creditOffset);
        } catch (StripeException e) {
            log.error("FULL_SYNC (accumulate): Failed to process Stripe invoice {} for account {}. " +
                    "Invoice remains as draft in Stripe and can be corrected manually. Error: {}",
                    stripeInvoice.getId(), accountId, e.getMessage(), e);
            // Do NOT create tanso-core mirror invoice — the Stripe draft is still recoverable
        }
    }

    /**
     * Applies credit offset for a subscription's linked pools during billing.
     * Returns the total credit offset applied.
     */
    private BigDecimal applyCreditOffsetForSubscription(Subscription subscription, BigDecimal totalCharge, UUID accountId) {
        List<CreditPoolSubscription> links = creditPoolSubscriptionRepository
                .findBySubscriptionIdOrderByDrawPriority(subscription.getId());

        BigDecimal totalOffset = BigDecimal.ZERO;
        BigDecimal remainingCharge = totalCharge;

        for (CreditPoolSubscription link : links) {
            if (remainingCharge.compareTo(BigDecimal.ZERO) <= 0) break;

            try {
                BigDecimal offset = creditService.applyCreditOffset(
                        link.getCreditPool().getId(), remainingCharge, subscription.getId(), accountId,
                        "Billing cycle credit offset");
                totalOffset = totalOffset.add(offset);
                remainingCharge = remainingCharge.subtract(offset);
            } catch (Exception e) {
                log.error("Failed to apply credit offset from pool {} for subscription {}: {}",
                        link.getCreditPool().getId(), subscription.getId(), e.getMessage(), e);
            }
        }

        return totalOffset;
    }

    /**
     * Applies rollover policy for all credit pools linked to a subscription at period boundary.
     */
    private void applyCreditRolloverForSubscription(Subscription subscription) {
        List<CreditPoolSubscription> links = creditPoolSubscriptionRepository
                .findBySubscriptionId(subscription.getId());

        for (CreditPoolSubscription link : links) {
            try {
                creditService.applyRolloverPolicy(link.getCreditPool().getId());
            } catch (Exception e) {
                log.error("Failed to apply rollover policy for pool {} on subscription {}: {}",
                        link.getCreditPool().getId(), subscription.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * For FULL_SYNC: When a Stripe invoice is paid, mark the mirrored Tanso invoice as PAID and grant entitlements.
     */
    @Transactional
    protected void handleFullSyncInvoicePaid(Invoice stripeInvoice, String accountId) {
        if (!stripeSyncService.stripeInvoiceLinked(stripeInvoice.getId())) {
            // If the invoice is already paid/finalized (e.g. invoice.paid arrived before invoice.created),
            // and this is an accumulate-mode plan, skip draft manipulation — create the mirror directly
            // using Stripe's amountPaid to avoid StripeException on an already-finalized invoice.
            Subscription subscription = resolveSubscription(stripeInvoice, accountId);
            if (subscription != null
                    && invoiceService.planHasAccumulateModeFeatures(subscription.getPlan())
                    && "paid".equals(stripeInvoice.getStatus())) {
                createMirrorInvoiceFromPaidStripeInvoice(stripeInvoice, subscription, accountId);
            } else {
                handleFullSyncInvoiceCreated(stripeInvoice, accountId);
            }
        }

        var stripeInvoiceEntity = stripeSyncService.retrieveStripeInvoiceLinkedData(stripeInvoice.getId());
        if (stripeInvoiceEntity == null || stripeInvoiceEntity.getInvoice() == null) {
            log.error("FULL_SYNC: Cannot find linked Tanso invoice for Stripe invoice {}", stripeInvoice.getId());
            return;
        }

        String invoiceId = stripeInvoiceEntity.getInvoice().getId().toString();
        // markInvoiceAsPaid handles: sets PAID status, activates IN_ADVANCE subscriptions,
        // processes entitlements, and grants credits.
        invoiceService.markInvoiceAsPaid(invoiceId);

        // Fulfill pending upgrade if this payment was for a proration invoice
        Subscription paidSubscription = stripeInvoiceEntity.getInvoice().getSubscription();
        if (paidSubscription != null) {
            subscriptionScheduledChangeRepository
                    .findPendingUpgradeBySubscription(paidSubscription)
                    .ifPresent(ssc -> {
                        paidSubscription.setPlan(ssc.getToPlan());
                        subscriptionRepository.save(paidSubscription);
                        entitlementService.processEntitlementsForSubscription(paidSubscription);
                        creditService.processCreditGrantsForSubscription(paidSubscription);
                        ssc.setStatus(SubscriptionScheduledChangeStatus.COMPLETED.name());
                        ssc.setFulfilledAt(Instant.now());
                        subscriptionScheduledChangeRepository.save(ssc);
                        log.info("STRIPE_INTEGRATION: Fulfilled upgrade for subscription {} to plan {}",
                                paidSubscription.getId(), ssc.getToPlan().getId());
                    });
        }

        log.info("STRIPE_INTEGRATION: Marked Tanso invoice {} as PAID from Stripe invoice {}", invoiceId, stripeInvoice.getId());
    }

    /**
     * Creates a mirror tanso invoice directly from an already-paid Stripe invoice.
     * Used when invoice.paid webhook arrives before invoice.created (race condition).
     */
    private void createMirrorInvoiceFromPaidStripeInvoice(Invoice stripeInvoice, Subscription subscription, String accountId) {
        BigDecimal amount = BigDecimal.valueOf(stripeInvoice.getAmountPaid()).movePointLeft(2);

        Instant periodStart = subscription.getCurrentPeriodStart();
        Instant periodEnd = subscription.getCurrentPeriodEnd();
        Instant[] stripePeriod = extractPeriodFromStripeInvoice(stripeInvoice);
        if (stripePeriod != null) {
            periodStart = stripePeriod[0];
            periodEnd = stripePeriod[1];
            subscription.setCurrentPeriodStart(periodStart);
            subscription.setCurrentPeriodEnd(periodEnd);
            subscriptionRepository.save(subscription);
        }

        var invoiceDto = invoiceService.createNewInvoice(subscription, LocalDate.now(ZoneOffset.UTC), amount, InvoiceStatus.DUE,
                periodStart, periodEnd);
        stripeSyncService.saveStripeInvoice(stripeInvoice.getId(), invoiceDto.getId(), accountId);

        log.info("FULL_SYNC (accumulate backfill): Created mirror invoice {} from already-paid Stripe invoice {} with amount {}",
                invoiceDto.getId(), stripeInvoice.getId(), amount);
    }

    /**
     * For FULL_SYNC: When a Stripe invoice payment fails, update the Tanso invoice status.
     */
    @Transactional
    protected void handleFullSyncInvoicePaymentFailed(Invoice stripeInvoice) {
        if (!stripeSyncService.stripeInvoiceLinked(stripeInvoice.getId())) {
            log.warn("FULL_SYNC: No linked Tanso invoice for failed Stripe invoice {}", stripeInvoice.getId());
            return;
        }

        var stripeInvoiceEntity = stripeSyncService.retrieveStripeInvoiceLinkedData(stripeInvoice.getId());
        if (stripeInvoiceEntity != null && stripeInvoiceEntity.getInvoice() != null) {
            com.tansoflow.tansocore.entity.Invoice tansoInvoice = stripeInvoiceEntity.getInvoice();
            tansoInvoice.setStatus(InvoiceStatus.PAST_DUE.name());
            log.info("STRIPE_INTEGRATION: Marked Tanso invoice {} as PAST_DUE from Stripe invoice payment failure {}",
                    tansoInvoice.getId(), stripeInvoice.getId());

            // If a pending upgrade caused this invoice, revert Stripe to the old plan's price
            Subscription subscription = tansoInvoice.getSubscription();
            if (subscription != null) {
                subscriptionScheduledChangeRepository
                        .findPendingUpgradeBySubscription(subscription)
                        .ifPresent(ssc -> {
                            try {
                                // Tanso subscription still has OLD plan — syncing price reverts Stripe
                                stripeSyncService.updateStripeSubscriptionPrice(
                                        subscription.getId(), subscription.getAccount().getId(), false);
                                ssc.setStatus("FAILED");
                                subscriptionScheduledChangeRepository.save(ssc);
                                log.info("STRIPE_INTEGRATION: Reverted upgrade for subscription {}, payment failed",
                                        subscription.getId());
                            } catch (Exception e) {
                                log.error("STRIPE_INTEGRATION: Failed to revert upgrade for subscription {}: {}",
                                        subscription.getId(), e.getMessage(), e);
                            }
                        });
            }
        }
    }

    /**
     * For FULL_SYNC: When a Stripe subscription is deleted, deactivate the Tanso subscription.
     */
    @Transactional
    protected void handleFullSyncSubscriptionDeleted(com.stripe.model.Subscription stripeSub, String accountId) {
        Subscription subscription = resolveSubscriptionFromStripeSubMetadataOrBridge(stripeSub, accountId);
        if (subscription == null) {
            log.warn("STRIPE_INTEGRATION: subscription.deleted — could not resolve Tanso subscription for Stripe sub {}", stripeSub.getId());
            return;
        }

        subscription.setIsActive(false);
        subscription.setCancelledAt(Instant.now());
        subscription.setCancelEffectiveAt(Instant.now());
        subscriptionRepository.save(subscription);

        entitlementService.processEntitlementRevokeForSubscription(subscription);

        log.info("STRIPE_INTEGRATION: Deactivated Tanso subscription {} from Stripe subscription deletion {}", subscription.getId(), stripeSub.getId());
    }

    /**
     * For FULL_SYNC: When a Stripe subscription is updated, reflect status changes and sync period dates.
     */
    @Transactional
    protected void handleFullSyncSubscriptionUpdated(com.stripe.model.Subscription stripeSub, String accountId) {
        Subscription subscription = resolveSubscriptionFromStripeSubMetadataOrBridge(stripeSub, accountId);
        if (subscription == null) {
            log.debug("STRIPE_INTEGRATION: subscription.updated — could not resolve Tanso subscription for Stripe sub {}", stripeSub.getId());
            return;
        }
        String subscriptionId = subscription.getId().toString();

        // Sync period dates from Stripe subscription items to keep tanso-core in sync.
        // In Stripe SDK v31+, period is per-item (not on the Subscription object itself).
        if (stripeSub.getItems() != null && stripeSub.getItems().getData() != null
                && !stripeSub.getItems().getData().isEmpty()) {
            var firstItem = stripeSub.getItems().getData().getFirst();
            if (firstItem.getCurrentPeriodStart() != null && firstItem.getCurrentPeriodEnd() != null) {
                Instant stripeStart = Instant.ofEpochSecond(firstItem.getCurrentPeriodStart());
                Instant stripeEnd = Instant.ofEpochSecond(firstItem.getCurrentPeriodEnd());
                if (!stripeStart.equals(subscription.getCurrentPeriodStart()) || !stripeEnd.equals(subscription.getCurrentPeriodEnd())) {
                    subscription.setCurrentPeriodStart(stripeStart);
                    subscription.setCurrentPeriodEnd(stripeEnd);
                    log.info("FULL_SYNC: Synced subscription {} period to [{}, {}] from Stripe", subscriptionId, stripeStart, stripeEnd);
                }
            }
        }

        // If Stripe marks the subscription as canceled or past_due, reflect in Tanso
        String status = stripeSub.getStatus();
        if ("canceled".equals(status) || "unpaid".equals(status)) {
            if (Boolean.TRUE.equals(subscription.getIsActive())) {
                subscription.setIsActive(false);
                subscription.setCancelledAt(Instant.now());
                entitlementService.processEntitlementRevokeForSubscription(subscription);
                log.info("FULL_SYNC: Deactivated subscription {} due to Stripe status: {}", subscriptionId, status);
            }
        }

        subscriptionRepository.save(subscription);
    }

    // ─── STRIPE_DRIVEN handlers ───────────────────────────────────────────

    /**
     * STRIPE_DRIVEN: When a new subscription is created in Stripe, auto-create a Tanso Subscription
     * by resolving the customer and product via bridge tables.
     */
    @Transactional
    protected void handleStripeDrivenSubscriptionCreated(com.stripe.model.Subscription stripeSub, String accountId) {
        if (stripeSubscriptionRepository.existsStripeSubscriptionByStripeSubscriptionExternalId(stripeSub.getId())) {
            log.info("STRIPE_DRIVEN: Subscription {} already mapped, skipping", stripeSub.getId());
            return;
        }

        Account account = accountRepository.findById(UUID.fromString(accountId)).orElse(null);
        if (account == null) return;

        // Resolve customer via bridge table, auto-creating if missing
        StripeCustomer stripeCustomer = stripeCustomerRepository.findByStripeCustomerExternalIdAndAccount(
                stripeSub.getCustomer(), account);
        if (stripeCustomer == null) {
            log.info("STRIPE_DRIVEN: No mapped customer for Stripe customer {}, attempting auto-creation", stripeSub.getCustomer());
            try {
                com.stripe.model.Customer stripeCustomerObj = com.stripe.model.Customer.retrieve(stripeSub.getCustomer());
                handleStripeDrivenCustomerCreated(stripeCustomerObj, accountId);
                stripeCustomer = stripeCustomerRepository.findByStripeCustomerExternalIdAndAccount(
                        stripeSub.getCustomer(), account);
            } catch (StripeException e) {
                log.error("STRIPE_DRIVEN: Failed to retrieve Stripe customer {} for auto-creation: {}", stripeSub.getCustomer(), e.getMessage(), e);
            }
            if (stripeCustomer == null) {
                log.error("STRIPE_DRIVEN: Could not auto-create customer for Stripe customer {}, skipping subscription {}", stripeSub.getCustomer(), stripeSub.getId());
                return;
            }
        }
        Customer tansoCustomer = stripeCustomer.getCustomer();

        // Resolve plan from first subscription item's product via bridge table
        Plan plan = resolveStripeDrivenPlan(stripeSub, account);
        if (plan == null) {
            log.warn("STRIPE_DRIVEN: No mapped plan for subscription {}, skipping", stripeSub.getId());
            return;
        }

        Subscription tansoSub = new Subscription();
        tansoSub.setCustomer(tansoCustomer);
        tansoSub.setPlan(plan);
        tansoSub.setAccount(account);
        tansoSub.setIsActive("active".equals(stripeSub.getStatus()));
        syncPeriodFromStripeSubscription(stripeSub, tansoSub);
        subscriptionRepository.save(tansoSub);

        StripeSubscription bridge = new StripeSubscription();
        bridge.setSubscription(tansoSub);
        bridge.setAccount(account);
        bridge.setStripeSubscriptionExternalId(stripeSub.getId());
        stripeSubscriptionRepository.save(bridge);

        if (tansoSub.getIsActive()) {
            entitlementService.processEntitlementsForSubscription(tansoSub);
        }

        log.info("STRIPE_DRIVEN: Created Tanso subscription {} from Stripe subscription {}", tansoSub.getId(), stripeSub.getId());
    }

    /**
     * STRIPE_INTEGRATION: When a Stripe subscription is created (e.g. after Checkout Session completes),
     * create the corresponding Tanso subscription and grant entitlements.
     * For IN_ARREARS subscriptions (already created via orchestrator), the idempotency check skips.
     */
    @Transactional
    protected void handleStripeIntegrationSubscriptionCreated(com.stripe.model.Subscription stripeSub, String accountId) {
        // Idempotency: skip if bridge already exists (IN_ARREARS subs are created by orchestrator first)
        if (stripeSubscriptionRepository.existsStripeSubscriptionByStripeSubscriptionExternalId(stripeSub.getId())) {
            log.info("STRIPE_INTEGRATION: Subscription {} already mapped, skipping", stripeSub.getId());
            return;
        }

        Account account = accountRepository.findById(UUID.fromString(accountId)).orElse(null);
        if (account == null) return;

        // Resolve customer and plan from metadata (set during checkout session creation)
        String tansoCustomerId = stripeSub.getMetadata().get("tanso_customer_id");
        String tansoPlanId = stripeSub.getMetadata().get("tanso_plan_id");

        if (tansoCustomerId == null || tansoPlanId == null) {
            // Fallback to bridge-table resolution (like STRIPE_DRIVEN)
            log.warn("STRIPE_INTEGRATION: Missing metadata on subscription {}, falling back to bridge resolution", stripeSub.getId());
            handleStripeDrivenSubscriptionCreated(stripeSub, accountId);
            return;
        }

        Customer tansoCustomer;
        try {
            tansoCustomer = customerService.validateAndRetrieveCustomer(tansoCustomerId, accountId);
        } catch (Exception e) {
            log.error("STRIPE_INTEGRATION: Customer {} not found for subscription {}: {}", tansoCustomerId, stripeSub.getId(), e.getMessage());
            return;
        }

        Plan plan = planService.retrievePlan(account, UUID.fromString(tansoPlanId));
        if (plan == null) {
            log.error("STRIPE_INTEGRATION: Plan {} not found for subscription {}", tansoPlanId, stripeSub.getId());
            return;
        }

        Subscription tansoSub = new Subscription();
        tansoSub.setCustomer(tansoCustomer);
        tansoSub.setPlan(plan);
        tansoSub.setAccount(account);
        tansoSub.setIntervalMonths(plan.getIntervalMonths());
        tansoSub.setIsActive("active".equals(stripeSub.getStatus()));
        syncPeriodFromStripeSubscription(stripeSub, tansoSub);
        tansoSub.setBillingAnchorDay((short) java.time.LocalDate.now().getDayOfMonth());
        subscriptionRepository.save(tansoSub);

        StripeSubscription bridge = new StripeSubscription();
        bridge.setSubscription(tansoSub);
        bridge.setAccount(account);
        bridge.setStripeSubscriptionExternalId(stripeSub.getId());
        stripeSubscriptionRepository.save(bridge);

        if (tansoSub.getIsActive()) {
            entitlementService.processEntitlementsForSubscription(tansoSub);
            creditService.processCreditGrantsForSubscription(tansoSub);
        }

        log.info("STRIPE_INTEGRATION: Created Tanso subscription {} from Stripe subscription {} (checkout-first flow)",
                tansoSub.getId(), stripeSub.getId());
    }

    /**
     * STRIPE_DRIVEN: Sync period dates and status changes from Stripe.
     */
    @Transactional
    protected void handleStripeDrivenSubscriptionUpdated(com.stripe.model.Subscription stripeSub) {
        StripeSubscription bridge = stripeSubscriptionRepository
                .findStripeSubscriptionByStripeSubscriptionExternalId(stripeSub.getId());
        if (bridge == null) {
            log.debug("STRIPE_DRIVEN: No mapped subscription for Stripe sub {}, ignoring update", stripeSub.getId());
            return;
        }

        Subscription subscription = bridge.getSubscription();

        // Sync period dates from first subscription item
        syncPeriodFromStripeSubscription(stripeSub, subscription);

        // Sync status
        String status = stripeSub.getStatus();
        if ("canceled".equals(status) || "unpaid".equals(status)) {
            if (Boolean.TRUE.equals(subscription.getIsActive())) {
                subscription.setIsActive(false);
                subscription.setCancelledAt(Instant.now());
                entitlementService.processEntitlementRevokeForSubscription(subscription);
                log.info("STRIPE_DRIVEN: Deactivated subscription {} due to Stripe status: {}", subscription.getId(), status);
            }
        } else if ("active".equals(status) && !Boolean.TRUE.equals(subscription.getIsActive())) {
            subscription.setIsActive(true);
            entitlementService.processEntitlementsForSubscription(subscription);
            log.info("STRIPE_DRIVEN: Reactivated subscription {} from Stripe status: active", subscription.getId());
        }

        subscriptionRepository.save(subscription);
    }

    /**
     * STRIPE_DRIVEN: Cancel Tanso subscription and revoke entitlements.
     */
    @Transactional
    protected void handleStripeDrivenSubscriptionDeleted(com.stripe.model.Subscription stripeSub) {
        StripeSubscription bridge = stripeSubscriptionRepository
                .findStripeSubscriptionByStripeSubscriptionExternalId(stripeSub.getId());
        if (bridge == null) {
            log.warn("STRIPE_DRIVEN: No mapped subscription for deleted Stripe sub {}", stripeSub.getId());
            return;
        }

        Subscription subscription = bridge.getSubscription();
        subscription.setIsActive(false);
        subscription.setCancelledAt(Instant.now());
        subscription.setCancelEffectiveAt(Instant.now());
        subscriptionRepository.save(subscription);

        entitlementService.processEntitlementRevokeForSubscription(subscription);
        log.info("STRIPE_DRIVEN: Deactivated Tanso subscription {} from Stripe subscription deletion {}", subscription.getId(), stripeSub.getId());
    }

    /**
     * STRIPE_DRIVEN: When Stripe creates an invoice (from a subscription), mirror it in Tanso.
     * Resolves subscription via bridge tables (not metadata).
     */
    @Transactional
    protected void handleStripeDrivenInvoiceCreated(Invoice stripeInvoice, String accountId) {
        String stripeSubId = extractStripeSubscriptionId(stripeInvoice);
        if (stripeSubId == null) {
            log.debug("STRIPE_DRIVEN: invoice.created has no subscription, skipping");
            return;
        }

        StripeSubscription bridge = stripeSubscriptionRepository
                .findStripeSubscriptionByStripeSubscriptionExternalId(stripeSubId);
        if (bridge == null) {
            log.debug("STRIPE_DRIVEN: No mapped subscription for invoice.created, Stripe sub {}", stripeSubId);
            return;
        }

        // Idempotency: skip if already linked
        if (stripeSyncService.stripeInvoiceLinked(stripeInvoice.getId())) {
            log.debug("STRIPE_DRIVEN: Stripe invoice {} already linked, skipping", stripeInvoice.getId());
            return;
        }

        Subscription subscription = bridge.getSubscription();
        BigDecimal amount = BigDecimal.valueOf(stripeInvoice.getAmountDue()).movePointLeft(2);

        // Extract period from line items
        Instant periodStart = subscription.getCurrentPeriodStart();
        Instant periodEnd = subscription.getCurrentPeriodEnd();
        Instant[] stripePeriod = extractPeriodFromStripeInvoice(stripeInvoice);
        if (stripePeriod != null) {
            periodStart = stripePeriod[0];
            periodEnd = stripePeriod[1];
        }

        // Map Stripe status to Tanso status
        InvoiceStatus tansoStatus = mapStripeInvoiceStatus(stripeInvoice.getStatus());

        var invoiceDto = invoiceService.createNewInvoice(subscription, LocalDate.now(ZoneOffset.UTC), amount, tansoStatus,
                periodStart, periodEnd);
        stripeSyncService.saveStripeInvoice(stripeInvoice.getId(), invoiceDto.getId(), accountId);

        log.info("STRIPE_DRIVEN: Mirrored Stripe invoice {} to Tanso invoice {} (amount={}, status={})",
                stripeInvoice.getId(), invoiceDto.getId(), amount, tansoStatus);
    }

    /**
     * STRIPE_DRIVEN: When a Stripe invoice is paid, mirror it if needed, mark as PAID, and process credit grants.
     */
    @Transactional
    protected void handleStripeDrivenInvoicePaid(Invoice stripeInvoice, String accountId) {
        String stripeSubId = extractStripeSubscriptionId(stripeInvoice);
        if (stripeSubId == null) {
            log.debug("STRIPE_DRIVEN: invoice.paid has no subscription, skipping");
            return;
        }

        StripeSubscription bridge = stripeSubscriptionRepository
                .findStripeSubscriptionByStripeSubscriptionExternalId(stripeSubId);
        if (bridge == null) {
            log.debug("STRIPE_DRIVEN: No mapped subscription for invoice.paid, Stripe sub {}", stripeSubId);
            return;
        }

        // If not yet linked (race: invoice.paid arrived before invoice.created), create the mirror first
        if (!stripeSyncService.stripeInvoiceLinked(stripeInvoice.getId())) {
            handleStripeDrivenInvoiceCreated(stripeInvoice, accountId);
        }

        // Mark as PAID
        var stripeInvoiceEntity = stripeSyncService.retrieveStripeInvoiceLinkedData(stripeInvoice.getId());
        if (stripeInvoiceEntity != null && stripeInvoiceEntity.getInvoice() != null) {
            String invoiceId = stripeInvoiceEntity.getInvoice().getId().toString();
            invoiceService.markInvoiceAsPaid(invoiceId);
            eventPublisher.publishEvent(new InvoicePaidEvent(UUID.fromString(accountId), UUID.fromString(invoiceId)));
        }

        Subscription subscription = bridge.getSubscription();
        try {
            creditService.processCreditGrantsForSubscription(subscription);
        } catch (Exception e) {
            log.error("STRIPE_DRIVEN: Failed to process credit grant for subscription {} on invoice.paid: {}",
                    subscription.getId(), e.getMessage(), e);
        }

        log.info("STRIPE_DRIVEN: Processed invoice.paid for subscription {} from Stripe invoice {}", subscription.getId(), stripeInvoice.getId());
    }

    /**
     * STRIPE_DRIVEN: When invoice payment fails, mark the mirrored invoice as PAST_DUE.
     */
    @Transactional
    protected void handleStripeDrivenInvoicePaymentFailed(Invoice stripeInvoice) {
        String stripeSubId = extractStripeSubscriptionId(stripeInvoice);
        log.warn("STRIPE_DRIVEN: Invoice payment failed for Stripe invoice {} (subscription: {})",
                stripeInvoice.getId(), stripeSubId != null ? stripeSubId : "unknown");

        // Mark linked Tanso invoice as PAST_DUE if it exists
        if (stripeSyncService.stripeInvoiceLinked(stripeInvoice.getId())) {
            var stripeInvoiceEntity = stripeSyncService.retrieveStripeInvoiceLinkedData(stripeInvoice.getId());
            if (stripeInvoiceEntity != null && stripeInvoiceEntity.getInvoice() != null) {
                com.tansoflow.tansocore.entity.Invoice tansoInvoice = stripeInvoiceEntity.getInvoice();
                tansoInvoice.setStatus(InvoiceStatus.PAST_DUE.name());
                log.info("STRIPE_DRIVEN: Marked Tanso invoice {} as PAST_DUE from Stripe invoice {}", tansoInvoice.getId(), stripeInvoice.getId());
            }
        }
    }

    /**
     * STRIPE_DRIVEN: When a customer is created in Stripe, auto-create a Tanso Customer and bridge record.
     */
    @Transactional
    protected void handleStripeDrivenCustomerCreated(com.stripe.model.Customer stripeCustomer, String accountId) {
        Account account = accountRepository.findById(UUID.fromString(accountId)).orElse(null);
        if (account == null) return;

        // Idempotency: skip if already mapped
        if (stripeCustomerRepository.existsByStripeCustomerExternalIdAndAccount(stripeCustomer.getId(), account)) {
            log.info("STRIPE_DRIVEN: Customer {} already mapped, skipping", stripeCustomer.getId());
            return;
        }

        CustomerDto dto = new CustomerDto();
        dto.setCustomerReferenceId(stripeCustomer.getId());
        dto.setEmail(stripeCustomer.getEmail() != null && !stripeCustomer.getEmail().isBlank()
                ? stripeCustomer.getEmail()
                : stripeCustomer.getId() + "@stripe.placeholder");
        dto.setFirstName(extractFirstName(stripeCustomer.getName()));
        dto.setLastName(extractLastName(stripeCustomer.getName()));
        dto.setPhoneNumber(stripeCustomer.getPhone());

        Customer tansoCustomer = customerService.createCustomer(account, dto);

        StripeCustomer bridge = new StripeCustomer();
        bridge.setAccount(account);
        bridge.setCustomer(tansoCustomer);
        bridge.setStripeCustomerExternalId(stripeCustomer.getId());
        bridge.setSyncedAt(Instant.now());
        stripeCustomerRepository.save(bridge);

        log.info("STRIPE_DRIVEN: Auto-created Tanso customer {} from Stripe customer {}", tansoCustomer.getId(), stripeCustomer.getId());
    }

    private String extractFirstName(String name) {
        if (name == null || name.isBlank()) return "Unknown";
        String[] parts = name.trim().split("\\s+", 2);
        return parts[0];
    }

    private String extractLastName(String name) {
        if (name == null || name.isBlank()) return "";
        String[] parts = name.trim().split("\\s+", 2);
        return parts.length > 1 ? parts[1] : "";
    }

    /**
     * Syncs period dates from a Stripe subscription's first item to a Tanso subscription.
     * In Stripe SDK v31+, period is per-item, not on the Subscription object.
     */
    private void syncPeriodFromStripeSubscription(com.stripe.model.Subscription stripeSub, Subscription tansoSub) {
        if (stripeSub.getItems() != null && stripeSub.getItems().getData() != null
                && !stripeSub.getItems().getData().isEmpty()) {
            var firstItem = stripeSub.getItems().getData().getFirst();
            if (firstItem.getCurrentPeriodStart() != null) {
                tansoSub.setCurrentPeriodStart(Instant.ofEpochSecond(firstItem.getCurrentPeriodStart()));
            }
            if (firstItem.getCurrentPeriodEnd() != null) {
                tansoSub.setCurrentPeriodEnd(Instant.ofEpochSecond(firstItem.getCurrentPeriodEnd()));
            }
        }
    }

    private Plan resolveStripeDrivenPlan(com.stripe.model.Subscription stripeSub, Account account) {
        if (stripeSub.getItems() == null || stripeSub.getItems().getData().isEmpty()) {
            return null;
        }
        var item = stripeSub.getItems().getData().getFirst();
        if (item.getPrice() == null || item.getPrice().getProduct() == null) {
            return null;
        }
        String stripeProductId = item.getPrice().getProduct();
        StripeProduct stripeProduct = stripeProductPlansRepository.findByStripeProductExternalIdAndAccount(stripeProductId, account);
        return stripeProduct != null ? stripeProduct.getPlan() : null;
    }

    // ─── Utility methods ────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private <T extends StripeObject> T deserializeEvent(Event event, Class<T> clazz) {
        return clazz.cast(
                event.getDataObjectDeserializer().getObject()
                        .or(() -> {
                            try {
                                return Optional.of(event.getDataObjectDeserializer().deserializeUnsafe());
                            } catch (Exception e) {
                                log.warn("deserializeUnsafe failed for {}: {}", event.getType(), e.getMessage());
                                return Optional.empty();
                            }
                        })
                        .orElseThrow(() -> new IllegalStateException("Unable to deserialize " + event.getType()))
        );
    }

    /**
     * Resolves a Tanso Subscription from a Stripe subscription by checking metadata first,
     * then falling back to the StripeSubscription bridge table.
     */
    private Subscription resolveSubscriptionFromStripeSubMetadataOrBridge(com.stripe.model.Subscription stripeSub, String accountId) {
        // Try metadata first (set by orchestrator-created subscriptions)
        String subscriptionId = stripeSub.getMetadata().get("tanso_subscription_id");
        if (subscriptionId != null) {
            try {
                return subscriptionService.getSubscriptionById(subscriptionId, accountId);
            } catch (Exception e) {
                log.debug("Could not resolve subscription {} from metadata: {}", subscriptionId, e.getMessage());
            }
        }

        // Fall back to bridge table (checkout-first flow — subscription created by webhook)
        StripeSubscription bridge = stripeSubscriptionRepository
                .findStripeSubscriptionByStripeSubscriptionExternalId(stripeSub.getId());
        if (bridge != null && bridge.getSubscription() != null) {
            return bridge.getSubscription();
        }

        return null;
    }

    private StripeMode getStripeMode(UUID accountId) {
        AccountSetting setting = accountSettingRepository.findAccountSettingById(accountId);
        if (setting == null) return StripeMode.NONE;
        return setting.getStripeMode();
    }

    private String extractMetadata(Invoice stripeInvoice, String key) {
        String value = stripeInvoice.getMetadata().get(key);
        if (value == null && stripeInvoice.getParent() != null
                && stripeInvoice.getParent().getSubscriptionDetails() != null) {
            value = stripeInvoice.getParent().getSubscriptionDetails().getMetadata().get(key);
        }
        return value;
    }

    private InvoiceStatus mapStripeInvoiceStatus(String stripeStatus) {
        if (stripeStatus == null) return InvoiceStatus.DUE;
        return switch (stripeStatus) {
            case "paid" -> InvoiceStatus.PAID;
            case "open" -> InvoiceStatus.DUE;
            case "draft" -> InvoiceStatus.PENDING;
            case "void" -> InvoiceStatus.VOID;
            case "uncollectible" -> InvoiceStatus.PAST_DUE;
            default -> InvoiceStatus.DUE;
        };
    }

    private String extractStripeSubscriptionId(Invoice stripeInvoice) {
        if (stripeInvoice.getParent() != null
                && stripeInvoice.getParent().getSubscriptionDetails() != null) {
            return stripeInvoice.getParent().getSubscriptionDetails().getSubscription();
        }
        return null;
    }

    /**
     * Extract billing period [start, end] from a Stripe invoice's line items.
     * Returns null if the period cannot be determined.
     */
    private Instant[] extractPeriodFromStripeInvoice(Invoice stripeInvoice) {
        if (stripeInvoice.getLines() != null && stripeInvoice.getLines().getData() != null
                && !stripeInvoice.getLines().getData().isEmpty()) {
            var lineItem = stripeInvoice.getLines().getData().getFirst();
            if (lineItem.getPeriod() != null
                    && lineItem.getPeriod().getStart() != null
                    && lineItem.getPeriod().getEnd() != null) {
                return new Instant[]{
                        Instant.ofEpochSecond(lineItem.getPeriod().getStart()),
                        Instant.ofEpochSecond(lineItem.getPeriod().getEnd())
                };
            }
        }
        return null;
    }
}
