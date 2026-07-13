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

import com.tansoflow.tansocore.entity.AiCsvUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AiCsvUploadRepository extends JpaRepository<AiCsvUpload, UUID> {
    List<AiCsvUpload> findByAccount_IdOrderByCreatedAtDesc(UUID accountId);

    long countByAccount_Id(UUID accountId);

    @Query("SELECT u.id, u.fileName, u.rowCount, u.headers, u.createdAt FROM AiCsvUpload u WHERE u.account.id = :accountId ORDER BY u.createdAt DESC")
    List<Object[]> findMetadataByAccountId(UUID accountId);

    @Modifying
    @Transactional
    void deleteByAccount_Id(UUID accountId);
}
