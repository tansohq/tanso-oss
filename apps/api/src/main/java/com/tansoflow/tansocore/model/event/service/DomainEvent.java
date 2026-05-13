package com.tansoflow.tansocore.model.event.service;

import java.util.UUID;

public sealed interface DomainEvent permits CustomerCreatedEvent, CustomerUpdatedEvent, InvoiceCreatedEvent, InvoicePaidEvent, PlanCreatedEvent, PlanUpdatedEvent, StripeModeChangedEvent, SubscriptionActivatedEvent, SubscriptionCancelledEvent, SubscriptionCreatedEvent, SubscriptionPlanChangedEvent, SubscriptionRolloverEvent {
    UUID accountId();
}

