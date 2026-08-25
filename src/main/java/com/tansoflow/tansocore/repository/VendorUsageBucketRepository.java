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

import com.tansoflow.tansocore.entity.VendorUsageBucket;
import com.tansoflow.tansocore.model.spend.type.VendorUsageSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface VendorUsageBucketRepository extends JpaRepository<VendorUsageBucket, UUID> {
    @Modifying
    @Query("DELETE FROM VendorUsageBucket b WHERE b.connectionId = :connectionId AND b.source = :source "
            + "AND b.bucketStart >= :from AND b.bucketStart < :to")
    int deleteWindow(UUID connectionId, VendorUsageSource source, Instant from, Instant to);

    List<VendorUsageBucket> findAllByAccountIdAndBucketStartGreaterThanEqualAndBucketStartLessThan(
            UUID accountId, Instant from, Instant to);
}
