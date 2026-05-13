package com.tansoflow.tansocore.jobs.scheduler.credit;

import com.tansoflow.tansocore.service.internal.monetization.CreditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class CreditExpirationJob {
    private final CreditService creditService;

    @Scheduled(cron = "${jobs.creditExpiration.cron:0 */15 * * * *}")
    @SchedulerLock(name = "creditExpirationJob", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void run() {
        try {
            log.info("Running Credit Expiration Job");
            creditService.processExpiredGrants();
            log.info("Completed Credit Expiration Job");
        } catch (Exception e) {
            log.error("Job {} failed", getClass().getSimpleName(), e);
        }
    }
}
