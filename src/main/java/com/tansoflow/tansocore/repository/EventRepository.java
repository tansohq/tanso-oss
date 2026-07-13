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

import com.tansoflow.tansocore.entity.Event;
import com.tansoflow.tansocore.model.event.events.type.EventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface EventRepository extends JpaRepository <Event, UUID>, JpaSpecificationExecutor<Event> {
    Page<Event> findByAccountId(UUID accountId, Pageable pageable);

    boolean existsByAccountIdAndEventIdempotencyKey(UUID accountId, String eventIdempotencyKey);

    @Query("SELECT e FROM Event e WHERE e.customerId = :customerId AND e.eventType IN :eventTypes AND e.occurredAt >= :start AND e.occurredAt < :end")
    List<Event> findEventsForBilling(
            @Param("customerId") UUID customerId,
            @Param("eventTypes") Collection<EventType> eventTypes,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    @Query("SELECT COALESCE(SUM(e.usageUnits), 0) FROM Event e " +
           "WHERE e.customerId = :customerId " +
           "AND e.eventName = :eventName " +
           "AND e.occurredAt >= :since")
    java.math.BigDecimal sumUsageUnitsByCustomerAndEventNameSince(
            @Param("customerId") UUID customerId,
            @Param("eventName") String eventName,
            @Param("since") Instant since);

    @Query("SELECT COALESCE(SUM(e.usageUnits), 0) FROM Event e " +
           "WHERE e.customerId = :customerId " +
           "AND e.featureId = :featureId " +
           "AND e.occurredAt >= :since")
    java.math.BigDecimal sumUsageUnitsByCustomerAndFeatureIdSince(
            @Param("customerId") UUID customerId,
            @Param("featureId") UUID featureId,
            @Param("since") Instant since);

    @Query("SELECT e FROM Event e WHERE e.customerId = :customerId " +
           "AND e.subscriptionId = :subscriptionId " +
           "AND e.eventType IN :eventTypes " +
           "AND e.occurredAt >= :start AND e.occurredAt < :end")
    List<Event> findEventsForBillingBySubscription(
            @Param("customerId") UUID customerId,
            @Param("subscriptionId") UUID subscriptionId,
            @Param("eventTypes") Collection<EventType> eventTypes,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("SELECT COALESCE(SUM(e.usageUnits), 0) FROM Event e " +
           "WHERE e.customerId = :customerId " +
           "AND e.subscriptionId = :subscriptionId " +
           "AND e.featureId = :featureId " +
           "AND e.occurredAt >= :since " +
           "AND e.occurredAt < :until")
    java.math.BigDecimal sumUsageUnitsBySubscriptionAndFeatureIdSince(
            @Param("customerId") UUID customerId,
            @Param("subscriptionId") UUID subscriptionId,
            @Param("featureId") UUID featureId,
            @Param("since") Instant since,
            @Param("until") Instant until);

    @Query("SELECT COALESCE(SUM(e.usageUnits), 0) FROM Event e " +
           "WHERE e.customerId = :customerId " +
           "AND (e.subscriptionId = :subscriptionId OR e.subscriptionId IS NULL) " +
           "AND e.featureId = :featureId " +
           "AND e.occurredAt >= :since " +
           "AND e.occurredAt < :until")
    java.math.BigDecimal sumUsageUnitsForSubscriptionOrUntaggedSince(
            @Param("customerId") UUID customerId,
            @Param("subscriptionId") UUID subscriptionId,
            @Param("featureId") UUID featureId,
            @Param("since") Instant since,
            @Param("until") Instant until);

    @Query("SELECT e FROM Event e WHERE e.customerId = :customerId " +
           "AND e.subscriptionId IS NULL " +
           "AND e.eventType IN :eventTypes " +
           "AND e.occurredAt >= :start AND e.occurredAt < :end")
    List<Event> findEventsForBillingUntagged(
            @Param("customerId") UUID customerId,
            @Param("eventTypes") Collection<EventType> eventTypes,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("""
        SELECT e.subscriptionId, e.featureId, COALESCE(SUM(e.usageUnits), 0)
        FROM Event e
        WHERE e.subscriptionId IN :subscriptionIds
        AND e.featureId IN :featureIds
        AND e.eventType IN :eventTypes
        AND e.occurredAt >= :start AND e.occurredAt < :end
        GROUP BY e.subscriptionId, e.featureId
    """)
    List<Object[]> sumUsageGroupedBySubscriptionAndFeature(
            @Param("subscriptionIds") Collection<UUID> subscriptionIds,
            @Param("featureIds") Collection<UUID> featureIds,
            @Param("eventTypes") Collection<EventType> eventTypes,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("""
        SELECT e.subscriptionId, e.featureId, COALESCE(SUM(e.usageUnits), 0)
        FROM Event e
        WHERE (e.subscriptionId IN :subscriptionIds OR e.subscriptionId IS NULL)
        AND e.featureId IN :featureIds
        AND e.eventType IN :eventTypes
        AND e.occurredAt >= :start AND e.occurredAt < :end
        AND e.customerId IN (
            SELECT s.customer.id FROM Subscription s WHERE s.id IN :subscriptionIds
        )
        GROUP BY e.subscriptionId, e.featureId
    """)
    List<Object[]> sumUsageGroupedBySubscriptionAndFeatureIncludingUntagged(
            @Param("subscriptionIds") Collection<UUID> subscriptionIds,
            @Param("featureIds") Collection<UUID> featureIds,
            @Param("eventTypes") Collection<EventType> eventTypes,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("SELECT e.customerId, COUNT(e) FROM Event e WHERE e.customerId IN :customerIds " +
           "AND e.eventType IN :eventTypes AND e.occurredAt >= :start AND e.occurredAt < :end " +
           "GROUP BY e.customerId")
    List<Object[]> countEventsGroupedByCustomerInRange(
            @Param("customerIds") Collection<UUID> customerIds,
            @Param("eventTypes") Collection<EventType> eventTypes,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("""
        SELECT DISTINCT e.customerId FROM Event e
        WHERE e.account.id = :accountId
        AND e.customerId IS NOT NULL
        AND e.customerId NOT IN :excludeCustomerIds
        AND e.eventType IN :eventTypes
        AND e.occurredAt >= :since
    """)
    List<UUID> findEventOnlyCustomerIds(
            @Param("accountId") UUID accountId,
            @Param("excludeCustomerIds") Collection<UUID> excludeCustomerIds,
            @Param("eventTypes") Collection<EventType> eventTypes,
            @Param("since") Instant since);

    @Query("""
        SELECT e.customerId,
               COALESCE(SUM(e.revenueAmount), 0),
               COALESCE(SUM(e.costAmount), 0)
        FROM Event e
        WHERE e.account.id = :accountId
        AND e.customerId IN :customerIds
        AND e.eventType IN :eventTypes
        AND e.occurredAt >= :start AND e.occurredAt < :end
        GROUP BY e.customerId
    """)
    List<Object[]> sumRevenueAndCostByCustomer(
            @Param("accountId") UUID accountId,
            @Param("customerIds") Collection<UUID> customerIds,
            @Param("eventTypes") Collection<EventType> eventTypes,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("""
        SELECT e.customerId, e.featureId,
               COALESCE(SUM(e.usageUnits), 0),
               COALESCE(SUM(e.costAmount), 0),
               COALESCE(SUM(e.revenueAmount), 0)
        FROM Event e
        WHERE e.account.id = :accountId
        AND e.customerId IN :customerIds
        AND e.eventType IN :eventTypes
        AND e.occurredAt >= :start AND e.occurredAt < :end
        AND e.featureId IS NOT NULL
        GROUP BY e.customerId, e.featureId
    """)
    List<Object[]> sumUsageRevenueAndCostByCustomerAndFeature(
            @Param("accountId") UUID accountId,
            @Param("customerIds") Collection<UUID> customerIds,
            @Param("eventTypes") Collection<EventType> eventTypes,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("""
        SELECT e.customerId, e.featureId, e.model, e.modelProvider,
               COALESCE(SUM(e.usageUnits), 0),
               COALESCE(SUM(e.costAmount), 0),
               COALESCE(SUM(e.revenueAmount), 0)
        FROM Event e
        WHERE e.account.id = :accountId
        AND e.customerId IN :customerIds
        AND e.eventType IN :eventTypes
        AND e.occurredAt >= :start AND e.occurredAt < :end
        AND e.featureId IS NOT NULL
        AND e.model IS NOT NULL
        GROUP BY e.customerId, e.featureId, e.model, e.modelProvider
    """)
    List<Object[]> sumUsageRevenueAndCostByCustomerFeatureAndModel(
            @Param("accountId") UUID accountId,
            @Param("customerIds") Collection<UUID> customerIds,
            @Param("eventTypes") Collection<EventType> eventTypes,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("""
        SELECT e.model, e.modelProvider,
               COUNT(e), COUNT(DISTINCT e.customerId), COUNT(DISTINCT e.featureId),
               COALESCE(SUM(e.costAmount), 0), COALESCE(SUM(e.revenueAmount), 0),
               COALESCE(SUM(e.usageUnits), 0), MAX(e.occurredAt)
        FROM Event e
        WHERE e.account.id = :accountId
        AND e.model IS NOT NULL
        AND e.eventType IN :eventTypes
        AND e.occurredAt >= :start AND e.occurredAt < :end
        GROUP BY e.model, e.modelProvider
        ORDER BY COALESCE(SUM(e.costAmount), 0) DESC
    """)
    List<Object[]> sumCostGroupedByModel(
            @Param("accountId") UUID accountId,
            @Param("eventTypes") Collection<EventType> eventTypes,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("""
        SELECT e.featureId, f.name, f.key,
               COUNT(e), COALESCE(SUM(e.costAmount), 0), COALESCE(SUM(e.revenueAmount), 0),
               COALESCE(SUM(e.usageUnits), 0), MAX(e.occurredAt)
        FROM Event e LEFT JOIN Feature f ON e.featureId = f.id
        WHERE e.account.id = :accountId
        AND e.featureId IS NOT NULL
        AND e.eventType IN :eventTypes
        AND e.occurredAt >= :start AND e.occurredAt < :end
        GROUP BY e.featureId, f.name, f.key
        ORDER BY COALESCE(SUM(e.costAmount), 0) DESC
    """)
    List<Object[]> aggregateByFeature(
            @Param("accountId") UUID accountId,
            @Param("eventTypes") Collection<EventType> eventTypes,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("""
        SELECT e.customerId, CONCAT(COALESCE(c.firstName, ''), ' ', COALESCE(c.lastName, '')), c.externalClientCustomerId,
               COUNT(e), COALESCE(SUM(e.costAmount), 0), COALESCE(SUM(e.revenueAmount), 0),
               COALESCE(SUM(e.usageUnits), 0), MAX(e.occurredAt)
        FROM Event e LEFT JOIN Customer c ON e.customerId = c.id
        WHERE e.account.id = :accountId
        AND e.customerId IS NOT NULL
        AND e.eventType IN :eventTypes
        AND e.occurredAt >= :start AND e.occurredAt < :end
        GROUP BY e.customerId, c.firstName, c.lastName, c.externalClientCustomerId
        ORDER BY COALESCE(SUM(e.costAmount), 0) DESC
    """)
    List<Object[]> aggregateByCustomer(
            @Param("accountId") UUID accountId,
            @Param("eventTypes") Collection<EventType> eventTypes,
            @Param("start") Instant start,
            @Param("end") Instant end);
}
