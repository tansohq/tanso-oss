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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rewrites any plaintext credential rows left over from before secrets were
 * encrypted at rest. Runs once per boot; a fully upgraded database makes it a
 * single SELECT that returns nothing.
 *
 * Liquibase can't do this because the migration would need APP_SECRETS_KEY.
 * It goes through JDBC rather than JPA because re-saving an entity whose
 * decrypted value is unchanged is not dirty, so Hibernate would skip the write.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecretsUpgradeRunner implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;
    private final SecretCipher cipher;

    @Override
    public void run(ApplicationArguments args) {
        // Fail fast if the key no longer matches what is stored: every Stripe and
        // vendor path would otherwise 500 on first touch with no way to recover
        // from the console.
        // Every encrypted column, not just Stripe's: an install that never connected Stripe but
        // has vendor or outcome credentials would otherwise boot clean and fail on the first sync.
        String[][] encrypted = {
                {"external_api_keys", "key_value"},
                {"vendor_connections", "admin_key"},
                {"outcome_sources", "token"},
        };
        for (String[] col : encrypted) {
            List<String> sample = jdbcTemplate.queryForList(
                    "SELECT " + col[1] + " FROM " + col[0] + " WHERE " + col[1] + " LIKE ? LIMIT 1", String.class,
                    SecretCipher.PREFIX + "%");
            if (sample.isEmpty()) {
                continue;
            }
            try {
                cipher.decrypt(sample.get(0));
            } catch (IllegalStateException e) {
                throw new IllegalStateException(
                        "APP_SECRETS_KEY does not decrypt the secrets already stored in " + col[0] + "." + col[1] + ". "
                                + "Restore the previous key, or clear the stored credentials and reconnect them.", e);
            }
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT external_api_key_id, key_value FROM external_api_keys WHERE key_value NOT LIKE ?",
                SecretCipher.PREFIX + "%");
        for (Map<String, Object> row : rows) {
            UUID id = (UUID) row.get("external_api_key_id");
            String plaintext = (String) row.get("key_value");
            jdbcTemplate.update("UPDATE external_api_keys SET key_value = ? WHERE external_api_key_id = ?",
                    cipher.encrypt(plaintext), id);
        }
        if (!rows.isEmpty()) {
            log.info("Encrypted {} legacy plaintext external_api_keys row(s)", rows.size());
        }
    }
}
