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
package com.tansoflow.tansocore.repository;

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.AccountSetting;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class AccountSettingRepositoryTest {

    @Autowired
    private AccountSettingRepository accountSettingRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    private Account account;

    @BeforeEach
    void setUp() {
        transactionTemplate.executeWithoutResult(status -> {
            account = new Account();
            account.setName("Repo Test Account " + System.nanoTime());
            account = accountRepository.save(account);

            AccountSetting setting = new AccountSetting();
            setting.setAccounts(account);
            accountSettingRepository.save(setting);
        });
    }

    @AfterEach
    void tearDown() {
        accountSettingRepository.deleteById(account.getId());
        accountRepository.deleteById(account.getId());
    }

    // Regression: ISSUE-001 — GET /api/v1/tanso/account-settings 500'd with
    // LazyInitializationException because findAccountSettingById returned a
    // lazy Account proxy and toDto() read accounts.slug outside any session
    // (open-in-view is false). Found by /qa on 2026-08-07.
    @Test
    void findAccountSettingById_initializesAccount_forUseOutsideSession() {
        AccountSetting found = accountSettingRepository.findAccountSettingById(account.getId());

        assertNotNull(found);
        assertTrue(Hibernate.isInitialized(found.getAccounts()),
                "accounts association must be fetched eagerly by the query — "
                        + "toDto() reads it after the session is closed");
        assertDoesNotThrow(() -> found.getAccounts().getSlug());
    }
}
