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

import com.tansoflow.tansocore.entity.CreditPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreditPriceRepository extends JpaRepository<CreditPrice, UUID> {

    Optional<CreditPrice> findTopByAccountIdAndDenominationAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            UUID accountId, String denomination, Instant at);

    List<CreditPrice> findByAccountIdOrderByDenominationAscEffectiveFromDesc(UUID accountId);

    List<CreditPrice> findByAccountIdAndDenominationOrderByEffectiveFromDesc(UUID accountId, String denomination);

    boolean existsByAccountIdAndEffectiveFrom(UUID accountId, Instant effectiveFrom);

    Optional<CreditPrice> findByIdAndAccountId(UUID id, UUID accountId);
}
