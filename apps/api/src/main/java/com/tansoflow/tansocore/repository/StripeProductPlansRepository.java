package com.tansoflow.tansocore.repository;

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.entity.StripeProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StripeProductPlansRepository extends JpaRepository<StripeProduct, String> {
    StripeProduct findStripeProductByPlan(Plan plan);

    StripeProduct findByStripeProductExternalIdAndAccount(String stripeProductExternalId, Account account);

    boolean existsByStripeProductExternalIdAndAccount(String stripeProductExternalId, Account account);
}
