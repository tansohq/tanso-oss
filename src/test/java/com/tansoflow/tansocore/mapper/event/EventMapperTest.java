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
package com.tansoflow.tansocore.mapper.event;

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.Event;
import com.tansoflow.tansocore.model.event.events.EventDto;
import com.tansoflow.tansocore.model.event.events.request.EventRequest;
import com.tansoflow.tansocore.model.event.events.type.EventType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
class EventMapperTest {

    @Autowired
    private EventMapper eventMapper;

    @Test
    void testEventDtoToEventEntity() {
        EventDto dto = new EventDto();
        dto.setId(UUID.randomUUID());
        dto.setEventName("test.event");
        dto.setEventType(EventType.CLIENT_TRACKED);
        dto.setOccurredAt(Instant.now());
        dto.setProperties(Map.of("key", "value"));
        dto.setMeta(Map.of("metaKey", "metaValue"));
        dto.setContext(Map.of("requestId", "req123"));

        Event entity = eventMapper.eventDtoToEventEntity(dto);

        assertNotNull(entity);
        assertEquals(dto.getId(), entity.getId());
        assertEquals(dto.getEventName(), entity.getEventName());
        assertEquals(dto.getEventType(), entity.getEventType());
        assertEquals(dto.getOccurredAt(), entity.getOccurredAt());
        assertEquals(dto.getProperties(), entity.getProperties());
        assertEquals(dto.getMeta(), entity.getMeta());
        assertEquals(dto.getContext(), entity.getContext());
        assertNull(entity.getAccount()); // Account is ignored in mapping
    }

    @Test
    void testEventEntityToEventDto() {
        Account account = new Account();
        account.setId(UUID.randomUUID());

        Event entity = new Event();
        entity.setId(UUID.randomUUID());
        entity.setAccount(account);
        entity.setEventName("test.event");
        entity.setEventType(EventType.ENTITLEMENT_CHECKED);
        entity.setOccurredAt(Instant.now());
        entity.setProperties(Map.of("foo", "bar"));

        EventDto dto = eventMapper.eventEntityToEventDto(entity);

        assertNotNull(dto);
        assertEquals(entity.getId(), dto.getId());
        assertEquals(account.getId(), dto.getAccountId());
        assertEquals(entity.getEventName(), dto.getEventName());
        assertEquals(entity.getEventType(), dto.getEventType());
        assertEquals(entity.getOccurredAt(), dto.getOccurredAt());
        assertEquals(entity.getProperties(), dto.getProperties());
    }

    @Test
    void testEventRequestToEventDto() {
        EventRequest request = new EventRequest();
        request.setEventName("request.event");
        request.setOccurredAt(Instant.now());
        request.setMeta(Map.of("metaKey", "metaValue"));

        EventDto dto = eventMapper.eventRequestToEventDto(request);

        assertNotNull(dto);
        assertEquals(request.getEventName(), dto.getEventName());
        assertEquals(request.getOccurredAt(), dto.getOccurredAt());
        assertEquals(request.getMeta(), dto.getMeta());
        assertNull(dto.getId());
        assertNull(dto.getProperties());
    }
}
