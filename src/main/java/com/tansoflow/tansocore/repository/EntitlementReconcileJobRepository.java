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

import com.tansoflow.tansocore.entity.EntitlementReconcileJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EntitlementReconcileJobRepository extends JpaRepository<EntitlementReconcileJob, UUID> {

    /**
     * Upsert a reconcile job for (account, plan, feature).
     * - Inserts new PENDING job if none exists.
     * - If a PENDING/IN_PROGRESS job exists, updates modified_at (coalescing).
     */
    @Modifying
    @Query(value = """
            INSERT INTO entitlement_reconcile_jobs (
                job_id,
                account_id,
                plan_id,
                feature_id,
                status,
                attempts,
                processed_count,
                created_at,
                modified_at
            )
            VALUES (
                gen_random_uuid(),
                :accountId,
                :planId,
                :featureId,
                'PENDING',
                0,
                0,
                now(),
                now()
            )
            ON CONFLICT (account_id, plan_id, feature_id)
            DO UPDATE
               SET status         = 'PENDING',
                   attempts       = 0,
                   processed_count = 0,
                   modified_at    = EXCLUDED.modified_at
            """,
            nativeQuery = true)
    void upsertActiveJob(@Param("accountId") UUID accountId,
                         @Param("planId") UUID planId,
                         @Param("featureId") UUID featureId);


    /**
     * Fetches one pending job and locks it so no other worker can take it.
     * The partial unique index ensures only one active job exists per
     * (accountId, planId, featureId).
     * This query:
     *  - selects next PENDING job
     *  - orders by created_at for fairness
     *  - FOR UPDATE SKIP LOCKED => allows concurrent workers
     */
    @Query(value = """
            SELECT *
            FROM entitlement_reconcile_jobs
            WHERE status = 'PENDING'
            ORDER BY created_at
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """,
            nativeQuery = true)
    Optional<EntitlementReconcileJob> lockNextPending();
}
