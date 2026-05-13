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
