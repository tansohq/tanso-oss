package com.tansoflow.tansocore.controller.tanso.event;

import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.model.event.events.EventDto;
import com.tansoflow.tansocore.model.event.events.EventGroupDto;
import com.tansoflow.tansocore.model.event.events.type.EventType;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.model.response.PagedResponse;
import com.tansoflow.tansocore.service.internal.data.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/tanso/events")
@PreAuthorize("hasRole('TANSO_UI')")
@Tag(name = "Event", description = "Event management for Tanso")
public class EventController {
    private final EventService eventService;

    @GetMapping
    @Operation(summary = "Get paginated events", description = "Retrieves a paginated list of events associated with the account", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<PagedResponse<EventDto>>> getEvents(
            @AuthenticationPrincipal UserContext userContext,
            @Parameter(description = "Start of the occurredAt date range") @RequestParam(required = false) Instant start,
            @Parameter(description = "End of the occurredAt date range") @RequestParam(required = false) Instant end,
            @Parameter(description = "Customer reference ID filter") @RequestParam(required = false) String customerReferenceId,
            @Parameter(description = "Plan identifier filter") @RequestParam(required = false) UUID planId,
            @Parameter(description = "Feature identifier filter") @RequestParam(required = false) UUID featureId,
            @Parameter(description = "Event type filter") @RequestParam(required = false) EventType eventType,
            @Parameter(description = "Model name filter") @RequestParam(required = false) String model,
            @Parameter(description = "Model provider filter") @RequestParam(required = false) String modelProvider,
            @Parameter(description = "Event name filter (partial match)") @RequestParam(required = false) String eventName,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        log.info("Retrieving events for account: {}, filters -> start: {}, end: {}, customerRef: {}, planId: {}, featureId: {}, eventType: {}, model: {}, modelProvider: {}, eventName: {}, page: {}, size: {}",
                userContext.getAccountId(), start, end, customerReferenceId, planId, featureId, eventType, model, modelProvider, eventName, page, size);

        UUID accountId = UUID.fromString(userContext.getAccountId());
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<EventDto> events = eventService.getEvents(accountId, start, end, customerReferenceId, planId, featureId, eventType, model, modelProvider, eventName, pageable);

        return ResponseEntity.ok(ApiResponse.<PagedResponse<EventDto>>builder()
                .success(true)
                .data(PagedResponse.fromPage(events))
                .build());
    }

    @GetMapping("/grouped")
    @Operation(summary = "Get grouped event aggregations", description = "Returns events aggregated by a specified field with totals", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<List<EventGroupDto>>> getGroupedEvents(
            @AuthenticationPrincipal UserContext userContext,
            @Parameter(description = "Field to group by: MODEL, MODEL_PROVIDER, CUSTOMER, FEATURE, EVENT_NAME")
            @RequestParam String groupBy,
            @Parameter(description = "Start of the occurredAt date range") @RequestParam(required = false) Instant start,
            @Parameter(description = "End of the occurredAt date range") @RequestParam(required = false) Instant end,
            @Parameter(description = "Customer reference ID filter") @RequestParam(required = false) String customerReferenceId,
            @Parameter(description = "Plan identifier filter") @RequestParam(required = false) UUID planId,
            @Parameter(description = "Feature identifier filter") @RequestParam(required = false) UUID featureId,
            @Parameter(description = "Event type filter") @RequestParam(required = false) EventType eventType,
            @Parameter(description = "Model name filter") @RequestParam(required = false) String model,
            @Parameter(description = "Model provider filter") @RequestParam(required = false) String modelProvider,
            @Parameter(description = "Event name filter (partial match)") @RequestParam(required = false) String eventName) {

        // Validate groupBy before passing to service
        var validGroupByValues = java.util.Set.of("MODEL", "MODEL_PROVIDER", "CUSTOMER", "FEATURE", "EVENT_NAME");
        if (!validGroupByValues.contains(groupBy.toUpperCase())) {
            return ResponseEntity.badRequest().body(ApiResponse.<List<EventGroupDto>>builder()
                    .success(false)
                    .build());
        }

        UUID accountId = UUID.fromString(userContext.getAccountId());
        List<EventGroupDto> groups = eventService.getGroupedEvents(accountId, groupBy, start, end, customerReferenceId, planId, featureId, eventType, model, modelProvider, eventName);

        return ResponseEntity.ok(ApiResponse.<List<EventGroupDto>>builder()
                .success(true)
                .data(groups)
                .build());
    }
}
