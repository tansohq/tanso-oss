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
package com.tansoflow.tansocore.controller.client;

import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.mapper.event.EventMapper;
import com.tansoflow.tansocore.model.event.events.EventDto;
import com.tansoflow.tansocore.model.event.events.EventIngestionResult;
import com.tansoflow.tansocore.model.event.events.request.EventRequest;
import com.tansoflow.tansocore.model.event.events.response.EventIngestionResponse;
import com.tansoflow.tansocore.model.event.events.type.EventType;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.service.internal.data.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/client/events")
@PreAuthorize("hasRole('CLIENT')")
@Tag(name = "Client Event", description = "Event ingestion for client applications")
public class EventClientController {
    private final EventService eventService;
    private final EventMapper eventMapper;

    @PostMapping
    @Operation(summary = "Ingest an event", description = "Ingests a single event with idempotency check", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<EventIngestionResponse>> createEvent(
            @AuthenticationPrincipal UserContext userContext,
            @Valid @RequestBody EventRequest eventRequest,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKeyHeader) {

        log.info("Ingesting event for account: {}", userContext.getAccountId());

        String finalIdempotencyKey = eventRequest.getEventIdempotencyKey();
        if (idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank()) {
            finalIdempotencyKey = idempotencyKeyHeader;
        }

        UUID accountId = UUID.fromString(userContext.getAccountId());
        if (finalIdempotencyKey != null && eventService.existsByEventIdempotencyKey(accountId, finalIdempotencyKey)) {
            log.warn("Duplicate event detected with idempotency key: {} for account: {}", finalIdempotencyKey, accountId);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.<EventIngestionResponse>builder()
                            .success(false)
                            .build());
        }

        EventDto eventDto = eventMapper.eventRequestToEventDto(eventRequest);
        eventDto.setEventType(EventType.CLIENT_TRACKED);
        eventDto.setAccountId(accountId);
        if (finalIdempotencyKey != null) {
            eventDto.setEventIdempotencyKey(finalIdempotencyKey);
        }

        if (eventDto.getOccurredAt() == null) {
            eventDto.setOccurredAt(Instant.now());
        }

        EventIngestionResult result = eventService.createEvent(eventDto);

        if (result.isUsageLimitExceeded()) {
            return ResponseEntity.status(HttpStatus.OK)
                    .body(ApiResponse.<EventIngestionResponse>builder()
                            .success(true)
                            .data(EventIngestionResponse.builder()
                                    .usageLimitExceeded(true)
                                    .message(result.getMessage())
                                    .build())
                            .build());
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<EventIngestionResponse>builder()
                        .success(true)
                        .build());
    }
}
