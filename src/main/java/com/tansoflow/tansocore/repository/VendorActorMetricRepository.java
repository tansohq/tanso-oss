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

import com.tansoflow.tansocore.entity.VendorActorMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface VendorActorMetricRepository extends JpaRepository<VendorActorMetric, UUID> {
    @Modifying
    @Query("DELETE FROM VendorActorMetric m WHERE m.connectionId = :connectionId AND m.day >= :from AND m.day < :to")
    int deleteWindow(UUID connectionId, LocalDate from, LocalDate to);

    @Modifying
    @Query("DELETE FROM VendorActorMetric m WHERE m.connectionId = :connectionId")
    int deleteByConnectionId(UUID connectionId);

    List<VendorActorMetric> findAllByAccountIdAndDayGreaterThanEqualAndDayLessThan(UUID accountId, LocalDate from, LocalDate to);
}
