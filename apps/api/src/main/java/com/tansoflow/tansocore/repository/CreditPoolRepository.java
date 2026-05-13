package com.tansoflow.tansocore.repository;

import com.tansoflow.tansocore.entity.CreditPool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreditPoolRepository extends JpaRepository<CreditPool, UUID> {

    @Query("SELECT cp FROM CreditPool cp WHERE cp.account.id = :accountId AND cp.deletedAt IS NULL")
    List<CreditPool> findAllByAccountId(@Param("accountId") UUID accountId);

    @Query("SELECT cp FROM CreditPool cp WHERE cp.id = :poolId AND cp.account.id = :accountId AND cp.deletedAt IS NULL")
    Optional<CreditPool> findByIdAndAccountId(@Param("poolId") UUID poolId, @Param("accountId") UUID accountId);

    @Query("SELECT cp FROM CreditPool cp WHERE cp.customer.id = :customerId AND cp.account.id = :accountId AND cp.deletedAt IS NULL")
    List<CreditPool> findByCustomerIdAndAccountId(@Param("customerId") UUID customerId, @Param("accountId") UUID accountId);

    boolean existsByAccountIdAndNameAndDeletedAtIsNull(UUID accountId, String name);

    @Query("""
        SELECT cp FROM CreditPool cp
        WHERE cp.account.id = :accountId
          AND cp.hardLimit = true
          AND cp.status = 'ACTIVE'
          AND cp.deletedAt IS NULL
    """)
    List<CreditPool> findHardLimitPoolsByAccountId(@Param("accountId") UUID accountId);

    @Query("""
        SELECT cp FROM CreditPool cp
        WHERE cp.customer.id = :customerId
          AND cp.account.id = :accountId
          AND cp.denomination = :denomination
          AND cp.deletedAt IS NULL
    """)
    Optional<CreditPool> findByCustomerIdAndAccountIdAndDenomination(
            @Param("customerId") UUID customerId,
            @Param("accountId") UUID accountId,
            @Param("denomination") String denomination);

    @Modifying
    @Query(value = """
        UPDATE credit_pools
        SET balance = balance + :delta,
            total_granted = CASE WHEN :txType = 'GRANT' AND :delta > 0 THEN total_granted + :delta ELSE total_granted END,
            total_consumed = CASE WHEN :txType = 'DEDUCTION' THEN total_consumed + ABS(:delta) ELSE total_consumed END,
            total_expired = CASE WHEN :txType = 'EXPIRATION' THEN total_expired + ABS(:delta) ELSE total_expired END,
            total_reversed = CASE WHEN :txType = 'REVERSAL' AND :delta > 0 THEN total_reversed + :delta ELSE total_reversed END,
            status = CASE
                WHEN balance + :delta <= 0 AND hard_limit = true THEN 'DEPLETED'
                WHEN balance + :delta > 0 AND status = 'DEPLETED' THEN 'ACTIVE'
                ELSE status END,
            version = version + 1,
            modified_at = now()
        WHERE credit_pool_id = :poolId AND version = :expectedVersion
        """, nativeQuery = true)
    int updatePoolBalanceAtomically(@Param("poolId") UUID poolId, @Param("delta") BigDecimal delta,
            @Param("txType") String txType, @Param("expectedVersion") long expectedVersion);
}
