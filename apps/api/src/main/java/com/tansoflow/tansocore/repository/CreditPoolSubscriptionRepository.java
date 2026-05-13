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
          AND cps.creditPool.denomination = :denomination
          AND cps.deletedAt IS NULL
        ORDER BY cps.drawPriority ASC
    """)
    List<CreditPoolSubscription> findBySubscriptionIdAndCreditPool_DenominationOrderByDrawPriority(
            @Param("subscriptionId") UUID subscriptionId, @Param("denomination") String denomination);

    Optional<CreditPoolSubscription> findByCreditPoolIdAndSubscriptionIdAndDeletedAtIsNull(UUID creditPoolId, UUID subscriptionId);

    boolean existsByCreditPoolIdAndSubscriptionIdAndDeletedAtIsNull(UUID creditPoolId, UUID subscriptionId);
}
