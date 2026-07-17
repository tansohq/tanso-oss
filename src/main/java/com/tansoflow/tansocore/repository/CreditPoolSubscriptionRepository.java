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

import com.tansoflow.tansocore.entity.CreditPoolSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreditPoolSubscriptionRepository extends JpaRepository<CreditPoolSubscription, UUID> {

    @Query("SELECT cps FROM CreditPoolSubscription cps WHERE cps.creditPool.id = :poolId AND cps.deletedAt IS NULL")
    List<CreditPoolSubscription> findByCreditPoolId(@Param("poolId") UUID poolId);

    @Query("SELECT cps FROM CreditPoolSubscription cps WHERE cps.subscription.id = :subscriptionId AND cps.deletedAt IS NULL")
    List<CreditPoolSubscription> findBySubscriptionId(@Param("subscriptionId") UUID subscriptionId);

    @Query("""
        SELECT cps FROM CreditPoolSubscription cps
        WHERE cps.subscription.id = :subscriptionId
          AND cps.deletedAt IS NULL
        ORDER BY cps.drawPriority ASC
    """)
    List<CreditPoolSubscription> findBySubscriptionIdOrderByDrawPriority(@Param("subscriptionId") UUID subscriptionId);

    @Query("""
        SELECT cps FROM CreditPoolSubscription cps
        WHERE cps.subscription.id = :subscriptionId
          AND cps.account.id = :accountId
          AND cps.creditPool.denomination = :denomination
          AND cps.deletedAt IS NULL
        ORDER BY cps.drawPriority ASC
    """)
    List<CreditPoolSubscription> findBySubscriptionIdAndAccountIdAndDenominationOrderByDrawPriority(
            @Param("subscriptionId") UUID subscriptionId,
            @Param("accountId") UUID accountId,
            @Param("denomination") String denomination);

    Optional<CreditPoolSubscription> findByCreditPoolIdAndSubscriptionIdAndDeletedAtIsNull(UUID creditPoolId, UUID subscriptionId);

    boolean existsByCreditPoolIdAndSubscriptionIdAndDeletedAtIsNull(UUID creditPoolId, UUID subscriptionId);
}
