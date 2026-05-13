package com.tansoflow.tansocore.model.event.service;



import java.util.UUID;

public record CustomerCreatedEvent(UUID accountId, UUID customerId) implements DomainEvent {
}
