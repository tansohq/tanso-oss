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
