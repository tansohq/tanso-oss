package com.tansoflow.tansocore.controller.tanso.monetization;

import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.model.billing.InvoiceDto;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.service.internal.monetization.InvoiceService;
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

class BillingControllerTest {

    @Mock
    private InvoiceService invoiceService;

    @InjectMocks
    private BillingController billingController;

    private UserContext userContext;
    private final String accountId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userContext = new UserContext(accountId, "test-api-key");
    }

    @Test
    void testGetInvoices_ReturnsInvoices() {
        InvoiceDto invoiceDto = new InvoiceDto();
        invoiceDto.setId(UUID.randomUUID().toString());
        invoiceDto.setStatus("PAID");
        List<InvoiceDto> invoices = Collections.singletonList(invoiceDto);

        when(invoiceService.retrieveInvoicesByAccount(accountId)).thenReturn(invoices);

        ResponseEntity<ApiResponse<List<InvoiceDto>>> response = billingController.getInvoices(userContext, "false");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(invoices, response.getBody().getData());
        verify(invoiceService).retrieveInvoicesByAccount(accountId);
    }

    @Test
    void testGetInvoices_OnlyDue_ReturnsDueInvoices() {
        InvoiceDto invoiceDto = new InvoiceDto();
        invoiceDto.setId(UUID.randomUUID().toString());
        invoiceDto.setStatus("DUE");
        List<InvoiceDto> invoices = Collections.singletonList(invoiceDto);

        when(invoiceService.retrieveOnlyDueInvoicesByAccount(accountId)).thenReturn(invoices);

        ResponseEntity<ApiResponse<List<InvoiceDto>>> response = billingController.getInvoices(userContext, "true");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(invoices, response.getBody().getData());
        verify(invoiceService).retrieveOnlyDueInvoicesByAccount(accountId);
    }

    @Test
    void testGetInvoices_UnsubscribedUserInvoicesStillRetrieved() {
        // This test case demonstrates that the controller retrieves invoices via the service
        // regardless of subscription status, as the service layer/repository doesn't filter by subscription activity.
        InvoiceDto invoiceDto = new InvoiceDto();
        invoiceDto.setId(UUID.randomUUID().toString());
        invoiceDto.setStatus("PAID");
        List<InvoiceDto> invoices = Collections.singletonList(invoiceDto);

        // Even if the user is "unsubscribed", the account ID remains the same and invoices associated with it are fetched.
        when(invoiceService.retrieveInvoicesByAccount(accountId)).thenReturn(invoices);

        ResponseEntity<ApiResponse<List<InvoiceDto>>> response = billingController.getInvoices(userContext, "false");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(1, response.getBody().getData().size());
        verify(invoiceService).retrieveInvoicesByAccount(accountId);
    }
}
