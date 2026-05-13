package com.tansoflow.tansocore.repository;

import com.tansoflow.tansocore.entity.StripeImportJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StripeImportJobRepository extends JpaRepository<StripeImportJob, UUID> {
    List<StripeImportJob> findByAccount_IdOrderByCreatedAtDesc(UUID accountId);
}
