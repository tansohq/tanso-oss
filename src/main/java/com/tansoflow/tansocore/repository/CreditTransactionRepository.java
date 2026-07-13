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
