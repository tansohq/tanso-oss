package com.tansoflow.tansocore.model.event.events.type;
import com.fasterxml.jackson.annotation.JsonCreator;

public enum EventType {
    CLIENT_TRACKED, // to be used when a user creates a custom tracking event
    ENTITLEMENT_CHECKED,
    ENTITLEMENT_REVOKED,
    CUSTOMER_CREATED,
    PLAN_CREATED,
    SUBSCRIPTION_CREATED,
    SUBSCRIPTION_CANCELLED,
    SUBSCRIPTION_UPGRADED,
    SUBSCRIPTION_DOWNGRADED,
    INVOICE_CREATED;

    @JsonCreator
    public static EventType fromString(String value) {
        if (value == null) {
            return null;
        }
        return EventType.valueOf(value.toUpperCase());
    }
}
