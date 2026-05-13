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
