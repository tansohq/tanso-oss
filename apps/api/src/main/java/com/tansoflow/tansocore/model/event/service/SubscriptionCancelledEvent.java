package com.tansoflow.tansocore.model.event.service;

import java.util.UUID;

public record SubscriptionCancelledEvent(UUID accountId, UUID subscriptionId, String cancelMode) implements DomainEvent {
}
