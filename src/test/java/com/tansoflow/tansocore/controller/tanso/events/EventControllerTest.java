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
package com.tansoflow.tansocore.controller.tanso.events;

import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.controller.tanso.event.EventController;
import com.tansoflow.tansocore.model.event.events.EventDto;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.model.response.PagedResponse;
import com.tansoflow.tansocore.service.internal.data.EventService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventControllerTest {

    @Mock
    private EventService eventService;

    @InjectMocks
    private EventController eventController;

    private UserContext userContext;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        UUID accountId = UUID.randomUUID();
        userContext = new UserContext(accountId.toString(), "test-api-key");
    }

    @Test
    void testGetEvents_Success() {
        Page<EventDto> eventPage = new PageImpl<>(Collections.singletonList(new EventDto()));
        when(eventService.getEvents(any(UUID.class), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class))).thenReturn(eventPage);

        ResponseEntity<ApiResponse<PagedResponse<EventDto>>> response = eventController.getEvents(userContext, null, null, null, null, null, null, null, null, null, 0, 10);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());

        PagedResponse<EventDto> data = response.getBody().getData();
        assertEquals(eventPage.getContent(), data.getItems());
        assertEquals(eventPage.getTotalElements(), data.getTotalElements());
        assertEquals(eventPage.getTotalPages(), data.getTotalPages());
        assertEquals(eventPage.getNumber(), data.getPage());
        assertEquals(eventPage.getSize(), data.getSize());

        verify(eventService).getEvents(any(UUID.class), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void testGetEvents_WithFilters() {
        Page<EventDto> eventPage = new PageImpl<>(Collections.singletonList(new EventDto()));
        Instant start = Instant.now().minusSeconds(3600);
        Instant end = Instant.now();
        String customerRef = "cust-123";
        UUID planId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();

        when(eventService.getEvents(any(UUID.class), eq(start), eq(end), eq(customerRef), eq(planId), eq(featureId), isNull(), isNull(), isNull(), isNull(), any(Pageable.class))).thenReturn(eventPage);

        ResponseEntity<ApiResponse<PagedResponse<EventDto>>> response = eventController.getEvents(userContext, start, end, customerRef, planId, featureId, null, null, null, null, 0, 10);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(eventService).getEvents(any(UUID.class), eq(start), eq(end), eq(customerRef), eq(planId), eq(featureId), isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
    }
}
