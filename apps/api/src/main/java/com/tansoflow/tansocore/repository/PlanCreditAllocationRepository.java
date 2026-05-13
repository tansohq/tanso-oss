package com.tansoflow.tansocore.repository;

import com.tansoflow.tansocore.entity.PlanCreditAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanCreditAllocationRepository extends JpaRepository<PlanCreditAllocation, UUID> {

    List<PlanCreditAllocation> findByPlanIdAndDeletedAtIsNull(UUID planId);

    Optional<PlanCreditAllocation> findByPlanIdAndCreditModelIdAndDeletedAtIsNull(UUID planId, UUID creditModelId);
}
