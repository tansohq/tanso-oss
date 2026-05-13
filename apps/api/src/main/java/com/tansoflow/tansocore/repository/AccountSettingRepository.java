package com.tansoflow.tansocore.repository;

import com.tansoflow.tansocore.entity.AccountSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface AccountSettingRepository extends JpaRepository<AccountSetting, UUID> {
    @Query(value = "SELECT accountSetting FROM AccountSetting accountSetting WHERE accountSetting.accounts.id = :accountId")
    AccountSetting findAccountSettingById(UUID accountId);
}
