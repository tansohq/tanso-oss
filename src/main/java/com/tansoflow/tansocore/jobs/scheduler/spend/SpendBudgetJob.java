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

import com.tansoflow.tansocore.entity.SpendBudget;
import com.tansoflow.tansocore.repository.SpendBudgetRepository;
import com.tansoflow.tansocore.service.internal.spend.SpendBudgetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Checks every account's budgets once an hour, after the usage sync has had its turn. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.modules.build.enabled", havingValue = "true", matchIfMissing = true)
public class SpendBudgetJob {
    private final SpendBudgetRepository budgetRepository;
    private final SpendBudgetService budgetService;

    @Scheduled(cron = "${jobs.spendBudget.cron:0 30 * * * *}")
    @SchedulerLock(name = "spendBudgetJob", lockAtMostFor = "PT20M", lockAtLeastFor = "PT1M")
    public void run() {
        Set<UUID> accounts = new LinkedHashSet<>();
        for (SpendBudget b : budgetRepository.findAll()) {
            accounts.add(b.getAccountId());
        }
        for (UUID account : accounts) {
            try {
                budgetService.evaluate(account.toString());
            } catch (RuntimeException e) {
                log.warn("Budget evaluation failed for account {}: {}", account, e.getMessage(), e);
            }
        }
    }
}
