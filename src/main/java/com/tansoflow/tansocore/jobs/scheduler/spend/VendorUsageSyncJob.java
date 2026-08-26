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
package com.tansoflow.tansocore.jobs.scheduler.spend;

import com.tansoflow.tansocore.service.internal.spend.VendorSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Re-pulls the last three days from every connected vendor. Reports lag by up to an hour, so yesterday is never final on the first pass. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.modules.build.enabled", havingValue = "true", matchIfMissing = true)
public class VendorUsageSyncJob {
    private final VendorSyncService vendorSyncService;

    @Scheduled(cron = "${jobs.vendorUsageSync.cron:0 15 * * * *}")
    @SchedulerLock(name = "vendorUsageSyncJob", lockAtMostFor = "PT50M", lockAtLeastFor = "PT1M")
    public void run() {
        log.info("Vendor usage sync starting");
        try {
            vendorSyncService.syncAll();
        } catch (RuntimeException e) {
            log.error("Vendor usage sync aborted: {}", e.getMessage(), e);
        }
    }
}
