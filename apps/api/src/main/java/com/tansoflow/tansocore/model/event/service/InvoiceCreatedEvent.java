package com.tansoflow.tansocore.model.event.service;

import java.util.UUID;

// Invoice was created in Tanso DB
public record InvoiceCreatedEvent(
        UUID accountId,
        UUID invoiceId,
        String invoiceType
) implements DomainEvent { }
