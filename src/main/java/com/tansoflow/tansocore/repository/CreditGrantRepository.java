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

import com.tansoflow.tansocore.entity.CreditGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface CreditGrantRepository extends JpaRepository<CreditGrant, UUID> {

    @Query("SELECT cg FROM CreditGrant cg WHERE cg.creditPool.id = :poolId AND cg.deletedAt IS NULL ORDER BY cg.createdAt ASC")
    List<CreditGrant> findByCreditPoolId(@Param("poolId") UUID poolId);

    @Query("""
        SELECT cg FROM CreditGrant cg
        WHERE cg.creditPool.id = :poolId
          AND cg.remaining > 0
          AND cg.voidedAt IS NULL
          AND cg.deletedAt IS NULL
        ORDER BY cg.createdAt ASC
    """)
    List<CreditGrant> findActiveGrantsByPoolIdOrderByCreatedAsc(@Param("poolId") UUID poolId);

    @Query("""
        SELECT cg FROM CreditGrant cg
        WHERE cg.expiresAt IS NOT NULL
          AND cg.expiresAt <= :now
          AND cg.remaining > 0
          AND cg.voidedAt IS NULL
          AND cg.deletedAt IS NULL
    """)
    List<CreditGrant> findExpiredGrantsWithRemaining(@Param("now") Instant now);

    @Query("""
        SELECT cg FROM CreditGrant cg
        WHERE cg.creditPool.id = :poolId
          AND cg.subscription.id = :subscriptionId
          AND cg.grantType = 'PLAN_INCLUDED'
          AND cg.remaining > 0
          AND cg.voidedAt IS NULL
          AND cg.deletedAt IS NULL
    """)
    List<CreditGrant> findActivePlanIncludedGrants(
            @Param("poolId") UUID poolId,
            @Param("subscriptionId") UUID subscriptionId);

    boolean existsByAccountIdAndIdempotencyKeyAndDeletedAtIsNull(UUID accountId, String idempotencyKey);
}
