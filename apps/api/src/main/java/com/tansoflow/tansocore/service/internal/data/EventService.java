package com.tansoflow.tansocore.service.internal.data;

import com.tansoflow.tansocore.model.event.events.EventDto;
import com.tansoflow.tansocore.model.event.events.EventGroupDto;
import com.tansoflow.tansocore.model.event.events.EventIngestionResult;
import com.tansoflow.tansocore.model.event.events.type.EventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EventService {

    EventIngestionResult createEvent(EventDto event);

    Page<EventDto> getEvents(UUID accountId, Instant start, Instant end, String customerReferenceId, UUID planId, UUID featureId, EventType eventType, String model, String modelProvider, String eventName, Pageable pageable);

    List<EventGroupDto> getGroupedEvents(UUID accountId, String groupBy, Instant start, Instant end, String customerReferenceId, UUID planId, UUID featureId, EventType eventType, String model, String modelProvider, String eventName);

    Page<EventDto> getEventsByAccountId(UUID accountId, Pageable pageable);

    boolean existsByEventIdempotencyKey(UUID accountId, String eventIdempotencyKey);
}
