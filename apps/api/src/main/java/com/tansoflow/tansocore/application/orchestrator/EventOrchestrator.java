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
