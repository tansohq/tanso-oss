package com.tansoflow.tansocore.mcp.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.model.event.events.type.EventType;
import com.tansoflow.tansocore.service.internal.data.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mcp.enabled", havingValue = "true")
public class AdminEventTools {

    private final EventService eventService;
    private final ObjectMapper objectMapper;

    @Tool(description = "Query events with filtering and pagination. "
            + "Returns paginated events sorted by creation time (newest first). "
            + "All filters are optional — omit them to get all events.")
    public String getEvents(
            @ToolParam(description = "Filter start time (inclusive) in ISO-8601, e.g. '2026-01-01T00:00:00Z'", required = false) String start,
            @ToolParam(description = "Filter end time (exclusive) in ISO-8601, e.g. '2026-04-01T00:00:00Z'", required = false) String end,
            @ToolParam(description = "Filter by customer's external reference ID", required = false) String customerReferenceId,
            @ToolParam(description = "Filter by plan ID (UUID)", required = false) String planId,
            @ToolParam(description = "Filter by feature ID (UUID)", required = false) String featureId,
            @ToolParam(description = "Filter by event type: CLIENT_TRACKED, ENTITLEMENT_CHECKED, CUSTOMER_CREATED, PLAN_CREATED, "
                    + "SUBSCRIPTION_CREATED, SUBSCRIPTION_CANCELLED, SUBSCRIPTION_UPGRADED, SUBSCRIPTION_DOWNGRADED, "
                    + "INVOICE_CREATED, ENTITLEMENT_REVOKED", required = false) String eventType,
            @ToolParam(description = "Filter by AI model name, e.g. 'gpt-4'", required = false) String model,
            @ToolParam(description = "Filter by AI model provider, e.g. 'openai'", required = false) String modelProvider,
            @ToolParam(description = "Filter by event name (partial match)", required = false) String eventName,
            @ToolParam(description = "Page number (0-indexed, default 0)", required = false) String page,
            @ToolParam(description = "Page size (default 20, max 100)", required = false) String size) {
        try {
            UUID accountId = UUID.fromString(getAccountId());

            Instant parsedStart = parseInstant(start);
            Instant parsedEnd = parseInstant(end);
            if (parsedStart == null && start != null && !start.isBlank()) {
                return "{\"error\": \"invalid_request\", \"message\": \"start must be a valid ISO-8601 timestamp\"}";
            }
            if (parsedEnd == null && end != null && !end.isBlank()) {
                return "{\"error\": \"invalid_request\", \"message\": \"end must be a valid ISO-8601 timestamp\"}";
            }

            UUID parsedPlanId = parseUuid(planId);
            UUID parsedFeatureId = parseUuid(featureId);

            EventType parsedEventType = null;
            if (eventType != null && !eventType.isBlank()) {
                try {
                    parsedEventType = EventType.valueOf(eventType.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return "{\"error\": \"invalid_request\", \"message\": \"Invalid eventType. Valid values: CLIENT_TRACKED, ENTITLEMENT_CHECKED, etc.\"}";
                }
            }

            int parsedPage = parseIntOrDefault(page, 0);
            int parsedSize = Math.min(parseIntOrDefault(size, 20), 100);

            var pageable = PageRequest.of(parsedPage, parsedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
            var result = eventService.getEvents(accountId, parsedStart, parsedEnd,
                    customerReferenceId, parsedPlanId, parsedFeatureId, parsedEventType,
                    model, modelProvider, eventName, pageable);

            Map<String, Object> response = new HashMap<>();
            response.put("items", result.getContent());
            response.put("totalElements", result.getTotalElements());
            response.put("totalPages", result.getTotalPages());
            response.put("page", result.getNumber());
            response.put("size", result.getSize());
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize events\"}";
        } catch (Exception e) {
            return "{\"error\": \"query_failed\", \"message\": \"" + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "Get aggregated event data grouped by a dimension. "
            + "Returns group key, event count, total cost, total revenue, total usage units, and last event timestamp. "
            + "All filters are optional.")
    public String getGroupedEvents(
            @ToolParam(description = "Dimension to group by: MODEL, MODEL_PROVIDER, CUSTOMER, FEATURE, or EVENT_NAME") String groupBy,
            @ToolParam(description = "Filter start time (inclusive) in ISO-8601", required = false) String start,
            @ToolParam(description = "Filter end time (exclusive) in ISO-8601", required = false) String end,
            @ToolParam(description = "Filter by customer's external reference ID", required = false) String customerReferenceId,
            @ToolParam(description = "Filter by plan ID (UUID)", required = false) String planId,
            @ToolParam(description = "Filter by feature ID (UUID)", required = false) String featureId,
            @ToolParam(description = "Filter by event type", required = false) String eventType,
            @ToolParam(description = "Filter by AI model name", required = false) String model,
            @ToolParam(description = "Filter by AI model provider", required = false) String modelProvider,
            @ToolParam(description = "Filter by event name (partial match)", required = false) String eventName) {
        try {
            UUID accountId = UUID.fromString(getAccountId());

            Instant parsedStart = parseInstant(start);
            Instant parsedEnd = parseInstant(end);
            UUID parsedPlanId = parseUuid(planId);
            UUID parsedFeatureId = parseUuid(featureId);

            EventType parsedEventType = null;
            if (eventType != null && !eventType.isBlank()) {
                try {
                    parsedEventType = EventType.valueOf(eventType.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return "{\"error\": \"invalid_request\", \"message\": \"Invalid eventType\"}";
                }
            }

            var result = eventService.getGroupedEvents(accountId, groupBy,
                    parsedStart, parsedEnd, customerReferenceId, parsedPlanId,
                    parsedFeatureId, parsedEventType, model, modelProvider, eventName);
            return objectMapper.writeValueAsString(result);
        } catch (IllegalArgumentException e) {
            return "{\"error\": \"invalid_request\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize grouped events\"}";
        }
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String getAccountId() {
        UserContext ctx = (UserContext) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return ctx.getAccountId();
    }
}
