package com.tansoflow.tansocore.repository;

import com.tansoflow.tansocore.entity.CreditTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, UUID> {

    @Query("SELECT ct FROM CreditTransaction ct WHERE ct.creditPool.id = :poolId ORDER BY ct.createdAt DESC")
    List<CreditTransaction> findByCreditPoolId(@Param("poolId") UUID poolId);

    @Query("SELECT ct FROM CreditTransaction ct WHERE ct.creditPool.id = :poolId ORDER BY ct.createdAt DESC")
    Page<CreditTransaction> findByCreditPoolIdPaged(@Param("poolId") UUID poolId, Pageable pageable);

    @Query("SELECT ct FROM CreditTransaction ct WHERE ct.account.id = :accountId ORDER BY ct.createdAt DESC")
    Page<CreditTransaction> findByAccountId(@Param("accountId") UUID accountId, Pageable pageable);

    boolean existsByAccountIdAndIdempotencyKey(UUID accountId, String idempotencyKey);
}
