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

import com.tansoflow.tansocore.entity.ApiKeySpendRecord;
import com.tansoflow.tansocore.model.apikey.type.SpendKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Repository
public interface ApiKeySpendRecordRepository extends JpaRepository<ApiKeySpendRecord, UUID> {

    @Query("""
            SELECT COALESCE(SUM(r.amount), 0)
            FROM ApiKeySpendRecord r
            WHERE r.apiKeyId = :apiKeyId
              AND r.kind = :kind
              AND r.occurredAt >= :windowStart
            """)
    BigDecimal sumSince(@Param("apiKeyId") UUID apiKeyId,
                        @Param("kind") SpendKind kind,
                        @Param("windowStart") Instant windowStart);

    boolean existsByAccountIdAndIdempotencyKey(UUID accountId, String idempotencyKey);
}
