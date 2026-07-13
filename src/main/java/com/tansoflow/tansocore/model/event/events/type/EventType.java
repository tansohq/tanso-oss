/*
 * Tanso Core - open-source B2B SaaS monetization engine
 * Copyright (C) 2026  Douglas Baek
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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
