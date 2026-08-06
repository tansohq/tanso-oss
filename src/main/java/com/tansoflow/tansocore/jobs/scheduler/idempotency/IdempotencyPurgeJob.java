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
package com.tansoflow.tansocore.jobs.scheduler.idempotency;

import com.tansoflow.tansocore.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Component
@Slf4j
@RequiredArgsConstructor
public class IdempotencyPurgeJob {

    private static final Duration RETENTION = Duration.ofHours(24);

    private final IdempotencyRecordRepository idempotencyRecordRepository;

    @Scheduled(fixedDelayString = "PT1H")
    @Transactional
    public void purgeExpiredRecords() {
        int deleted = idempotencyRecordRepository.deleteByCreatedAtBefore(Instant.now().minus(RETENTION));
        if (deleted > 0) {
            log.info("Purged {} idempotency records older than {}", deleted, RETENTION);
        }
    }
}
