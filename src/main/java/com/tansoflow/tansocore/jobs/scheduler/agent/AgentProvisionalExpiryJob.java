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
package com.tansoflow.tansocore.jobs.scheduler.agent;

import com.tansoflow.tansocore.service.client.AgentLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.modules.monetization.enabled", havingValue = "true", matchIfMissing = true)
public class AgentProvisionalExpiryJob {
    private final AgentLifecycleService agentLifecycleService;

    @Scheduled(cron = "${jobs.agentProvisionalExpiry.cron:0 30 3 * * *}")
    @SchedulerLock(name = "agentProvisionalExpiryJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void run() {
        try {
            log.info("Running Agent Provisional Expiry Job");
            int expired = agentLifecycleService.expireProvisional(Instant.now());
            log.info("Completed Agent Provisional Expiry Job: {} customers expired", expired);
        } catch (Exception e) {
            log.error("Job {} failed", getClass().getSimpleName(), e);
        }
    }
}
