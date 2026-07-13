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
