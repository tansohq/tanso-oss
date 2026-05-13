package com.tansoflow.tansocore.repository;

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.StripeCustomer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StripeCustomerRepository extends JpaRepository<StripeCustomer, String> {
    StripeCustomer findByCustomer(Customer customer);

    StripeCustomer findStripeCustomerById(UUID id);

    boolean existsStripeCustomerByCustomerAndAccount(Customer customer, Account account);

    StripeCustomer findByStripeCustomerExternalIdAndAccount(String stripeCustomerExternalId, Account account);

    boolean existsByStripeCustomerExternalIdAndAccount(String stripeCustomerExternalId, Account account);
}
