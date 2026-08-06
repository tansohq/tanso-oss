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
package com.tansoflow.tansocore.service.internal.idempotency;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyService {

    record StoredResponse(int status, String body) {
    }

    /**
     * Returns the stored response for a replay of the same request, empty when
     * this key has not been seen. Throws IdempotencyConflictException when the
     * key exists with a different request hash.
     */
    Optional<StoredResponse> findReplay(UUID accountId, String endpoint, String idempotencyKey, String requestHash);

    void store(UUID accountId, String endpoint, String idempotencyKey, String requestHash, int status, String body);

    String hash(byte[] requestBody);
}
