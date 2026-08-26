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
package com.tansoflow.tansocore.config;

import com.tansoflow.tansocore.util.SecretCipher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecretsUpgradeRunnerTest {
    @Mock private JdbcTemplate jdbcTemplate;

    @Test
    void aKeyThatDoesNotDecryptVendorCredentialsFailsBootEvenWithoutStripe() {
        SecretCipher wrongKey = new SecretCipher("a-different-key-than-the-one-that-encrypted-this-row-0000");
        String encryptedUnderOtherKey = new SecretCipher("the-original-key-material-0000000000000000000000").encrypt("sk-ant-admin01-x");
        when(jdbcTemplate.queryForList(contains("external_api_keys"), eq(String.class), anyString())).thenReturn(List.of());
        when(jdbcTemplate.queryForList(contains("vendor_connections"), eq(String.class), anyString())).thenReturn(List.of(encryptedUnderOtherKey));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> new SecretsUpgradeRunner(jdbcTemplate, wrongKey).run(null));
        assertTrue(e.getMessage().contains("vendor_connections.admin_key"), e.getMessage());
    }
}
