package com.tansoflow.tansocore.controller.tanso.monetization;

import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.model.subscription.SubscriptionDto;
import com.tansoflow.tansocore.model.subscription.SubscriptionScheduledChangeDto;
import com.tansoflow.tansocore.model.subscription.request.SubscriptionRequest;
import com.tansoflow.tansocore.model.subscription.response.SubscribedCustomerResponse;
import com.tansoflow.tansocore.service.internal.monetization.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubscriptionControllerTest {

    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private SubscriptionController subscriptionController;

    private UserContext userContext;
    private final String accountId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userContext = new UserContext(accountId, "test-api-key");
    }

    @Test
    void testCreateSubscription() {
        SubscriptionRequest request = new SubscriptionRequest();
        request.setPlanId(UUID.randomUUID().toString());
        request.setCustomerId(UUID.randomUUID().toString());

        SubscribedCustomerResponse mockResponse = new SubscribedCustomerResponse();
        when(subscriptionService.subscribeCustomer(request, accountId)).thenReturn(mockResponse);

        ResponseEntity<ApiResponse<SubscribedCustomerResponse>> response = subscriptionController.createSubscription(userContext, request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertNotNull(response.getBody().getData());
        verify(subscriptionService).subscribeCustomer(request, accountId);
    }

    @Test
    void testCancelScheduledChanges() {
        UUID subscriptionUuid = UUID.randomUUID();
        
        ResponseEntity<ApiResponse<Void>> response = subscriptionController.cancelScheduledChanges(userContext, subscriptionUuid.toString());
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        verify(subscriptionService).cancelScheduledChangesForSubscription(subscriptionUuid, UUID.fromString(accountId));
    }

    @Test
    void testCancelScheduledCancellation() {
        String subscriptionUuid = UUID.randomUUID().toString();
        
        ResponseEntity<ApiResponse<Void>> response = subscriptionController.cancelScheduledCancellation(userContext, subscriptionUuid);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        verify(subscriptionService).cancelScheduledSubscriptionCancellation(subscriptionUuid, accountId);
    }

    @Test
    void testGetScheduledChanges() {
        SubscriptionScheduledChangeDto dto = new SubscriptionScheduledChangeDto();
        when(subscriptionService.getScheduledChangesByAccount(accountId)).thenReturn(Collections.singletonList(dto));
        
        ResponseEntity<ApiResponse<List<SubscriptionScheduledChangeDto>>> response = subscriptionController.getScheduledChanges(userContext);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals(1, response.getBody().getData().size());
        verify(subscriptionService).getScheduledChangesByAccount(accountId);
    }

    @Test
    void testGetScheduledCancellations() {
        SubscriptionDto dto = new SubscriptionDto();
        when(subscriptionService.getScheduledCancellationsByAccount(accountId)).thenReturn(Collections.singletonList(dto));
        
        ResponseEntity<ApiResponse<List<SubscriptionDto>>> response = subscriptionController.getScheduledCancellations(userContext);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals(1, response.getBody().getData().size());
        verify(subscriptionService).getScheduledCancellationsByAccount(accountId);
    }
}
