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
package com.tansoflow.tansocore.jobs.scheduler.invoice;

import com.tansoflow.tansocore.service.internal.monetization.InvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class InvoiceCancelledJob {
    private final InvoiceService invoiceService;

    @Scheduled(cron = "${jobs.invoiceCancelled.cron}")
    @SchedulerLock(name = "invoiceCancelledJob", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    @Transactional
    public void run() {
        log.info("Running Invoice Due Job");
        invoiceService.processCancelledInvoices();
    }

}
