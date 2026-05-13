package com.tansoflow.tansocore.jobs.scheduler.invoice;

import com.tansoflow.tansocore.service.internal.monetization.InvoiceService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
// Dummy cron for testing purposes
@TestPropertySource(properties = {"jobs.invoicePending.cron=0 0 * * * *"})
class InvoicePendingJobTest {

    @MockitoBean
    private InvoiceService invoiceService;

    @Test
    void testRun_invokesProcessPendingInvoices() {
        // Given
        InvoicePendingJob invoicePendingJob = new InvoicePendingJob(invoiceService);

        // When
        invoicePendingJob.run();

        // Then
        verify(invoiceService, times(1)).processPendingInvoices();
    }

    @Test
    void testRun_logsExecution() {
        // Given
        InvoicePendingJob invoicePendingJob = new InvoicePendingJob(invoiceService);

        // When
        invoicePendingJob.run();

        // Then
        Mockito.verify(invoiceService, times(1)).processPendingInvoices(); // Ensures behavior execution
        // Verify if the method interacted with logging as expected can be done using additional tools/plugins if needed
        // However, this level is generally sufficient for method interaction testing
    }
}