/*
 * Tanso Core - open-source B2B SaaS monetization engine
 * Copyright (C) 2026  Douglas Baek
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.tansoflow.tansocore.service.internal.account.implementation;

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.AccountApiKey;
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.property.AppProperty;
import com.tansoflow.tansocore.repository.AccountApiKeyRepository;
import com.tansoflow.tansocore.repository.AccountRepository;
import com.tansoflow.tansocore.repository.AccountSettingRepository;
import com.tansoflow.tansocore.repository.ExternalApiKeyRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    private static final String API_KEY = "sk_test_1234567890abcdef";

    @Mock
    private AccountApiKeyRepository accountApiKeyRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ExternalApiKeyRepository externalApiKeyRepository;

    @Mock
    private AccountSettingRepository accountSettingRepository;

    @Mock
    private AppProperty appProperty;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private AccountServiceImpl accountService;

    private Account account;

    @BeforeEach
    void setUp() {
        account = new Account();
        account.setId(UUID.randomUUID());
    }

    @Test
    void findByApiKey_ValidKey_ReturnsAccount() {
        AccountApiKey apiKey = apiKey(true, Instant.now().plusSeconds(60), null);
        when(accountApiKeyRepository.findAccountApiKeyByKeyValue(API_KEY)).thenReturn(apiKey);

        assertSame(account, accountService.findByApiKey(API_KEY));
    }

    @Test
    void findByApiKey_InactiveKey_IsRejected() {
        when(accountApiKeyRepository.findAccountApiKeyByKeyValue(API_KEY))
                .thenReturn(apiKey(false, Instant.now().plusSeconds(60), null));

        assertNull(accountService.findByApiKey(API_KEY));
    }

    @Test
    void findByApiKey_ExpiredKey_IsRejected() {
        when(accountApiKeyRepository.findAccountApiKeyByKeyValue(API_KEY))
                .thenReturn(apiKey(true, Instant.now().minusSeconds(1), null));

        assertNull(accountService.findByApiKey(API_KEY));
    }

    @Test
    void findByApiKey_ArchivedKey_IsRejected() {
        when(accountApiKeyRepository.findAccountApiKeyByKeyValue(API_KEY))
                .thenReturn(apiKey(true, Instant.now().plusSeconds(60), Instant.now()));

        assertNull(accountService.findByApiKey(API_KEY));
    }

    @Test
    void findByApiKey_MissingExpiry_IsRejected() {
        when(accountApiKeyRepository.findAccountApiKeyByKeyValue(API_KEY))
                .thenReturn(apiKey(true, null, null));

        assertNull(accountService.findByApiKey(API_KEY));
    }

    @Test
    void findByApiKey_UnknownKey_IsRejected() {
        when(accountApiKeyRepository.findAccountApiKeyByKeyValue(API_KEY)).thenReturn(null);

        assertNull(accountService.findByApiKey(API_KEY));
    }

    @Test
    void retrieveFirstApiKey_NoUsableKey_RequiresRotation() {
        AccountApiKey expiredKey = apiKey(true, Instant.now().minusSeconds(1), null);
        when(accountApiKeyRepository.findByAccountId(account.getId())).thenReturn(List.of(expiredKey));

        assertThrows(ResourceNotFoundException.class,
                () -> accountService.retrieveFirstApiKey(account.getId().toString()));
    }

    @Test
    void rotateApiKey_InvalidatesExistingKeysAndRetainsAuditRecord() {
        AccountApiKey existingKey = apiKey(true, Instant.now().plusSeconds(60), null);
        when(accountRepository.findById(account.getId())).thenReturn(java.util.Optional.of(account));
        when(accountApiKeyRepository.findByAccountId(account.getId())).thenReturn(List.of(existingKey));
        when(appProperty.getApiKeyPrefix()).thenReturn("sk_test_");
        when(accountApiKeyRepository.save(any(AccountApiKey.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountApiKey rotatedKey = accountService.rotateApiKey(account.getId().toString());

        assertFalse(existingKey.getIsActive());
        assertTrue(existingKey.getDeletedAt() != null);
        assertNotSame(existingKey, rotatedKey);
        assertTrue(rotatedKey.getKeyValue().startsWith("sk_test_"));
        verify(accountApiKeyRepository).saveAll(List.of(existingKey));
    }

    private AccountApiKey apiKey(boolean active, Instant expiresAt, Instant archivedAt) {
        AccountApiKey apiKey = new AccountApiKey();
        apiKey.setAccount(account);
        apiKey.setKeyValue(API_KEY);
        apiKey.setIsActive(active);
        apiKey.setExpiresAt(expiresAt);
        apiKey.setArchivedAt(archivedAt);
        return apiKey;
    }
}
