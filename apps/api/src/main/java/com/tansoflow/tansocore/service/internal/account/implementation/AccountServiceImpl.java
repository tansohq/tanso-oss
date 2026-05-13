package com.tansoflow.tansocore.service.internal.account.implementation;

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.AccountApiKey;
import com.tansoflow.tansocore.entity.AccountSetting;
import com.tansoflow.tansocore.entity.ExternalApiKey;
import com.tansoflow.tansocore.model.api.external.ExternalApiKeyEntityName;
import com.tansoflow.tansocore.property.AppProperty;
import com.tansoflow.tansocore.repository.AccountApiKeyRepository;
import com.tansoflow.tansocore.repository.AccountRepository;
import com.tansoflow.tansocore.repository.AccountSettingRepository;
import com.tansoflow.tansocore.repository.ExternalApiKeyRepository;
import com.tansoflow.tansocore.service.internal.account.AccountService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {
    private final AccountApiKeyRepository accountApiKeyRepository;
    private final AccountRepository accountRepository;
    private final ExternalApiKeyRepository externalApiKeyRepository;
    private final AccountSettingRepository accountSettingRepository;
    private final AppProperty appProperty;
    private final EntityManager entityManager;

    @Override
    public Account createAccount(String name) {
        Account account = new Account();
        account.setName(name);
        log.info("Creating new account with name: {}", name);
        return accountRepository.save(account);
    }

    @Override
    public Account createAccount(String name, UUID accountId, boolean useNativeInsert) {
        log.info("Creating new account with pre-set ID {} and name: {}", accountId, name);
        if (useNativeInsert) {
            entityManager.createNativeQuery(
                    "INSERT INTO accounts (account_id, name, created_at, modified_at) VALUES (:id, :name, NOW(), NOW())")
                    .setParameter("id", accountId)
                    .setParameter("name", name)
                    .executeUpdate();
            return entityManager.find(Account.class, accountId);
        }
        Account account = new Account();
        account.setId(accountId);
        account.setName(name);
        entityManager.persist(account);
        return entityManager.find(Account.class, accountId);
    }

    @Override
    public void createAccountSettings(Account account) {
        AccountSetting setting = new AccountSetting();
        setting.setAccounts(account);
        // Defaults from entity field initializers: stripeMode=NONE, currency=USD
        accountSettingRepository.save(setting);
    }

    @Override
    public Account findByApiKey(String apiKey) {
        AccountApiKey accountApiKey = accountApiKeyRepository.findAccountApiKeyByKeyValue(apiKey);
        if (accountApiKey != null) {
            return accountApiKey.getAccount();
        }
        return null;
    }

    @Override
    public AccountSetting retrieveAccountSettings(String accountId) {
        AccountSetting setting = accountSettingRepository.findAccountSettingById(UUID.fromString(accountId));
        if (setting == null) {
            log.info("No account settings found for account {}, creating defaults", accountId);
            Account account = retrieveAccount(accountId);
            createAccountSettings(account);
            setting = accountSettingRepository.findAccountSettingById(UUID.fromString(accountId));
        }
        return setting;
    }

    @Override
    public Account retrieveAccount(String accountId) {
        return accountRepository.findById(UUID.fromString(accountId))
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
    }

    @Override
    public AccountApiKey retrieveFirstApiKey(String accountId) {
        List<AccountApiKey> keys = accountApiKeyRepository.findByAccountId(UUID.fromString(accountId));

        // Prefer keys with valid sk_ prefix (required by ApiKeyAuthFilter)
        return keys.stream()
                .filter(k -> k.getKeyValue().startsWith("sk_live_") || k.getKeyValue().startsWith("sk_test_"))
                .findFirst()
                .or(() -> keys.stream().findFirst())
                .orElseGet(() -> {
                    // Auto-generate a key for accounts created before key generation was added
                    log.info("No API key found for account {}, generating one", accountId);
                    Account account = retrieveAccount(accountId);
                    return createApiKeyForAccount(account);
                });
    }

    @Override
    public AccountApiKey createApiKeyForAccount(Account account) {
        String rawKey = generateRawApiKey();

        AccountApiKey apiKey = new AccountApiKey();
        apiKey.setAccount(account);
        apiKey.setKeyType("secret");
        apiKey.setKeyValue(rawKey);
        apiKey.setIsActive(true);
        apiKey.setExpiresAt(Instant.now().plus(1825, ChronoUnit.DAYS));

        AccountApiKey saved = accountApiKeyRepository.save(apiKey);
        log.info("Created API key for account {}", account.getId());
        return saved;
    }

    @Override
    public AccountApiKey rotateApiKey(String accountId) {
        Account account = retrieveAccount(accountId);

        // Soft-delete existing keys
        List<AccountApiKey> existing = accountApiKeyRepository.findByAccountId(UUID.fromString(accountId));
        // triggers @SQLDelete soft-delete
        accountApiKeyRepository.deleteAll(existing);

        return createApiKeyForAccount(account);
    }

    private String generateRawApiKey() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return appProperty.getApiKeyPrefix() + uuid;
    }

    @Override
    public void registerExternalApiKeyForAccount(String externalApiKey, String accountId, String externalEntityName, String type) {
        ExternalApiKeyEntityName externalApiKeyEntityName = ExternalApiKeyEntityName.valueOf(externalEntityName);

        ExternalApiKey externalApiKeyEntity = new ExternalApiKey();

        externalApiKeyEntity.setAccount(UUID.fromString(accountId));
        externalApiKeyEntity.setExternalApiEntityName(externalApiKeyEntityName.name());
        externalApiKeyEntity.setKeyType(type);
        externalApiKeyEntity.setKeyValue(externalApiKey);
        externalApiKeyEntity.setIsActive(true);

        externalApiKeyRepository.save(externalApiKeyEntity);
    }

    @Override
    public void updateStripeCheckoutUrls(String accountId, String successUrl, String cancelUrl) {
        if (successUrl == null && cancelUrl == null) {
            return;
        }
        AccountSetting setting = accountSettingRepository.findAccountSettingById(UUID.fromString(accountId));
        if (successUrl != null) {
            setting.setStripeCheckoutSuccessUrl(successUrl);
        }
        if (cancelUrl != null) {
            setting.setStripeCheckoutCancelUrl(cancelUrl);
        }
        accountSettingRepository.save(setting);
    }
}
