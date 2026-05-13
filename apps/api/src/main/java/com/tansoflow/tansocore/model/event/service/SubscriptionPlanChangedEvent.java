package com.tansoflow.tansocore.model.event.service;

import java.util.UUID;

public record SubscriptionPlanChangedEvent(UUID accountId, UUID subscriptionId, boolean prorate) implements DomainEvent {
}
