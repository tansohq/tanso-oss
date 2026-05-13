package com.tansoflow.tansocore.repository;

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.entity.StripePrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StripePriceRepository extends JpaRepository<StripePrice, UUID> {
    Optional<StripePrice> findByPlanAndAccount(Plan plan, Account account);

    Optional<StripePrice> findFirstByPlanAndAccountOrderByCreatedAtDesc(Plan plan, Account account);
}
