package com.tansoflow.tansocore.model.event.service;



import java.util.UUID;

// Invoice was marked PAID in Tanso DB
public record InvoicePaidEvent(
        UUID accountId,
        UUID invoiceId
) implements DomainEvent { }
