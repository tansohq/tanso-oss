package com.tansoflow.tansocore.model.event.service;



import java.util.UUID;

public record SubscriptionRolloverEvent(UUID accountId, UUID invoiceId) implements DomainEvent {
}
