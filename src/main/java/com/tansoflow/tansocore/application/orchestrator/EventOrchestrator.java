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
package com.tansoflow.tansocore.application.orchestrator;

import com.tansoflow.tansocore.model.event.service.InvoicePaidEvent;
import com.tansoflow.tansocore.service.internal.monetization.InvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@RequiredArgsConstructor
@Service
public class EventOrchestrator {
    private final InvoiceService invoiceService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvoicePaid(InvoicePaidEvent event) {
        log.info("Received InvoicePaidEvent: {}", event);
        invoiceService.markInvoiceAsPaid(event.invoiceId().toString());

    }

}
