package com.tansoflow.tansocore.service.internal.account;

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.AccountApiKey;
import com.tansoflow.tansocore.entity.AccountSetting;

import java.util.UUID;

public interface AccountService {
    Account createAccount(String name);

    Account createAccount(String name, UUID accountId, boolean useNativeInsert);

    Account findByApiKey(String apiKey);

    AccountSetting retrieveAccountSettings(String accountId);

    Account retrieveAccount(String accountId);

    AccountApiKey retrieveFirstApiKey(String accountId);

    void createAccountSettings(Account account);

    AccountApiKey createApiKeyForAccount(Account account);

    AccountApiKey rotateApiKey(String accountId);

    void registerExternalApiKeyForAccount(String externalApiKey, String accountId, String externalEntityName, String type);

    void updateStripeCheckoutUrls(String accountId, String successUrl, String cancelUrl);
}
