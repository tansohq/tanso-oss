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
import com.tansoflow.tansocore.entity.AccountApiKey;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.model.apikey.CustomerApiKeyDto;
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.property.AppProperty;
import com.tansoflow.tansocore.repository.AccountApiKeyRepository;
import com.tansoflow.tansocore.repository.CustomerRepository;
import com.tansoflow.tansocore.service.internal.account.CustomerApiKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomerApiKeyServiceImpl implements CustomerApiKeyService {

    private static final Set<String> ALLOWED_SCOPES = Set.of("read", "purchase");
    private static final long KEY_TTL_DAYS = 1825;

    private final AccountApiKeyRepository accountApiKeyRepository;
    private final CustomerRepository customerRepository;
    private final AppProperty appProperty;

    @Override
    @Transactional
    public AccountApiKey findByKey(String rawKey) {
        AccountApiKey key = accountApiKeyRepository.findAccountApiKeyByKeyValue(ApiKeyHasher.sha256Hex(rawKey));
        if (key == null || key.getCustomer() == null) {
            return null;
        }
        boolean usable = Boolean.TRUE.equals(key.getIsActive())
                && key.getExpiresAt() != null && key.getExpiresAt().isAfter(Instant.now())
                && key.getDeletedAt() == null && key.getArchivedAt() == null;
        return usable ? key : null;
    }

    @Override
    @Transactional
    public CustomerApiKeyDto createKey(String accountId, String customerReferenceId, List<String> scopes) {
        Customer customer = requireCustomer(accountId, customerReferenceId);
        List<String> effectiveScopes = validateScopes(scopes);

        String rawKey = customerKeyPrefix() + UUID.randomUUID().toString().replace("-", "");
        AccountApiKey key = new AccountApiKey();
        key.setAccount(customer.getAccount());
        key.setCustomer(customer);
        key.setKeyType("customer");
        key.setKeyValue(ApiKeyHasher.sha256Hex(rawKey));
        key.setKeyHint(ApiKeyHasher.hint(rawKey));
        key.setScopes(String.join(",", effectiveScopes));
        key.setIsActive(true);
        key.setExpiresAt(Instant.now().plus(KEY_TTL_DAYS, ChronoUnit.DAYS));

        AccountApiKey saved = accountApiKeyRepository.save(key);
        log.info("Created customer API key {} for customer {} on account {}", saved.getId(), customer.getId(), accountId);
        return toDto(saved, rawKey);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerApiKeyDto> listKeys(String accountId, String customerReferenceId) {
        Customer customer = requireCustomer(accountId, customerReferenceId);
        return accountApiKeyRepository
                .findByAccountIdAndCustomerId(UUID.fromString(accountId), customer.getId())
                .stream()
                .map(key -> toDto(key, null))
                .toList();
    }

    @Override
    @Transactional
    public CustomerApiKeyDto rotateKey(String accountId, String customerReferenceId, String keyId) {
        Customer customer = requireCustomer(accountId, customerReferenceId);
        AccountApiKey existing = requireCustomerKey(accountId, customer, keyId);

        existing.setIsActive(false);
        existing.setDeletedAt(Instant.now());
        accountApiKeyRepository.save(existing);

        List<String> scopes = existing.getScopes() != null
                ? Arrays.asList(existing.getScopes().split(","))
                : List.of("read");
        return createKey(accountId, customerReferenceId, scopes);
    }

    @Override
    @Transactional
    public void revokeKey(String accountId, String customerReferenceId, String keyId) {
        Customer customer = requireCustomer(accountId, customerReferenceId);
        AccountApiKey existing = requireCustomerKey(accountId, customer, keyId);
        existing.setIsActive(false);
        existing.setDeletedAt(Instant.now());
        accountApiKeyRepository.save(existing);
        log.info("Revoked customer API key {} for customer {}", keyId, customer.getId());
    }

    private Customer requireCustomer(String accountId, String customerReferenceId) {
        return customerRepository
                .getCustomerByReferenceIdAndAccountId(customerReferenceId, UUID.fromString(accountId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Customer not found: " + customerReferenceId));
    }

    private AccountApiKey requireCustomerKey(String accountId, Customer customer, String keyId) {
        AccountApiKey key = accountApiKeyRepository
                .findByIdAndAccountId(UUID.fromString(keyId), UUID.fromString(accountId))
                .orElseThrow(() -> new ResourceNotFoundException("API key not found: " + keyId));
        if (key.getCustomer() == null || !key.getCustomer().getId().equals(customer.getId())) {
            throw new ResourceNotFoundException("API key not found: " + keyId);
        }
        return key;
    }

    private List<String> validateScopes(List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return List.of("read");
        }
        for (String scope : scopes) {
            if (!ALLOWED_SCOPES.contains(scope)) {
                throw new IllegalArgumentException("Unknown scope '" + scope + "' — allowed: " + ALLOWED_SCOPES);
            }
        }
        return scopes.stream().distinct().toList();
    }

    private String customerKeyPrefix() {
        return appProperty.getApiKeyPrefix().replaceFirst("^sk_", "ck_");
    }

    private CustomerApiKeyDto toDto(AccountApiKey key, String rawKey) {
        return CustomerApiKeyDto.builder()
                .id(key.getId().toString())
                .customerReferenceId(key.getCustomer().getExternalClientCustomerId())
                .apiKey(rawKey)
                .keyHint(key.getKeyHint())
                .scopes(key.getScopes() != null ? Arrays.asList(key.getScopes().split(",")) : List.of())
                .active(key.getIsActive())
                .expiresAt(key.getExpiresAt())
                .createdAt(key.getCreatedAt())
                .budgetPeriod(key.getBudgetPeriod())
                .budgetCredits(key.getBudgetCredits())
                .budgetAmount(key.getBudgetAmount())
                .build();
    }
}
