package com.tansoflow.tansocore.model.event.service;

import java.util.UUID;

public record PlanUpdatedEvent(UUID accountId, UUID planId) implements DomainEvent {
}
