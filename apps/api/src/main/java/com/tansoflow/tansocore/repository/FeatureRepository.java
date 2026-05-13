package com.tansoflow.tansocore.repository;

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.Feature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FeatureRepository extends JpaRepository<Feature, UUID> {
    Optional<Feature> findByIdAndAccount(UUID id, Account account);

    List<Feature> findAllByAccount(Account account);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(f) > 0 FROM Feature f WHERE f.key = :key AND f.account.id = :accountId")
    Boolean existsByKeyAndAccountId(String key, UUID accountId);

    Optional<Feature> findByKeyAndAccountId(String key, UUID accountId);

}
