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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncryptedStringConverterTest {

    private final EncryptedStringConverter converter;

    EncryptedStringConverterTest() {
        converter = new EncryptedStringConverter(new SecretCipher("unit-test-key"));
    }

    @Test
    void writesCiphertextReadsPlaintext() {
        String column = converter.convertToDatabaseColumn("whsec_123");
        assertTrue(column.startsWith(SecretCipher.PREFIX));
        assertEquals("whsec_123", converter.convertToEntityAttribute(column));
    }

    @Test
    void legacyPlaintextRowReadsUnchanged() {
        assertEquals("sk_test_legacy", converter.convertToEntityAttribute("sk_test_legacy"));
    }

    @Test
    void alreadyEncryptedValueIsNotDoubleWrapped() {
        String once = converter.convertToDatabaseColumn("x");
        assertEquals(once, converter.convertToDatabaseColumn(once));
    }

    @Test
    void emptyMeansNoSecretAndStaysEmpty() {
        assertEquals("", converter.convertToDatabaseColumn(""));
        assertEquals("", converter.convertToEntityAttribute(""));
    }

    @Test
    void nullBothWays() {
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
    }
}
