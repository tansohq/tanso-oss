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
package com.tansoflow.tansocore.jobs;

import com.tansoflow.tansocore.jobs.scheduler.invoice.InvoiceDueJob;
import com.tansoflow.tansocore.jobs.scheduler.invoice.InvoicePendingJob;
import com.tansoflow.tansocore.jobs.scheduler.subscription.SubscriptionCycleJob;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Tag("manual")
class SchedulerIntegrationTest {

    @Autowired
    private SubscriptionCycleJob subscriptionCycleJob;

    @Autowired
    private InvoicePendingJob invoicePendingJob;

    @Autowired
    private InvoiceDueJob invoiceDueJob;

    @Test
    void runSubscriptionCycleJob() {
        subscriptionCycleJob.run();
    }

    @Test
    void runInvoicePendingJob() {
        invoicePendingJob.run();
    }

    @Test
    void runInvoiceDueJob() {
        invoiceDueJob.run();
    }
}
