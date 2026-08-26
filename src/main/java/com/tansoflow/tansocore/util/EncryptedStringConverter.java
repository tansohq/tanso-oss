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
package com.tansoflow.tansocore.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * JPA converter for columns that hold a third-party credential.
 *
 * Writes always encrypt. Reads accept a legacy plaintext row (no enc:v1
 * prefix) and hand it back untouched, so an install upgrading from a version
 * that stored plaintext keeps working before {@link com.tansoflow.tansocore.config.SecretsUpgradeRunner}
 * has rewritten the rows.
 */
@Converter
@Component
public class EncryptedStringConverter implements AttributeConverter<String, String> {
    private final SecretCipher cipher;

    public EncryptedStringConverter(SecretCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty() || cipher.isEncrypted(attribute)) {
            // Empty means "no secret" (a disconnected connection); encrypting it would hide that.
            return attribute;
        }
        return cipher.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || !cipher.isEncrypted(dbData)) {
            return dbData;
        }
        return cipher.decrypt(dbData);
    }
}
