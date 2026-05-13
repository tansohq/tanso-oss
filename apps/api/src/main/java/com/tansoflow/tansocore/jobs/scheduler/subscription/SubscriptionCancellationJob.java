package com.tansoflow.tansocore.jobs.scheduler.subscription;

import com.tansoflow.tansocore.service.internal.monetization.SubscriptionService;
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
public class SubscriptionCancellationJob {
    private final SubscriptionService subscriptionService;

    @Scheduled(cron = "${jobs.subscriptionCancellation.cron}")
    @SchedulerLock(name = "subscriptionCancellationJob", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void run() {
        try {
            log.info("Running Subscription Cancellation Job");
            subscriptionService.processCancellations();
        } catch (Exception e) {
            log.error("Job {} failed", getClass().getSimpleName(), e);
        }
    }
}
