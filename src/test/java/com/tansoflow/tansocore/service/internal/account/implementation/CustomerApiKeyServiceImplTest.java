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

import com.tansoflow.tansocore.auth.ApiKeyHasher;
import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.AccountApiKey;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.model.apikey.CustomerApiKeyDto;
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.property.AppProperty;
import com.tansoflow.tansocore.repository.AccountApiKeyRepository;
import com.tansoflow.tansocore.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerApiKeyServiceImplTest {

    @Mock
    private AccountApiKeyRepository accountApiKeyRepository;
    @Mock
    private CustomerRepository customerRepository;

    private CustomerApiKeyServiceImpl service;

    private final UUID accountId = UUID.randomUUID();
    private Account account;
    private Customer customer;

    @BeforeEach
    void setUp() {
        AppProperty appProperty = new AppProperty();
        appProperty.setApiKeyPrefix("sk_test_");
        service = new CustomerApiKeyServiceImpl(accountApiKeyRepository, customerRepository, appProperty);

        account = new Account();
        account.setId(accountId);
        customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setAccount(account);
        customer.setExternalClientCustomerId("cust-ref-1");

        lenient().when(customerRepository.getCustomerByReferenceIdAndAccountId("cust-ref-1", accountId))
                .thenReturn(Optional.of(customer));
        lenient().when(accountApiKeyRepository.save(any(AccountApiKey.class)))
                .thenAnswer(inv -> {
                    AccountApiKey key = inv.getArgument(0);
                    if (key.getId() == null) key.setId(UUID.randomUUID());
                    return key;
                });
    }

    @Test
    void createKeyUsesCkPrefixAndStoresDigest() {
        CustomerApiKeyDto dto = service.createKey(accountId.toString(), "cust-ref-1", List.of("read", "purchase"));

        assertThat(dto.getApiKey()).startsWith("ck_test_");
        ArgumentCaptor<AccountApiKey> captor = ArgumentCaptor.forClass(AccountApiKey.class);
        org.mockito.Mockito.verify(accountApiKeyRepository).save(captor.capture());
        AccountApiKey saved = captor.getValue();
        assertThat(saved.getKeyValue()).isEqualTo(ApiKeyHasher.sha256Hex(dto.getApiKey()));
        assertThat(saved.getCustomer()).isEqualTo(customer);
        assertThat(saved.getScopes()).isEqualTo("read,purchase");
        assertThat(saved.getKeyType()).isEqualTo("customer");
    }

    @Test
    void createKeyDefaultsToReadScope() {
        CustomerApiKeyDto dto = service.createKey(accountId.toString(), "cust-ref-1", null);
        assertThat(dto.getScopes()).containsExactly("read");
    }

    @Test
    void createKeyRejectsUnknownScope() {
        assertThatThrownBy(() -> service.createKey(accountId.toString(), "cust-ref-1", List.of("admin")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown scope");
    }

    @Test
    void createKeyRequiresExistingCustomer() {
        assertThatThrownBy(() -> service.createKey(accountId.toString(), "missing", List.of("read")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findByKeyNeverMatchesTenantKeys() {
        String raw = "ck_test_abc";
        AccountApiKey tenantKey = new AccountApiKey();
        tenantKey.setAccount(account);
        tenantKey.setCustomer(null);
        tenantKey.setIsActive(true);
        tenantKey.setExpiresAt(Instant.now().plusSeconds(3600));
        when(accountApiKeyRepository.findAccountApiKeyByKeyValue(ApiKeyHasher.sha256Hex(raw)))
                .thenReturn(tenantKey);

        assertThat(service.findByKey(raw)).isNull();
    }

    @Test
    void findByKeyRejectsInactiveOrExpired() {
        String raw = "ck_test_abc";
        AccountApiKey key = new AccountApiKey();
        key.setAccount(account);
        key.setCustomer(customer);
        key.setIsActive(false);
        key.setExpiresAt(Instant.now().plusSeconds(3600));
        when(accountApiKeyRepository.findAccountApiKeyByKeyValue(ApiKeyHasher.sha256Hex(raw))).thenReturn(key);
        assertThat(service.findByKey(raw)).isNull();

        key.setIsActive(true);
        key.setExpiresAt(Instant.now().minusSeconds(1));
        assertThat(service.findByKey(raw)).isNull();
    }

    @Test
    void rotateDeactivatesOnlyThatKeyAndKeepsScopes() {
        AccountApiKey existing = new AccountApiKey();
        existing.setId(UUID.randomUUID());
        existing.setAccount(account);
        existing.setCustomer(customer);
        existing.setScopes("read,purchase");
        existing.setIsActive(true);
        existing.setExpiresAt(Instant.now().plusSeconds(3600));
        when(accountApiKeyRepository.findByIdAndAccountId(existing.getId(), accountId))
                .thenReturn(Optional.of(existing));

        CustomerApiKeyDto rotated = service.rotateKey(accountId.toString(), "cust-ref-1", existing.getId().toString());

        assertThat(existing.getIsActive()).isFalse();
        assertThat(existing.getDeletedAt()).isNotNull();
        assertThat(rotated.getApiKey()).startsWith("ck_test_");
        assertThat(rotated.getScopes()).containsExactlyInAnyOrder("read", "purchase");
    }

    @Test
    void rotateRejectsKeyBelongingToAnotherCustomer() {
        Customer other = new Customer();
        other.setId(UUID.randomUUID());
        other.setAccount(account);
        AccountApiKey existing = new AccountApiKey();
        existing.setId(UUID.randomUUID());
        existing.setAccount(account);
        existing.setCustomer(other);
        when(accountApiKeyRepository.findByIdAndAccountId(existing.getId(), accountId))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.rotateKey(accountId.toString(), "cust-ref-1", existing.getId().toString()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
