package com.tansoflow.tansocore.model.event.service;

import com.tansoflow.tansocore.model.billing.InvoiceDto;

import java.util.UUID;

public record SubscriptionCreatedEvent(UUID accountId, UUID subscriptionId, boolean initialInvoice, InvoiceDto invoice) implements DomainEvent {
}
