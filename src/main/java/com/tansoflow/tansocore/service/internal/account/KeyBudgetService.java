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
package com.tansoflow.tansocore.service.internal.account;

import com.tansoflow.tansocore.entity.AccountApiKey;
import com.tansoflow.tansocore.model.apikey.KeyBudgetDto;
import com.tansoflow.tansocore.model.apikey.request.UpdateKeyBudgetRequest;
import com.tansoflow.tansocore.model.apikey.type.SpendKind;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Bounds what one API key may spend. An account-wide cap is all-or-nothing —
 * this is the per-actor dial, so one runaway agent cannot drain the pool the
 * rest of the customer's agents draw from.
 */
public interface KeyBudgetService {

    /**
     * Throws {@link com.tansoflow.tansocore.model.exception.BudgetExceededException}
     * if this key spending {@code amount} would breach its budget on that axis.
     * No-op when the caller has no key id (JWT/UI traffic) or no budget is set.
     */
    void assertWithinBudget(UUID apiKeyId, SpendKind kind, BigDecimal amount);

    /** Records spend against a key. Silently ignored when apiKeyId is null. */
    void recordSpend(UUID accountId, UUID apiKeyId, SpendKind kind, BigDecimal amount,
                     String referenceId, String idempotencyKey);

    KeyBudgetDto describe(AccountApiKey key);

    KeyBudgetDto setBudget(String accountId, String customerReferenceId, String keyId,
                           UpdateKeyBudgetRequest request);

    KeyBudgetDto getBudget(String accountId, String customerReferenceId, String keyId);

    void clearBudget(String accountId, String customerReferenceId, String keyId);
}
