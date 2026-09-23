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
package com.tansoflow.tansocore.repository;

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.Invoice;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.model.api.external.StripeMode;
import com.tansoflow.tansocore.model.billing.type.InvoiceSource;
import com.tansoflow.tansocore.model.billing.type.InvoiceStatus;
import com.tansoflow.tansocore.service.internal.monetization.InvoiceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InvoiceRepositoryTest {

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Cancel IMMEDIATE and the scheduled downgrade both void through this.
    @Autowired
    private InvoiceService invoiceService;

    private final Instant now = Instant.now();
    private Plan plan;
    private Subscription subscription;

    @BeforeEach
    void setUp() {
        // Tests run without Liquibase, so a local database can lag invoices.source (changelog 2026.09.23.40), which
        // every Invoice save writes. Added inside the test transaction, so it rolls back with it.
        jdbcTemplate.execute("ALTER TABLE invoices ADD COLUMN IF NOT EXISTS source VARCHAR(16) NOT NULL DEFAULT 'TANSO'");

        Account account = new Account();
        account.setName("Invoice Repo Test " + System.nanoTime());
        account = accountRepository.save(account);

        Customer customer = new Customer();
        customer.setAccount(account);
        customer = customerRepository.save(customer);

        plan = new Plan();
        plan.setAccount(account);
        plan.setKey("starter-" + System.nanoTime());
        plan.setName("Starter");
        plan.setStatus("ACTIVE");
        plan = planRepository.save(plan);

        subscription = new Subscription();
        subscription.setAccount(account);
        subscription.setCustomer(customer);
        subscription.setPlan(plan);
        subscription = subscriptionRepository.save(subscription);
    }

    // A past period's PAST_DUE is owed for service already used; voiding it on cancel or downgrade forgave the debt.
    // One for the period still running, or with the period ending exactly now, is decided by invoicePeriodEnd.
    @Test
    void voidableInvoicesTakePastDueOnlyForAPeriodThatHasNotEnded() {
        Invoice due = invoice(InvoiceStatus.DUE, now.minus(40, ChronoUnit.DAYS));
        Invoice pending = invoice(InvoiceStatus.PENDING, now.plus(30, ChronoUnit.DAYS));
        Invoice pastDueCurrentPeriod = invoice(InvoiceStatus.PAST_DUE, now.plus(1, ChronoUnit.SECONDS));
        invoice(InvoiceStatus.PAST_DUE, now);
        invoice(InvoiceStatus.PAST_DUE, now.minus(30, ChronoUnit.DAYS));
        invoice(InvoiceStatus.PAST_DUE, null);
        invoice(InvoiceStatus.PAID, now.plus(30, ChronoUnit.DAYS));
        invoice(InvoiceStatus.VOID, now.plus(30, ChronoUnit.DAYS));

        List<Invoice> voidable = invoiceRepository.findVoidableInvoicesBySubscription(subscription, now);

        assertThat(voidable).extracting(Invoice::getId)
                .containsExactlyInAnyOrder(due.getId(), pending.getId(), pastDueCurrentPeriod.getId());
    }

    // The proration behind an upgrade still waiting on payment buys nothing once the subscription is cancelled or
    // moved, so it is voided even when past due and even when its period has ended.
    @Test
    void voidableInvoicesTakeThePastDueProrationOfAWaitingUpgrade() {
        Invoice proration = invoice(InvoiceStatus.PAST_DUE, now.minus(1, ChronoUnit.DAYS));
        upgradeWaitingOn(proration, "PENDING");
        Invoice fulfilledProration = invoice(InvoiceStatus.PAST_DUE, now.minus(1, ChronoUnit.DAYS));
        upgradeWaitingOn(fulfilledProration, "COMPLETED");

        List<Invoice> voidable = invoiceRepository.findVoidableInvoicesBySubscription(subscription, now);

        assertThat(voidable).extracting(Invoice::getId).containsExactly(proration.getId());
    }

    @Test
    void voidingOutstandingInvoicesLeavesAPastPeriodsDebtPayable() {
        Invoice pastPeriod = invoice(InvoiceStatus.PAST_DUE, now.minus(30, ChronoUnit.DAYS));
        Invoice currentPeriod = invoice(InvoiceStatus.PAST_DUE, now.plus(30, ChronoUnit.DAYS));

        invoiceService.voidOutstandingInvoicesForSubscription(subscription);

        assertThat(invoiceRepository.findById(pastPeriod.getId()).orElseThrow().getStatus())
                .isEqualTo(InvoiceStatus.PAST_DUE.name());
        assertThat(invoiceRepository.findById(currentPeriod.getId()).orElseThrow().getStatus())
                .isEqualTo(InvoiceStatus.VOID.name());
    }

    // Disconnecting Stripe resets the account's mode to NONE. The copies of Stripe's invoices left behind must stay
    // out of the pending and due jobs, which recalculate amounts; Tanso's own invoices, on a NONE account or on a
    // PAYMENT_PASS_THROUGH account where they are also linked to a Stripe invoice, stay in.
    @Test
    void theInvoiceJobsNeverSelectAStripeCopyWhateverTheAccountsModeIsNow() {
        hideTheDatabasesOtherInvoices();
        for (InvoiceStatus status : List.of(InvoiceStatus.PENDING, InvoiceStatus.DUE)) {
            Subscription disconnected = subscriptionOnAccountInMode(StripeMode.NONE, new BigDecimal("10.00"));
            Invoice stripeCopy = invoiceOn(disconnected, status, InvoiceSource.STRIPE, "in_copy_" + status);
            Invoice tansoOnNone = invoiceOn(disconnected, status, InvoiceSource.TANSO, null);

            Subscription passThrough = subscriptionOnAccountInMode(StripeMode.PAYMENT_PASS_THROUGH, new BigDecimal("10.00"));
            Invoice tansoPushedToStripe = invoiceOn(passThrough, status, InvoiceSource.TANSO, "in_pushed_" + status);

            Subscription stripeDriven = subscriptionOnAccountInMode(StripeMode.STRIPE_DRIVEN, new BigDecimal("10.00"));
            Invoice onStripeDriven = invoiceOn(stripeDriven, status, InvoiceSource.TANSO, null);

            List<Invoice> selected = invoiceRepository
                    .getInvoicesByStatusExcludingFullSyncPaged(status.name(), Pageable.unpaged())
                    .getContent();

            assertThat(selected).extracting(Invoice::getId)
                    .contains(tansoOnNone.getId(), tansoPushedToStripe.getId())
                    .doesNotContain(stripeCopy.getId(), onStripeDriven.getId());
        }
    }

    // What the selection above protects: the jobs reset a selected invoice's amount to the current plan price plus
    // Tanso's usage, which overwrote the amount Stripe charged.
    @Test
    void thePendingAndDueJobsLeaveAStripeCopysAmountAloneAfterDisconnect() {
        hideTheDatabasesOtherInvoices();
        Subscription disconnected = subscriptionOnAccountInMode(StripeMode.NONE, new BigDecimal("10.00"));
        usagePricedFeatureOn(disconnected.getPlan());
        Invoice pendingCopy = invoiceOn(disconnected, InvoiceStatus.PENDING, InvoiceSource.STRIPE, "in_pending_copy");
        Invoice dueCopy = invoiceOn(disconnected, InvoiceStatus.DUE, InvoiceSource.STRIPE, "in_due_copy");
        Invoice pendingTanso = invoiceOn(disconnected, InvoiceStatus.PENDING, InvoiceSource.TANSO, null);
        Invoice dueTanso = invoiceOn(disconnected, InvoiceStatus.DUE, InvoiceSource.TANSO, null);

        invoiceService.processPendingInvoices();
        invoiceService.processDueInvoices();

        assertThat(amountOf(pendingCopy)).isEqualByComparingTo("49.00");
        assertThat(amountOf(dueCopy)).isEqualByComparingTo("49.00");
        // No usage in the period, so Tanso's recalculation is the plan price.
        assertThat(amountOf(pendingTanso)).isEqualByComparingTo("10.00");
        assertThat(amountOf(dueTanso)).isEqualByComparingTo("10.00");
    }

    // The jobs scan every account's invoices, and the local database holds whatever other work left in it. Soft
    // deleted inside the test transaction, so it rolls back with it.
    private void hideTheDatabasesOtherInvoices() {
        jdbcTemplate.update("UPDATE invoices SET deleted_at = now() WHERE deleted_at IS NULL");
    }

    private Subscription subscriptionOnAccountInMode(StripeMode mode, BigDecimal planPrice) {
        Account account = new Account();
        account.setName("Invoice Source Test " + System.nanoTime());
        account = accountRepository.saveAndFlush(account);
        jdbcTemplate.update("INSERT INTO account_settings (account_id, stripe_mode) VALUES (?, ?)",
                account.getId(), mode.name());

        Customer customer = new Customer();
        customer.setAccount(account);
        customer = customerRepository.save(customer);

        Plan pricedPlan = new Plan();
        pricedPlan.setAccount(account);
        pricedPlan.setKey("priced-" + System.nanoTime());
        pricedPlan.setName("Priced");
        pricedPlan.setStatus("ACTIVE");
        pricedPlan.setPriceAmount(planPrice);
        pricedPlan = planRepository.save(pricedPlan);

        Subscription sub = new Subscription();
        sub.setAccount(account);
        sub.setCustomer(customer);
        sub.setPlan(pricedPlan);
        sub.setCurrentPeriodStart(now.minus(10, ChronoUnit.DAYS));
        sub.setCurrentPeriodEnd(now.plus(20, ChronoUnit.DAYS));
        return subscriptionRepository.save(sub);
    }

    // A usage-priced feature is what makes the jobs recalculate an invoice at all.
    private void usagePricedFeatureOn(Plan pricedPlan) {
        planRepository.flush();
        UUID featureId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO features (feature_id, account_id, key, name, description) VALUES (?, ?, ?, ?, ?)",
                featureId, pricedPlan.getAccount().getId(), "tokens-" + System.nanoTime(), "Tokens", "Tokens");
        jdbcTemplate.update("""
                INSERT INTO plan_feature_rules (id, plan_id, feature_id, value)
                VALUES (gen_random_uuid(), ?, ?, '{"model": "usage", "price_per_unit": 0.01}'::jsonb)
                """, pricedPlan.getId(), featureId);
    }

    private Invoice invoiceOn(Subscription sub, InvoiceStatus status, InvoiceSource source, String stripeInvoiceId) {
        Invoice invoice = new Invoice();
        invoice.setAccount(sub.getAccount());
        invoice.setSubscription(sub);
        invoice.setAmount(new BigDecimal("49.00"));
        invoice.setCurrency("USD");
        invoice.setDueDate(now);
        invoice.setStatus(status.name());
        invoice.setType("REGULAR");
        invoice.setInvoicePeriodStart(sub.getCurrentPeriodStart());
        invoice.setInvoicePeriodEnd(sub.getCurrentPeriodEnd());
        invoice.setSource(source.name());
        invoice = invoiceRepository.saveAndFlush(invoice);
        if (stripeInvoiceId != null) {
            jdbcTemplate.update("INSERT INTO stripe_invoices (stripe_invoice_external_id, invoice_id) VALUES (?, ?)",
                    stripeInvoiceId + "_" + System.nanoTime(), invoice.getId());
        }
        return invoice;
    }

    private BigDecimal amountOf(Invoice invoice) {
        invoiceRepository.flush();
        return jdbcTemplate.queryForObject("SELECT amount FROM invoices WHERE invoice_id = ?", BigDecimal.class,
                invoice.getId());
    }

    private Invoice invoice(InvoiceStatus status, Instant periodEnd) {
        Invoice invoice = new Invoice();
        invoice.setAccount(subscription.getAccount());
        invoice.setSubscription(subscription);
        invoice.setAmount(new BigDecimal("49.00"));
        invoice.setCurrency("USD");
        invoice.setDueDate(now);
        invoice.setStatus(status.name());
        invoice.setInvoicePeriodEnd(periodEnd);
        return invoiceRepository.save(invoice);
    }

    // Written with plain SQL naming only the columns the query reads: tests run without Liquibase, so a local
    // database can lag newer columns on this table (stripe_invoice_id, payment_url) that an entity save would write.
    private void upgradeWaitingOn(Invoice proration, String status) {
        invoiceRepository.flush();
        jdbcTemplate.update("""
                INSERT INTO subscription_scheduled_changes
                    (type, subscription_id, from_plan_id, to_plan_id, adjustment_invoice_id, status, effective_at)
                VALUES ('UPGRADE', ?, ?, ?, ?, ?, now())
                """, subscription.getId(), plan.getId(), plan.getId(), proration.getId(), status);
    }
}
