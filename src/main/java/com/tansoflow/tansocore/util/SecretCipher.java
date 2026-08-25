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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Encrypts third-party credentials before they touch the database.
 *
 * Stripe keys and vendor admin keys used to sit in external_api_keys as
 * plaintext, so a database dump handed over every integration the operator
 * had connected. Everything now goes through AES-256-GCM under one key from
 * APP_SECRETS_KEY. The key material is the SHA-256 of whatever string the
 * operator sets, so any reasonably long random value works (same shape as
 * JWT_SECRET) and there is no base64/length format to get wrong.
 *
 * Ciphertext is stored as {@code enc:v1:<base64(iv || ciphertext+tag)>}. The
 * prefix is how readers tell an encrypted row from a legacy plaintext one.
 */
@Component
public class SecretCipher {
    public static final String PREFIX = "enc:v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(@Value("${app.secrets.key:}") String material) {
        if (material == null || material.isBlank()) {
            throw new IllegalStateException(
                    "APP_SECRETS_KEY is not set. It encrypts stored integration credentials "
                            + "(Stripe keys, vendor admin keys). Generate one with: openssl rand -base64 48");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(digest, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(ciphertext, 0, packed, iv.length, ciphertext.length);
            return PREFIX + Base64.getEncoder().encodeToString(packed);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt secret", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (!isEncrypted(stored)) {
            throw new IllegalArgumentException("Value is not an enc:v1 ciphertext");
        }
        try {
            byte[] packed = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = Arrays.copyOfRange(packed, 0, IV_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(packed, IV_BYTES, packed.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Could not decrypt a stored secret. APP_SECRETS_KEY probably changed since the row was written.", e);
        }
    }
}
