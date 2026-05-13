package com.tansoflow.tansocore.repository;

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanRepository extends JpaRepository<Plan, UUID> {
    List<Plan> findAllByAccount(Account account);

    List<Plan> findAllByAccountAndStatus(Account account, String status);

    Optional<Plan> findByIdAndAccount(UUID uuid, Account account);

    @Query("SELECT COUNT(plan) > 0 FROM Plan plan WHERE plan.key = :key AND plan.account.id = :accountId")
    Boolean existsByKeyAndAccountId(String key, UUID accountId);

    Optional<Plan> findByKeyAndAccountId(String key, UUID accountId);
}
