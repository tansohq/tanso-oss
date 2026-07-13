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

import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.entity.SubscriptionScheduledChange;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionScheduledChangeRepository extends JpaRepository<SubscriptionScheduledChange, UUID> {
    @Query(value = "SELECT ssc FROM SubscriptionScheduledChange ssc " +
            "WHERE ssc.effectiveAt <= :effectiveAt " +
            "AND (ssc.status = 'PENDING' " +
            "OR ssc.status = 'FAILED') " +
            "AND ssc.type = 'DOWNGRADE' " +
            "AND NOT EXISTS (" +
            "  SELECT 1 FROM AccountSetting acs " +
            "  WHERE acs.accounts.id = ssc.subscription.account.id " +
            "    AND acs.platformMode = 'OBSERVE'" +
            ")")
    Page<SubscriptionScheduledChange> findScheduledSubscriptionsForDowngrade(Instant effectiveAt, Pageable pageable);

    @Modifying
    @Query("""
    UPDATE SubscriptionScheduledChange ssc
       SET ssc.status = 'CANCELLED'
     WHERE ssc.subscription = :subscription
       AND ssc.status = 'PENDING'
""")
    void cancelAllScheduledChanges(Subscription subscription);

    @Query("SELECT ssc FROM SubscriptionScheduledChange ssc WHERE ssc.subscription.account.id = :accountId AND ssc.status = 'PENDING'")
    List<SubscriptionScheduledChange> findAllPendingChangesByAccountId(UUID accountId);

    @Query("SELECT ssc FROM SubscriptionScheduledChange ssc WHERE ssc.subscription = :subscription AND ssc.status = 'PENDING' AND ssc.type = 'UPGRADE'")
    Optional<SubscriptionScheduledChange> findPendingUpgradeBySubscription(Subscription subscription);

    boolean existsSubscriptionScheduledChangeBySubscriptionIn(Collection<Subscription> subscriptions);

    List<SubscriptionScheduledChange> findSubscriptionScheduledChangesByStatusAndSubscriptionIsIn(String status, Collection<Subscription> subscriptions);
}
