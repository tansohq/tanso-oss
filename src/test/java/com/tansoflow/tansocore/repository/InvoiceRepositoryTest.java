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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
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

    // Cancel, downgrade and free-plan retirement void what this returns. A PAST_DUE invoice left out stayed payable
    // after the subscription behind it was gone.
    @Test
    void outstandingInvoicesIncludeEveryStatusACustomerCanStillPay() {
        Account account = new Account();
        account.setName("Invoice Repo Test " + System.nanoTime());
        account = accountRepository.save(account);

        Customer customer = new Customer();
        customer.setAccount(account);
        customer = customerRepository.save(customer);

        Plan plan = new Plan();
        plan.setAccount(account);
        plan.setKey("starter-" + System.nanoTime());
        plan.setName("Starter");
        plan.setStatus("ACTIVE");
        plan = planRepository.save(plan);

        Subscription subscription = new Subscription();
        subscription.setAccount(account);
        subscription.setCustomer(customer);
        subscription.setPlan(plan);
        subscription = subscriptionRepository.save(subscription);

        Invoice due = invoiceWithStatus(subscription, InvoiceStatus.DUE);
        Invoice pending = invoiceWithStatus(subscription, InvoiceStatus.PENDING);
        Invoice pastDue = invoiceWithStatus(subscription, InvoiceStatus.PAST_DUE);
        invoiceWithStatus(subscription, InvoiceStatus.PAID);
        invoiceWithStatus(subscription, InvoiceStatus.VOID);

        List<Invoice> outstanding = invoiceRepository.findOutstandingInvoicesBySubscription(subscription);

        assertThat(outstanding).extracting(Invoice::getId)
                .containsExactlyInAnyOrder(due.getId(), pending.getId(), pastDue.getId());
    }

    private Invoice invoiceWithStatus(Subscription subscription, InvoiceStatus status) {
        Invoice invoice = new Invoice();
        invoice.setAccount(subscription.getAccount());
        invoice.setSubscription(subscription);
        invoice.setAmount(new BigDecimal("49.00"));
        invoice.setCurrency("USD");
        invoice.setDueDate(Instant.now());
        invoice.setStatus(status.name());
        return invoiceRepository.save(invoice);
    }
}
