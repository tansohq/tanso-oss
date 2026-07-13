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
package com.tansoflow.tansocore.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.model.event.events.EventDto;
import com.tansoflow.tansocore.model.event.events.EventIngestionResult;
import com.tansoflow.tansocore.model.event.events.type.EventType;
import com.tansoflow.tansocore.service.internal.data.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mcp.enabled", havingValue = "true")
public class EventTools {

    private final EventService eventService;
    private final ObjectMapper objectMapper;

    @Tool(description = "INGESTS a usage event for billing and metering. "
            + "SIDE EFFECT: Records usage that affects billing calculations and entitlement limits. "
            + "Events are idempotent — sending the same idempotency key twice will be rejected.")
    public String ingestEvent(
            @ToolParam(description = "Unique idempotency key to prevent duplicate processing, e.g. 'evt_abc123'") String eventIdempotencyKey,
            @ToolParam(description = "Name of the event, e.g. 'api_call' or 'message_sent'") String eventName,
            @ToolParam(description = "When the event occurred, in ISO-8601 format, e.g. '2026-03-16T10:30:00Z'") String occurredAt,
            @ToolParam(description = "The customer's external reference ID") String customerReferenceId,
            @ToolParam(description = "The feature key this event is tracked against, e.g. 'api_access'") String featureKey,
            @ToolParam(description = "Number of usage units consumed, e.g. '1' or '1000'. Defaults to 1 if omitted.", required = false) String usageUnits,
            @ToolParam(description = "AI model name, e.g. 'gpt-4', 'claude-3-opus'", required = false) String model,
            @ToolParam(description = "AI model provider, e.g. 'openai', 'anthropic'", required = false) String modelProvider,
            @ToolParam(description = "Cost-relevant quantity (e.g. token count). Falls back to usageUnits if omitted.", required = false) String costUnits,
            @ToolParam(description = "Cost amount in currency (e.g. '0.05'). If omitted, calculated from plan rules.", required = false) String costAmount,
            @ToolParam(description = "Revenue amount in currency (e.g. '0.10'). If omitted, calculated from plan rules.", required = false) String revenueAmount) {
        try {
            UUID accountId = UUID.fromString(getAccountId());

            if (eventService.existsByEventIdempotencyKey(accountId, eventIdempotencyKey)) {
                return "{\"error\": \"duplicate_event\", \"message\": \"An event with this idempotency key already exists\"}";
            }

            Instant parsedOccurredAt;
            try {
                parsedOccurredAt = Instant.parse(occurredAt);
            } catch (DateTimeParseException e) {
                return "{\"error\": \"invalid_request\", \"message\": \"occurredAt must be a valid ISO-8601 timestamp, e.g. '2026-03-16T10:30:00Z'\"}";
            }

            BigDecimal parsedUsageUnits = BigDecimal.ONE;
            if (usageUnits != null && !usageUnits.isBlank()) {
                try {
                    parsedUsageUnits = new BigDecimal(usageUnits);
                } catch (NumberFormatException e) {
                    return "{\"error\": \"invalid_request\", \"message\": \"usageUnits must be a valid number\"}";
                }
            }

            EventDto eventDto = new EventDto();
            eventDto.setEventIdempotencyKey(eventIdempotencyKey);
            eventDto.setEventName(eventName);
            eventDto.setOccurredAt(parsedOccurredAt);
            eventDto.setCustomerReferenceId(customerReferenceId);
            eventDto.setFeatureKey(featureKey);
            eventDto.setUsageUnits(parsedUsageUnits);
            eventDto.setEventType(EventType.CLIENT_TRACKED);
            eventDto.setAccountId(accountId);

            if (model != null && !model.isBlank()) {
                eventDto.setModel(model);
            }
            if (modelProvider != null && !modelProvider.isBlank()) {
                eventDto.setModelProvider(modelProvider);
            }
            if (costUnits != null && !costUnits.isBlank()) {
                try {
                    eventDto.setCostUnits(new BigDecimal(costUnits));
                } catch (NumberFormatException e) {
                    return "{\"error\": \"invalid_request\", \"message\": \"costUnits must be a valid number\"}";
                }
            }
            if (costAmount != null && !costAmount.isBlank()) {
                try {
                    eventDto.setCostAmount(new BigDecimal(costAmount));
                } catch (NumberFormatException e) {
                    return "{\"error\": \"invalid_request\", \"message\": \"costAmount must be a valid number\"}";
                }
            }
            if (revenueAmount != null && !revenueAmount.isBlank()) {
                try {
                    eventDto.setRevenueAmount(new BigDecimal(revenueAmount));
                } catch (NumberFormatException e) {
                    return "{\"error\": \"invalid_request\", \"message\": \"revenueAmount must be a valid number\"}";
                }
            }

            EventIngestionResult result = eventService.createEvent(eventDto);

            if (result.isUsageLimitExceeded()) {
                return "{\"success\": true, \"warning\": \"usage_limit_exceeded\", \"message\": \"" + result.getMessage() + "\"}";
            }

            return "{\"success\": true}";
        } catch (Exception e) {
            return "{\"error\": \"ingestion_failed\", \"message\": \"" + e.getMessage() + "\"}";
        }
    }

    private String getAccountId() {
        UserContext ctx = (UserContext) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return ctx.getAccountId();
    }
}
