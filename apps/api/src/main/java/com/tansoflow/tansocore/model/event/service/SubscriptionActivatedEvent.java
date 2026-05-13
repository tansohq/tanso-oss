package com.tansoflow.tansocore.model.event.service;

import java.util.UUID;

public record SubscriptionActivatedEvent(UUID accountId, UUID subscriptionId) implements DomainEvent {
}
