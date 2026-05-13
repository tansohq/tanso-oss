package com.tansoflow.tansocore.jobs.scheduler.entitlement;

import com.tansoflow.tansocore.repository.EntitlementReconcileJobRepository;
import com.tansoflow.tansocore.service.internal.monetization.PlanFeatureRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class EntitlementReconcileJobWorker {

    private final EntitlementReconcileJobRepository jobRepo;
    private final PlanFeatureRuleService planFeatureRuleService;
    private static final int MAX_LENGTH_ERROR_MSG = 2000;

    @Scheduled(fixedDelayString = "${jobs.backgroundJobsWithFixedDelay.reconciliationDelay}" )
    @Transactional
    public void processNextJob() {
        jobRepo.lockNextPending().ifPresent(job -> {
            job.setStatus("IN_PROGRESS");
            job.setAttempts((job.getAttempts() == null ? 0 : job.getAttempts()) + 1);
            job.setModifiedAt(Instant.now());

            try {
                UUID accountId = job.getAccount().getId();
                UUID planId    = job.getPlan().getId();
                UUID featureId = job.getFeature().getId();

                planFeatureRuleService.reconcilePlanFeature(accountId, planId, featureId, null);

                job.setStatus("COMPLETED");
            } catch (Exception ex) {
                job.setStatus("FAILED");
                job.setErrorMessage(truncate(ex.getMessage()));
            }

            job.setModifiedAt(Instant.now());
            jobRepo.save(job);
        });
    }

    private String truncate(String msg) {
        if (msg == null) return null;
        return msg.length() <= EntitlementReconcileJobWorker.MAX_LENGTH_ERROR_MSG ?
                msg : msg.substring(0, EntitlementReconcileJobWorker.MAX_LENGTH_ERROR_MSG);
    }
}

