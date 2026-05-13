package com.tansoflow.tansocore.repository;

import com.tansoflow.tansocore.entity.CreditModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreditModelRepository extends JpaRepository<CreditModel, UUID> {

    List<CreditModel> findByAccountId(UUID accountId);

    Optional<CreditModel> findByAccountIdAndDenomination(UUID accountId, String denomination);

    Optional<CreditModel> findByIdAndAccountId(UUID id, UUID accountId);
}
