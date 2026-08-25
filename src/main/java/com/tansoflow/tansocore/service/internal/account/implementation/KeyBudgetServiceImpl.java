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
import com.tansoflow.tansocore.util.BudgetWindow;
import com.tansoflow.tansocore.service.internal.account.KeyBudgetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

        stampAlertIfCrossed(apiKeyId);
    }

    @Override
    @Transactional(readOnly = true)
    public KeyBudgetDto describe(AccountApiKey key) {
        Instant windowStart = windowStart(key, Instant.now());
        BigDecimal creditsSpent = spendRecordRepository.sumSince(key.getId(), SpendKind.CREDITS, windowStart);
        BigDecimal amountSpent = spendRecordRepository.sumSince(key.getId(), SpendKind.MONEY, windowStart);
        Integer percentUsed = percentOfTightestLimit(key, creditsSpent, amountSpent);
        boolean alerting = key.getBudgetAlertAt() != null
                && !key.getBudgetAlertAt().isBefore(windowStart);

        return KeyBudgetDto.builder()
                .keyId(key.getId())
                .percentUsed(percentUsed)
                .alertThreshold(key.getBudgetAlertThreshold())
                .alerting(alerting)
                // A stamp from a previous window is stale, not current news.
                .alertingSince(alerting ? key.getBudgetAlertAt() : null)
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
        if (request.getAlertThreshold() != null
                && (request.getAlertThreshold() < 0 || request.getAlertThreshold() > 99)) {
            throw new IllegalArgumentException("alertThreshold must be between 1 and 99, or 0 to never alert");
        }
        AccountApiKey key = requireCustomerKey(accountId, customerReferenceId, keyId);

        boolean alreadyBudgeted = key.getBudgetPeriod() != null;

        // Changing the window restarts it — otherwise spend measured over the old
        // period would be re-read against a window it was never checked against.
        if (key.getBudgetStartedAt() == null || key.getBudgetPeriod() != request.getPeriod()) {
            key.setBudgetStartedAt(Instant.now());
        }
        Integer threshold = request.getAlertThreshold();
        key.setBudgetPeriod(request.getPeriod());
        key.setBudgetCredits(request.getCreditLimit());
        key.setBudgetAmount(request.getAmountLimit());
        // Omitting the threshold means "leave it as it is", not "turn it off" —
        // otherwise changing a limit would silently stop the warnings. A budget
        // that has never had one starts at 80, since a budget nobody is warned
        // about is the problem this solves. Turning alerting off is an explicit 0.
        if (threshold != null) {
            key.setBudgetAlertThreshold(threshold == 0 ? null : threshold);
        } else if (key.getBudgetAlertThreshold() == null && !alreadyBudgeted) {
            key.setBudgetAlertThreshold(80);
        }
        key.setBudgetAlertAt(null);
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
        key.setBudgetAlertThreshold(null);
        key.setBudgetAlertAt(null);
        accountApiKeyRepository.save(key);
        log.info("Budget cleared on key {}", key.getId());
    }

    /**
     * Stamps the first crossing of the threshold in this window. Stamped once:
     * the point is "when did this key get close", not "it is still close",
     * which the caller can see from percentUsed.
     */
    private void stampAlertIfCrossed(UUID apiKeyId) {
        AccountApiKey key = accountApiKeyRepository.findById(apiKeyId).orElse(null);
        if (key == null || key.getBudgetAlertThreshold() == null || key.getBudgetPeriod() == null) {
            return;
        }
        Instant windowStart = windowStart(key, Instant.now());
        if (key.getBudgetAlertAt() != null && !key.getBudgetAlertAt().isBefore(windowStart)) {
            return; // already stamped for this window
        }
        BigDecimal creditsSpent = spendRecordRepository.sumSince(apiKeyId, SpendKind.CREDITS, windowStart);
        BigDecimal amountSpent = spendRecordRepository.sumSince(apiKeyId, SpendKind.MONEY, windowStart);
        Integer percent = percentOfTightestLimit(key, creditsSpent, amountSpent);
        if (percent != null && percent >= key.getBudgetAlertThreshold()) {
            key.setBudgetAlertAt(Instant.now());
            accountApiKeyRepository.save(key);
            log.info("Key {} crossed {}% of its budget", apiKeyId, key.getBudgetAlertThreshold());
        }
    }

    /**
     * How far through the closest limit this key is. Whichever axis is nearest
     * its ceiling is the one that will refuse the next call, so that is the
     * number worth reporting. Null when neither axis is capped.
     */
    private Integer percentOfTightestLimit(AccountApiKey key, BigDecimal creditsSpent, BigDecimal amountSpent) {
        Integer worst = null;
        for (BigDecimal[] axis : new BigDecimal[][]{
                {key.getBudgetCredits(), creditsSpent},
                {key.getBudgetAmount(), amountSpent}}) {
            BigDecimal limit = axis[0];
            if (limit == null || limit.signum() <= 0) {
                continue;
            }
            int percent = axis[1].multiply(BigDecimal.valueOf(100))
                    .divide(limit, 0, RoundingMode.FLOOR).intValue();
            worst = worst == null ? percent : Math.max(worst, percent);
        }
        return worst;
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
     * Per-key budgets tile forward from budget_started_at (see
     * {@link BudgetWindow#tiling}), so a budget set mid-month resets on that
     * day of the month rather than on the 1st.
     */
    private Instant windowStart(AccountApiKey key, Instant now) {
        return BudgetWindow.tiling(key.getBudgetStartedAt(), key.getBudgetPeriod(), now).start();
    }

    private Instant resetsAt(AccountApiKey key, Instant windowStart) {
        return BudgetWindow.tiling(key.getBudgetStartedAt(), key.getBudgetPeriod(), windowStart).resetsAt();
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
