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
package com.tansoflow.tansocore.service.internal.idempotency.implementation;

import com.tansoflow.tansocore.entity.IdempotencyRecord;
import com.tansoflow.tansocore.model.exception.IdempotencyConflictException;
import com.tansoflow.tansocore.repository.IdempotencyRecordRepository;
import com.tansoflow.tansocore.service.internal.idempotency.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class IdempotencyServiceImpl implements IdempotencyService {

    private final IdempotencyRecordRepository idempotencyRecordRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredResponse> findReplay(UUID accountId, String endpoint, String idempotencyKey,
                                               String requestHash) {
        return idempotencyRecordRepository
                .findByAccountIdAndEndpointAndIdempotencyKey(accountId, endpoint, idempotencyKey)
                .map(record -> {
                    if (!record.getRequestHash().equals(requestHash)) {
                        throw new IdempotencyConflictException(
                                "Idempotency-Key '" + idempotencyKey + "' was already used with a different request body");
                    }
                    log.info("Idempotent replay for account {} endpoint {} key {}", accountId, endpoint, idempotencyKey);
                    return new StoredResponse(record.getResponseStatus(), record.getResponseBody());
                });
    }

    @Override
    // REQUIRES_NEW so a store race (two concurrent first requests) can fail alone
    // without poisoning the caller's transaction; the loser's response was already
    // sent, losing the race only means one fewer stored replay.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void store(UUID accountId, String endpoint, String idempotencyKey, String requestHash, int status,
                      String body) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setAccountId(accountId);
        record.setEndpoint(endpoint);
        record.setIdempotencyKey(idempotencyKey);
        record.setRequestHash(requestHash);
        record.setResponseStatus(status);
        record.setResponseBody(body);
        try {
            idempotencyRecordRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent idempotency store for account {} endpoint {} key {} — keeping the first response",
                    accountId, endpoint, idempotencyKey);
        }
    }

    @Override
    public String hash(byte[] requestBody) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(requestBody != null ? requestBody
                    : new byte[0]));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
