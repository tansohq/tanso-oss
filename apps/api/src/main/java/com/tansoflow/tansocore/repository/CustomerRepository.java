package com.tansoflow.tansocore.repository;

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> getCustomerById(UUID id);

    List<Customer> getCustomersByAccount(Account account);

    @Query("SELECT c FROM Customer c WHERE c.externalClientCustomerId = :referenceId AND c.account.id = :accountId")
    Optional<Customer> getCustomerByReferenceIdAndAccountId(String referenceId, UUID accountId);

    @Query("SELECT COUNT(c) > 0 FROM Customer c WHERE c.id = :id AND c.account.id = :accountId")
    boolean existsByIdAndAccountId(UUID id, UUID accountId);
}
