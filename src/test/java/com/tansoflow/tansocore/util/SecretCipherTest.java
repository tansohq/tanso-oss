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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretCipherTest {

    private static SecretCipher cipher(String key) {
        return new SecretCipher(key);
    }

    @Test
    void roundTripsAndPrefixesCiphertext() {
        SecretCipher cipher = cipher("unit-test-key");
        String stored = cipher.encrypt("sk_live_abc123");
        assertTrue(stored.startsWith(SecretCipher.PREFIX));
        assertTrue(cipher.isEncrypted(stored));
        assertFalse(stored.contains("sk_live"));
        assertEquals("sk_live_abc123", cipher.decrypt(stored));
    }

    @Test
    void freshIvPerEncrypt() {
        SecretCipher cipher = cipher("unit-test-key");
        assertNotEquals(cipher.encrypt("same"), cipher.encrypt("same"));
    }

    @Test
    void differentKeyCannotDecrypt() {
        String stored = cipher("key-one").encrypt("secret");
        assertThrows(IllegalStateException.class, () -> cipher("key-two").decrypt(stored));
    }

    @Test
    void refusesToDecryptPlaintext() {
        assertThrows(IllegalArgumentException.class, () -> cipher("k").decrypt("sk_live_plain"));
        assertFalse(cipher("k").isEncrypted("sk_live_plain"));
    }

    @Test
    void nullPassesThrough() {
        assertNull(cipher("k").encrypt(null));
        assertNull(cipher("k").decrypt(null));
    }

    @Test
    void failsClosedWithoutKey() {
        assertThrows(IllegalStateException.class, () -> cipher(null));
        assertThrows(IllegalStateException.class, () -> cipher("  "));
    }
}
