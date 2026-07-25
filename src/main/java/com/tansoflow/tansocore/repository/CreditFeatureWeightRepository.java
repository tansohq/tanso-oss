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

import com.tansoflow.tansocore.entity.CreditFeatureWeight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreditFeatureWeightRepository extends JpaRepository<CreditFeatureWeight, UUID> {

    Optional<CreditFeatureWeight> findTopByAccountIdAndFeatureIdAndModelAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            UUID accountId, UUID featureId, String model, Instant at);

    Optional<CreditFeatureWeight> findTopByAccountIdAndFeatureIdAndModelIsNullAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            UUID accountId, UUID featureId, Instant at);

    List<CreditFeatureWeight> findByAccountIdOrderByFeatureIdAscModelAscEffectiveFromDesc(UUID accountId);

    @Query("""
        SELECT w FROM CreditFeatureWeight w
        WHERE w.account.id = :accountId AND w.feature.id = :featureId
          AND ((:model IS NULL AND w.model IS NULL) OR w.model = :model)
        ORDER BY w.effectiveFrom DESC
        """)
    List<CreditFeatureWeight> findHistory(@Param("accountId") UUID accountId,
                                          @Param("featureId") UUID featureId,
                                          @Param("model") String model);

    boolean existsByAccountIdAndEffectiveFrom(UUID accountId, Instant effectiveFrom);

    Optional<CreditFeatureWeight> findByIdAndAccountId(UUID id, UUID accountId);
}
