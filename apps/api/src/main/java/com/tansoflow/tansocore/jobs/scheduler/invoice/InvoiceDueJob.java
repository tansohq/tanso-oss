package com.tansoflow.tansocore.jobs.scheduler.invoice;

import com.tansoflow.tansocore.service.internal.monetization.InvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class InvoiceDueJob {
    private final InvoiceService invoiceService;

    @Scheduled(cron = "${jobs.invoiceDue.cron}")
    @SchedulerLock(name = "invoiceDueJob", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    @Transactional
    public void run() {
        log.info("Running Invoice Due Job");
        invoiceService.processDueInvoices();
    }

}
