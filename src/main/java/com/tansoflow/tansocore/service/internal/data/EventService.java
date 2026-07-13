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
