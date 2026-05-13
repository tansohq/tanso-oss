package com.tansoflow.tansocore.repository;

import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.Subscription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    @Query("SELECT subscription FROM Subscription subscription WHERE subscription.id = :uuid AND subscription.account.id = :accountId")
    Subscription findSubscriptionByUuidAndAccountId(UUID uuid, UUID accountId);

    List<Subscription> findSubscriptionsByCustomer(Customer customer);

    List<Subscription> findSubscriptionsByCustomer_Id(UUID customerId);

    @EntityGraph(attributePaths = {"customer", "plan"})
    List<Subscription> findSubscriptionsByCustomer_IdInAndAccount_Id(List<UUID> customerIds, UUID accountId);

    @EntityGraph(attributePaths = {"customer", "plan"})
    List<Subscription> findSubscriptionsByCustomerIn(List<Customer> customers);

    Subscription findSubscriptionsById(UUID subscriptionUuid);

    @Modifying
    @Transactional
    @Query("""
        UPDATE Subscription s
        SET s.isActive = FALSE,
            s.modifiedAt = CURRENT_TIMESTAMP,
            s.cancelledAt = COALESCE(s.cancelledAt, CURRENT_TIMESTAMP)
        WHERE s.isActive = TRUE
          AND s.cancelEffectiveAt IS NOT NULL
          AND s.cancelEffectiveAt <= CURRENT_TIMESTAMP
          AND s.deletedAt IS NULL
          AND s.archivedAt IS NULL
    """)
    int deactivateExpiredSubscriptions();

    @Query("""
        SELECT s FROM Subscription s
        WHERE s.isActive = TRUE
          AND s.cancelEffectiveAt IS NOT NULL
          AND s.cancelEffectiveAt <= CURRENT_TIMESTAMP
          AND s.deletedAt IS NULL
          AND s.archivedAt IS NULL
    """)
    List<Subscription> findExpiredSubscriptionsForCancellation();

    @Query("""
   SELECT s FROM Subscription s
   WHERE s.isActive = true
     AND s.deletedAt IS NULL
     AND s.cancelledAt IS NULL
     AND s.currentPeriodEnd <= :now
     AND NOT EXISTS (
       SELECT 1 FROM AccountSetting acs
       WHERE acs.accounts.id = s.account.id
         AND acs.stripeMode IN ('FULL_SYNC', 'STRIPE_INTEGRATION', 'STRIPE_DRIVEN')
     )
   """)
    Page<Subscription> findActiveNeedingRollover(Instant now, Pageable pageable);

    @Query("SELECT s FROM Subscription s WHERE s.account.id = :accountId AND s.cancelEffectiveAt IS NOT NULL AND s.isActive = TRUE")
    List<Subscription> findAllScheduledCancellationsByAccountId(UUID accountId);

    @EntityGraph(attributePaths = {"customer", "plan"})
    @Query("SELECT s FROM Subscription s WHERE s.account.id = :accountId AND s.isActive = TRUE AND s.deletedAt IS NULL")
    List<Subscription> findActiveSubscriptionsByAccountId(UUID accountId);

    @EntityGraph(attributePaths = {"customer", "plan"})
    @Query("""
        SELECT s FROM Subscription s
        WHERE s.account.id = :accountId
        AND s.createdAt <= :asOfDate
        AND (s.cancelledAt IS NULL OR s.cancelledAt > :asOfDate)
        AND s.deletedAt IS NULL
    """)
    List<Subscription> findSubscriptionsActiveAsOf(UUID accountId, Instant asOfDate);

    @Query("""
        SELECT COUNT(s) FROM Subscription s
        WHERE s.account.id = :accountId
        AND s.cancelledAt IS NOT NULL
        AND s.cancelledAt >= :periodStart
        AND s.cancelledAt <= :periodEnd
        AND s.deletedAt IS NULL
    """)
    long countChurnedSubscriptions(UUID accountId, Instant periodStart, Instant periodEnd);

    @EntityGraph(attributePaths = {"customer", "plan"})
    @Query("""
        SELECT s FROM Subscription s
        WHERE s.plan.id = :planId
        AND s.account.id = :accountId
        AND s.createdAt <= :periodEnd
        AND (s.cancelledAt IS NULL OR s.cancelledAt >= :periodStart)
        AND s.deletedAt IS NULL
    """)
    Page<Subscription> findSubscriptionsActiveDuringPeriodByPlan(
            UUID accountId, UUID planId, Instant periodStart, Instant periodEnd, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "plan"})
    @Query("""
        SELECT s FROM Subscription s
        WHERE s.plan.id = :planId
        AND s.account.id = :accountId
        AND s.id = :subscriptionId
        AND s.createdAt <= :periodEnd
        AND (s.cancelledAt IS NULL OR s.cancelledAt >= :periodStart)
        AND s.deletedAt IS NULL
    """)
    Page<Subscription> findSubscriptionActiveDuringPeriodByPlanAndId(
            UUID accountId, UUID planId, UUID subscriptionId, Instant periodStart, Instant periodEnd, Pageable pageable);
}
