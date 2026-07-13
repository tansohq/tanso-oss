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
package com.tansoflow.tansocore.jobs.scheduler.credit;

import com.tansoflow.tansocore.service.internal.monetization.CreditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class CreditExpirationJob {
    private final CreditService creditService;

    @Scheduled(cron = "${jobs.creditExpiration.cron:0 */15 * * * *}")
    @SchedulerLock(name = "creditExpirationJob", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void run() {
        try {
            log.info("Running Credit Expiration Job");
            creditService.processExpiredGrants();
            log.info("Completed Credit Expiration Job");
        } catch (Exception e) {
            log.error("Job {} failed", getClass().getSimpleName(), e);
        }
    }
}
