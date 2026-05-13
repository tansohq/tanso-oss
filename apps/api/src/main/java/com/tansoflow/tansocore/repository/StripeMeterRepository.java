package com.tansoflow.tansocore.repository;

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.Feature;
import com.tansoflow.tansocore.entity.StripeMeter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StripeMeterRepository extends JpaRepository<StripeMeter, UUID> {
    Optional<StripeMeter> findByFeatureAndAccount(Feature feature, Account account);
}
