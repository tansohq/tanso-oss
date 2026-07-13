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
package com.tansoflow.tansocore.application.orchestrator;

import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.repository.EntitlementReconcileJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class EntitlementOrchestratorImpl implements EntitlementOrchestrator {
    private final EntitlementReconcileJobRepository jobRepository;

    /**
     * Handle entitlement request coming from a data source. CURRENTLY THIS IS USED ONLY BY STRIPE WEBHOOK PROCESSING
     * TODO: Maybe rename this and make it specific(?) Not liking how it handles w/ entitlement entity creation
     *
     * @param customerUuid - customer uuid
     * @param accountUuid  - account (tenant) owner uuid
     * @param subscription - Tanso subscription object
     */
    @Override
    public void handleStripeEntitlementRequest(String customerUuid, String accountUuid, Subscription subscription) {
        // TODO: Check if entitlement exists (?) before creating entitlement
        log.info("Handling entitlement request for customerUuid={}, planUuid={}, accountUuid={}", customerUuid, subscription.getPlan().getId(), accountUuid);
    }

    /**
     * Enqueue a reconciled job for (account, plan, feature).
     * Uses an upsert so multiple calls in a short time window just coalesce into
     * a single active job.
     */
    @Transactional
    public void enqueue(UUID accountId, UUID planId, UUID featureId) {
        jobRepository.upsertActiveJob(accountId, planId, featureId);
    }

    // TODO: This should check and clean up entitlements
    @Override
    public void entitlementResolver() {
        log.info("Running entitlement resolver");
    }



}
