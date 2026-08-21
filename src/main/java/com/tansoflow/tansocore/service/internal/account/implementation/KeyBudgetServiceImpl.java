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

import com.tansoflow.tansocore.entity.AccountApiKey;
import com.tansoflow.tansocore.entity.ApiKeySpendRecord;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.model.apikey.KeyBudgetDto;
import com.tansoflow.tansocore.model.apikey.request.UpdateKeyBudgetRequest;
import com.tansoflow.tansocore.model.apikey.type.BudgetPeriod;
import com.tansoflow.tansocore.model.apikey.type.SpendKind;
import com.tansoflow.tansocore.model.exception.BudgetExceededException;
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.repository.AccountApiKeyRepository;
import com.tansoflow.tansocore.repository.ApiKeySpendRecordRepository;
import com.tansoflow.tansocore.repository.CustomerRepository;
import com.tansoflow.tansocore.service.internal.account.KeyBudgetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeyBudgetServiceImpl implements KeyBudgetService {

    private final AccountApiKeyRepository accountApiKeyRepository;
    private final ApiKeySpendRecordRepository spendRecordRepository;
    private final CustomerRepository customerRepository;

    @Override
    @Transactional(readOnly = true)
    public void assertWithinBudget(UUID apiKeyId, SpendKind kind, BigDecimal amount) {
        if (apiKeyId == null || amount == null || amount.signum() <= 0) {
            return;
        }
        AccountApiKey key = accountApiKeyRepository.findById(apiKeyId).orElse(null);
        if (key == null) {
            return;
        }
        BigDecimal limit = limitFor(key, kind);
        if (limit == null) {
            return;
        }
        Instant windowStart = windowStart(key, Instant.now());
        BigDecimal spent = spendRecordRepository.sumSince(apiKeyId, kind, windowStart);
        if (spent.add(amount).compareTo(limit) > 0) {
            throw new BudgetExceededException(kind, limit, spent, amount, resetsAt(key, windowStart));
        }
    }

    @Override
    @Transactional
    public void recordSpend(UUID accountId, UUID apiKeyId, SpendKind kind, BigDecimal amount,
                            String referenceId, String idempotencyKey) {
        if (apiKeyId == null || amount == null || amount.signum() <= 0) {
            return;
        }
        if (idempotencyKey != null
                && spendRecordRepository.existsByAccountIdAndIdempotencyKey(accountId, idempotencyKey)) {
            log.debug("Skipping duplicate spend record for key {} (idempotencyKey={})", apiKeyId, idempotencyKey);
            return;
        }
        ApiKeySpendRecord record = new ApiKeySpendRecord();
        record.setAccountId(accountId);
        record.setApiKeyId(apiKeyId);
        record.setKind(kind);
        record.setAmount(amount);
        record.setOccurredAt(Instant.now());
        record.setReferenceId(referenceId);
        record.setIdempotencyKey(idempotencyKey);
        spendRecordRepository.save(record);
    }

    @Override
    @Transactional(readOnly = true)
    public KeyBudgetDto describe(AccountApiKey key) {
        Instant windowStart = windowStart(key, Instant.now());
        BigDecimal creditsSpent = spendRecordRepository.sumSince(key.getId(), SpendKind.CREDITS, windowStart);
        BigDecimal amountSpent = spendRecordRepository.sumSince(key.getId(), SpendKind.MONEY, windowStart);
        return KeyBudgetDto.builder()
                .keyId(key.getId())
                .period(key.getBudgetPeriod())
                .creditLimit(key.getBudgetCredits())
                .creditsSpent(creditsSpent)
                .creditsRemaining(remaining(key.getBudgetCredits(), creditsSpent))
                .amountLimit(key.getBudgetAmount())
                .amountSpent(amountSpent)
                .amountRemaining(remaining(key.getBudgetAmount(), amountSpent))
                .windowStart(windowStart)
                .resetsAt(resetsAt(key, windowStart))
                .build();
    }

    @Override
    @Transactional
    public KeyBudgetDto setBudget(String accountId, String customerReferenceId, String keyId,
                                  UpdateKeyBudgetRequest request) {
        if (request.getCreditLimit() != null && request.getCreditLimit().signum() < 0) {
            throw new IllegalArgumentException("creditLimit must not be negative");
        }
        if (request.getAmountLimit() != null && request.getAmountLimit().signum() < 0) {
            throw new IllegalArgumentException("amountLimit must not be negative");
        }
        AccountApiKey key = requireCustomerKey(accountId, customerReferenceId, keyId);

        // Changing the window restarts it — otherwise spend measured over the old
        // period would be re-read against a window it was never checked against.
        if (key.getBudgetStartedAt() == null || key.getBudgetPeriod() != request.getPeriod()) {
            key.setBudgetStartedAt(Instant.now());
        }
        key.setBudgetPeriod(request.getPeriod());
        key.setBudgetCredits(request.getCreditLimit());
        key.setBudgetAmount(request.getAmountLimit());
        accountApiKeyRepository.save(key);

        log.info("Budget set on key {}: period={}, credits={}, amount={}",
                key.getId(), request.getPeriod(), request.getCreditLimit(), request.getAmountLimit());
        return describe(key);
    }

    @Override
    @Transactional(readOnly = true)
    public KeyBudgetDto getBudget(String accountId, String customerReferenceId, String keyId) {
        return describe(requireCustomerKey(accountId, customerReferenceId, keyId));
    }

    @Override
    @Transactional
    public void clearBudget(String accountId, String customerReferenceId, String keyId) {
        AccountApiKey key = requireCustomerKey(accountId, customerReferenceId, keyId);
        key.setBudgetPeriod(null);
        key.setBudgetCredits(null);
        key.setBudgetAmount(null);
        key.setBudgetStartedAt(null);
        accountApiKeyRepository.save(key);
        log.info("Budget cleared on key {}", key.getId());
    }

    // ─── Window arithmetic ───

    private BigDecimal limitFor(AccountApiKey key, SpendKind kind) {
        return kind == SpendKind.CREDITS ? key.getBudgetCredits() : key.getBudgetAmount();
    }

    private BigDecimal remaining(BigDecimal limit, BigDecimal spent) {
        if (limit == null) {
            return null;
        }
        BigDecimal remaining = limit.subtract(spent);
        return remaining.signum() < 0 ? BigDecimal.ZERO : remaining;
    }

    /**
     * Start of the window {@code now} falls in. Windows tile forward from
     * budget_started_at, so a budget set mid-month resets on that day of the
     * month rather than on the 1st.
     */
    private Instant windowStart(AccountApiKey key, Instant now) {
        Instant anchor = key.getBudgetStartedAt();
        BudgetPeriod period = key.getBudgetPeriod();
        if (anchor == null || period == null || period == BudgetPeriod.TOTAL) {
            return anchor != null ? anchor : Instant.EPOCH;
        }
        Duration length = periodLength(period);
        long elapsed = Duration.between(anchor, now).toSeconds();
        if (elapsed < 0) {
            return anchor;
        }
        long windowsPassed = elapsed / length.toSeconds();
        return anchor.plusSeconds(windowsPassed * length.toSeconds());
    }

    private Instant resetsAt(AccountApiKey key, Instant windowStart) {
        BudgetPeriod period = key.getBudgetPeriod();
        if (period == null || period == BudgetPeriod.TOTAL) {
            return null;
        }
        return windowStart.plus(periodLength(period));
    }

    private Duration periodLength(BudgetPeriod period) {
        return switch (period) {
            case DAY -> Duration.ofDays(1);
            case WEEK -> Duration.ofDays(7);
            case MONTH -> Duration.ofDays(30);
            case TOTAL -> Duration.ZERO;
        };
    }

    private AccountApiKey requireCustomerKey(String accountId, String customerReferenceId, String keyId) {
        Customer customer = customerRepository
                .getCustomerByReferenceIdAndAccountId(customerReferenceId, UUID.fromString(accountId))
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + customerReferenceId));
        AccountApiKey key = accountApiKeyRepository
                .findByIdAndAccountId(UUID.fromString(keyId), UUID.fromString(accountId))
                .orElseThrow(() -> new ResourceNotFoundException("API key not found: " + keyId));
        if (key.getCustomer() == null || !key.getCustomer().getId().equals(customer.getId())) {
            throw new ResourceNotFoundException("API key not found: " + keyId);
        }
        return key;
    }
}
