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
package com.tansoflow.tansocore.model.event.events;

import com.tansoflow.tansocore.model.event.events.type.EventType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Schema(description = "Data Transfer Object for EventDto information")
public class EventDto {
    @Schema(description = "Unique identifier of the event")
    private UUID id;

    @Schema(description = "Account identifier associated with the event")
    private UUID accountId;

    @Schema(description = "Idempotency key for the event", example = "event_12345")
    private String eventIdempotencyKey;

    @Schema(description = "Flow identifier associated with the event")
    private String flowId;

    @Schema(description = "Name of the event")
    private String eventName;

    @Schema(description = "Timestamp when the event occurred")
    private Instant occurredAt;

    @Schema(description = "Customer identifier (UUID) associated with the event")
    private UUID customerId;

    @Schema(description = "Customer reference ID (external identifier) associated with the event. " +
            "Use this if you don't have the Tanso customer UUID.",
            example = "user_abc123")
    private String customerReferenceId;

    @Schema(description = "Stripe customer ID (cus_...). Resolves customer via the stripe_customers bridge table.",
            example = "cus_abc123")
    private String stripeCustomerId;

    @Schema(description = "The key of the feature this event tracks usage for. Transient — not persisted.")
    private String featureKey;

    @Schema(description = "Feature identifier associated with the event")
    private UUID featureId;

    @Schema(description = "Subscription identifier associated with the event")
    private UUID subscriptionId;

    @Schema(description = "Entitlement identifier associated with the event")
    private UUID entitlementId;

    @Schema(description = "Invoice identifier associated with the event")
    private UUID invoiceId;

    @Schema(description = "Cost amount associated with the event", example = "0.05")
    private java.math.BigDecimal costAmount;

    @Schema(description = "Unit of the cost amount (e.g. USD, credits)", example = "USD")
    private String costUnit;

    @Schema(description = "Customer-facing charge (revenue)", example = "100.00")
    private java.math.BigDecimal revenueAmount;

    @Schema(description = "Unit of the revenue amount (e.g. USD, credits)", example = "USD")
    private String revenueUnit;

    @Schema(description = "Usage units (e.g., number of tokens, seconds, etc.)", example = "1000.00")
    private java.math.BigDecimal usageUnits;

    @Schema(description = "Type of usage units (e.g., tokens, api_calls, storage_gb)", example = "tokens")
    private String usageUnitType;

    @Schema(description = "AI model name (e.g., gpt-4, claude-3-opus)")
    private String model;

    @Schema(description = "AI model provider (e.g., openai, anthropic)")
    private String modelProvider;

    @Schema(description = "Cost-relevant quantity (e.g., token count)")
    private java.math.BigDecimal costUnits;

    @Schema(description = "Input token count for AI model cost calculation", example = "3000")
    private java.math.BigDecimal inputTokens;

    @Schema(description = "Output token count for AI model cost calculation", example = "500")
    private java.math.BigDecimal outputTokens;

    @Schema(description = "Additional properties associated with the event")
    private Map<String, Object> properties;

    @Schema(description = "Metadata associated with the event")
    private Map<String, Object> meta;

    @Schema(description = "Context associated with the event")
    private Map<String, Object> context;

    @Schema(description = "Whether the customerId references a native Tanso customer")
    private Boolean customerIsNative;

    @Schema(description = "Whether the featureId references a native Tanso feature")
    private Boolean featureIsNative;

    @Schema(description = "Whether the subscriptionId references a native Tanso subscription")
    private Boolean subscriptionIsNative;

    @Schema(description = "Whether the entitlementId references a native Tanso entitlement")
    private Boolean entitlementIsNative;

    @Schema(description = "Whether the invoiceId references a native Tanso invoice")
    private Boolean invoiceIsNative;

    @Schema(description = "Ingest error message if any")
    private String ingestError;

    @Schema(description = "Timestamp when the event was created")
    private Instant createdAt;

    @Schema(description = "Timestamp when the event was last modified")
    private Instant modifiedAt;

    @Schema(description = "Event type associated with the event")
    private EventType eventType;
}
