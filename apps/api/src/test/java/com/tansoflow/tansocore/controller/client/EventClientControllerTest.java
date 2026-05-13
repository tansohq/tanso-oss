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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventClientControllerTest {

    @Mock
    private EventService eventService;

    @Mock
    private EventMapper eventMapper;

    @InjectMocks
    private EventClientController eventClientController;

    private UserContext userContext;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        accountId = UUID.randomUUID();
        userContext = new UserContext(accountId.toString(), "test-api-key");
    }

    @Test
    void testCreateEvent_Success() {
        EventRequest eventRequest = new EventRequest();
        eventRequest.setEventName("test.event");
        eventRequest.setOccurredAt(java.time.Instant.now());
        eventRequest.setEventIdempotencyKey("test-key");
        EventDto eventDto = new EventDto();
        eventDto.setEventName("test.event");
        eventDto.setEventType(EventType.CLIENT_TRACKED);

        when(eventMapper.eventRequestToEventDto(eventRequest)).thenReturn(eventDto);
        when(eventService.createEvent(any(EventDto.class))).thenReturn(EventIngestionResult.builder().build());

        ResponseEntity<ApiResponse<EventIngestionResponse>> response = eventClientController.createEvent(userContext, eventRequest, null);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        Assertions.assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(accountId, eventDto.getAccountId());
        verify(eventService).createEvent(eventDto);
    }

    @Test
    void testCreateEvent_Idempotency_Header() {
        EventRequest eventRequest = new EventRequest();
        eventRequest.setEventName("test.event");
        eventRequest.setOccurredAt(java.time.Instant.now());
        eventRequest.setEventIdempotencyKey("temp-key");
        EventDto eventDto = new EventDto();
        String key = "header-key";

        when(eventMapper.eventRequestToEventDto(eventRequest)).thenReturn(eventDto);
        when(eventService.existsByEventIdempotencyKey(accountId, key)).thenReturn(false);
        when(eventService.createEvent(any(EventDto.class))).thenReturn(EventIngestionResult.builder().build());

        ResponseEntity<ApiResponse<EventIngestionResponse>> response = eventClientController.createEvent(userContext, eventRequest, key);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(key, eventDto.getEventIdempotencyKey());
        verify(eventService).existsByEventIdempotencyKey(accountId, key);
        verify(eventService).createEvent(eventDto);
    }

    @Test
    void testCreateEvent_Idempotency_Payload() {
        String key = "payload-key";
        EventRequest eventRequest = new EventRequest();
        eventRequest.setEventIdempotencyKey(key);
        EventDto eventDto = new EventDto();
        eventDto.setEventIdempotencyKey(key);

        when(eventMapper.eventRequestToEventDto(eventRequest)).thenReturn(eventDto);
        when(eventService.existsByEventIdempotencyKey(accountId, key)).thenReturn(false);
        when(eventService.createEvent(any(EventDto.class))).thenReturn(EventIngestionResult.builder().build());

        ResponseEntity<ApiResponse<EventIngestionResponse>> response = eventClientController.createEvent(userContext, eventRequest, null);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(eventService).existsByEventIdempotencyKey(accountId, key);
        verify(eventService).createEvent(eventDto);
    }

    @Test
    void testCreateEvent_Duplicate_Conflict() {
        String key = "duplicate-key";
        EventRequest eventRequest = new EventRequest();
        eventRequest.setEventIdempotencyKey(key);

        when(eventService.existsByEventIdempotencyKey(accountId, key)).thenReturn(true);

        ResponseEntity<ApiResponse<EventIngestionResponse>> response = eventClientController.createEvent(userContext, eventRequest, null);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        Assertions.assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        verify(eventService, never()).createEvent(any());
    }
}
