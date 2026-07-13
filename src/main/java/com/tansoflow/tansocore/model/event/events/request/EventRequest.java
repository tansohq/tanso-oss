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
package com.tansoflow.tansocore.model.event.events.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Schema(description = "Request object for creating an event")
public class EventRequest {

    @Schema(description = "Idempotency key for the event. Auto-generated if omitted.", example = "event_12345")
    private String eventIdempotencyKey;

    @Schema(description = "Flow identifier associated with the event")
    private String flowId;

    @Schema(description = "The key of the feature this event tracks usage for. Used to resolve pricing rules and billing.")
    private String featureKey;

    @Schema(description = "Feature UUID. If provided, supersedes featureKey — no lookup is performed.")
    private UUID featureId;

    @Schema(description = "Name of the event")
    @NotNull
    private String eventName;

    @Schema(description = "Timestamp when the event occurred")
    private Instant occurredAt;

    @Schema(description = "Customer identifier (UUID) associated with the event. Use either customerId or customerReferenceId, not both.")
    private UUID customerId;

    @Schema(description = "Customer reference ID (your internal customer identifier). " +
            "Use either customerId, customerReferenceId, or stripeCustomerId.",
            example = "user_12345")
    private String customerReferenceId;

    @Schema(description = "Stripe customer ID (cus_...). Creates a customer linked via Stripe bridge table " +
            "without setting externalClientCustomerId. Preferred for Observe mode.",
            example = "cus_abc123")
    private String stripeCustomerId;

    @Schema(description = "Subscription identifier associated with the event")
    private UUID subscriptionId;

    @Schema(description = "Entitlement identifier associated with the event")
    private UUID entitlementId;

    @Schema(description = "Invoice identifier associated with the event")
    private UUID invoiceId;

    @Schema(description = "Cost amount associated with the event. Optional - if omitted, Tanso will " +
            "automatically calculate it based on usageUnits and the configured cost_rate in the plan rule.",
            example = "0.05")
    private BigDecimal costAmount;

    @Schema(description = "Revenue amount associated with the event. Optional - if omitted, Tanso will " +
            "automatically calculate it based on usageUnits and the configured pricing rule. " +
            "Use this to pass through your own revenue figures for observe-mode margin tracking.",
            example = "0.10")
    private BigDecimal revenueAmount;

    @Schema(description = "Usage units (e.g., number of tokens, API calls, storage GB). " +
            "Used for billing calculations and margin analysis.", example = "1000.00")
    private BigDecimal usageUnits;

    @Schema(description = "Metadata associated with the event")
    private Map<String, Object> meta;

    @Schema(description = "Structured cost input for model-aware cost tracking")
    private CostInput costInput;

    @Data
    @Schema(description = "Structured cost input for model-aware cost tracking. " +
            "Provides typed fields for AI model name, provider, and cost-relevant quantity.")
    public static class CostInput {
        @Schema(description = "AI model name (e.g., gpt-4, claude-3-opus)", example = "gpt-4")
        private String model;

        @Schema(description = "AI model provider (e.g., openai, anthropic)", example = "openai")
        private String modelProvider;

        @Deprecated
        @Schema(description = "Cost-relevant quantity (e.g., total token count). Deprecated — use inputTokens and outputTokens instead.",
                example = "50000", deprecated = true)
        private BigDecimal costUnits;

        @Schema(description = "Input token count for AI model cost calculation", example = "3000")
        private BigDecimal inputTokens;

        @Schema(description = "Output token count for AI model cost calculation", example = "500")
        private BigDecimal outputTokens;
    }
}
