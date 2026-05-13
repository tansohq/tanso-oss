package com.tansoflow.tansocore.application.orchestrator;

import com.tansoflow.tansocore.entity.Subscription;

import java.util.UUID;

public interface EntitlementOrchestrator {
    void handleStripeEntitlementRequest(String customerUuid, String accountUuid, Subscription subscription);

    /**
     * Enqueue a reconciled job for (account, plan, feature).
     * If a PENDING/IN_PROGRESS job already exists, just bumps modified_at.
     * Trigger: rule changes, maybe subscription lifecycle events.
     */
    void enqueue(UUID accountId, UUID planId, UUID featureId);

    void entitlementResolver();
}
