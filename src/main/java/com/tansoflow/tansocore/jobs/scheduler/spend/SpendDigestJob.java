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

import com.tansoflow.tansocore.entity.AccountSetting;
import com.tansoflow.tansocore.repository.AccountSettingRepository;
import com.tansoflow.tansocore.service.internal.spend.SpendDigestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Monday morning: last week's spend per unit to every account that opted in. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.modules.build.enabled", havingValue = "true", matchIfMissing = true)
public class SpendDigestJob {
    private final AccountSettingRepository accountSettingRepository;
    private final SpendDigestService digestService;

    @Scheduled(cron = "${jobs.spendDigest.cron:0 0 8 * * MON}", zone = "UTC")
    @SchedulerLock(name = "spendDigestJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT5M")
    public void run() {
        for (AccountSetting setting : accountSettingRepository.findAll()) {
            if (!setting.isSpendDigestEnabled()) {
                continue;
            }
            try {
                digestService.send(setting.getId().toString());
            } catch (RuntimeException e) {
                log.warn("Spend digest failed for account {}: {}", setting.getId(), e.getMessage(), e);
            }
        }
    }
}
