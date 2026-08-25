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

import com.tansoflow.tansocore.entity.Outcome;
import com.tansoflow.tansocore.model.spend.type.OutcomeSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OutcomeRepository extends JpaRepository<Outcome, UUID> {
    Optional<Outcome> findByAccountIdAndSourceAndExternalId(UUID accountId, OutcomeSource source, String externalId);

    List<Outcome> findAllByAccountIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDesc(
            UUID accountId, Instant from, Instant to);

    List<Outcome> findTop200ByAccountIdOrderByOccurredAtDesc(UUID accountId);

    @Modifying
    @Query("DELETE FROM Outcome o WHERE o.sourceConnectionId = :sourceConnectionId")
    int deleteBySourceConnectionId(UUID sourceConnectionId);
}
