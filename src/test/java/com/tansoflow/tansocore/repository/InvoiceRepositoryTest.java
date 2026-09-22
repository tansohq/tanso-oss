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
import com.tansoflow.tansocore.model.billing.type.InvoiceStatus;
import com.tansoflow.tansocore.service.internal.monetization.InvoiceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

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
