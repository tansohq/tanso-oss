package com.tansoflow.tansocore.repository;

import com.tansoflow.tansocore.entity.AccountApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AccountApiKeyRepository extends JpaRepository<AccountApiKey, UUID> {
    AccountApiKey findAccountApiKeyByKeyValue(String apiKey);

    List<AccountApiKey> findByAccountId(UUID accountId);
}
