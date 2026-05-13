package com.tansoflow.tansocore.model.event.service;

import com.tansoflow.tansocore.model.api.external.StripeMode;

import java.util.UUID;

public record StripeModeChangedEvent(UUID accountId, StripeMode oldMode, StripeMode newMode) implements DomainEvent {
}
