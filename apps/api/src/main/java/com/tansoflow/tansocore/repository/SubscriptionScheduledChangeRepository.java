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
            "AND ssc.type = 'DOWNGRADE'")
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
