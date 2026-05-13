package com.tansoflow.tansocore.repository;

import com.tansoflow.tansocore.entity.ExternalApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ExternalApiKeyRepository extends JpaRepository<ExternalApiKey, UUID>  {
    @Query(value = "SELECT eak FROM ExternalApiKey eak WHERE eak.keyType = :keyType AND eak.account = :account")
    ExternalApiKey findExternalApiKeyByKeyTypeAndAccount(String keyType, UUID account);
}
