package com.tansoflow.tansocore.controller.client;

import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.mapper.account.CustomerMapper;
import com.tansoflow.tansocore.model.customer.CustomerDto;
import com.tansoflow.tansocore.model.customer.request.CustomerRequest;
import com.tansoflow.tansocore.model.customer.request.CustomerUpdateRequest;
import com.tansoflow.tansocore.model.customer.response.CustomerClientResponse;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.service.internal.account.CustomerService;
import com.tansoflow.tansocore.service.internal.monetization.CreditService;
import com.tansoflow.tansocore.service.internal.monetization.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CustomerClientController.
 * The key behavior under test is that postCustomer now returns ApiResponse<CustomerClientResponse>
 * with the newly created customer's data (HTTP 201), rather than the previous ApiResponse<Void>.
 * This covers the interface + implementation change to CustomerService.createCustomer() and the
 * corresponding controller update.
 */
class CustomerClientControllerTest {

    @Mock
    private CustomerService customerService;

    @Mock
    private CustomerMapper customerMapper;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private CreditService creditService;

    @InjectMocks
    private CustomerClientController customerClientController;

    private UserContext userContext;
    private final String accountId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userContext = new UserContext(accountId, "test-api-key");
    }

    // ─── postCustomer ────────────────────────────────────────────────────────────

    @Test
    void postCustomer_shouldReturn201WithCreatedCustomerData() {
        // Arrange
        CustomerRequest request = buildCustomerRequest("cust_001", "Alice", "Smith", "alice@example.com");

        Customer savedCustomer = buildCustomerEntity(UUID.randomUUID(), "cust_001", "Alice", "Smith", "alice@example.com");

        CustomerDto customerDto = buildCustomerDto("cust_001", "Alice", "Smith", "alice@example.com");

        CustomerClientResponse expectedResponse = buildCustomerClientResponse("cust_001", "Alice", "Smith", "alice@example.com");

        when(customerService.createCustomer(eq(accountId), eq(request))).thenReturn(savedCustomer);
        when(customerMapper.customerEntityToCustomerDto(savedCustomer)).thenReturn(customerDto);
        when(customerMapper.customerDtoToCustomerClientResponse(customerDto)).thenReturn(expectedResponse);

        // Act
        ResponseEntity<ApiResponse<CustomerClientResponse>> response =
                customerClientController.postCustomer(userContext, request);

        // Assert — HTTP status must be 201 Created
        assertEquals(HttpStatus.CREATED, response.getStatusCode());

        // Assert — response body is populated and successful
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());

        // Assert — the created customer's data is returned in the response body (not Void)
        CustomerClientResponse data = response.getBody().getData();
        assertNotNull(data, "Response data must not be null — createCustomer should return customer data");
        assertEquals("cust_001", data.getCustomerReferenceId());
        assertEquals("Alice", data.getFirstName());
        assertEquals("Smith", data.getLastName());
        assertEquals("alice@example.com", data.getEmail());
    }

    @Test
    void postCustomer_shouldDelegateToCustomerServiceWithCorrectAccountIdAndRequest() {
        // Arrange
        CustomerRequest request = buildCustomerRequest("cust_002", "Bob", "Jones", "bob@example.com");
        Customer savedCustomer = buildCustomerEntity(UUID.randomUUID(), "cust_002", "Bob", "Jones", "bob@example.com");
        CustomerDto customerDto = buildCustomerDto("cust_002", "Bob", "Jones", "bob@example.com");
        CustomerClientResponse clientResponse = buildCustomerClientResponse("cust_002", "Bob", "Jones", "bob@example.com");

        when(customerService.createCustomer(anyString(), any(CustomerRequest.class))).thenReturn(savedCustomer);
        when(customerMapper.customerEntityToCustomerDto(savedCustomer)).thenReturn(customerDto);
        when(customerMapper.customerDtoToCustomerClientResponse(customerDto)).thenReturn(clientResponse);

        // Act
        customerClientController.postCustomer(userContext, request);

        // Assert — service is called with the authenticated account's ID from UserContext
        verify(customerService).createCustomer(eq(accountId), eq(request));
    }

    @Test
    void postCustomer_returnedCustomerData_doesNotIncludeSubscriptionsOnCreate() {
        // Arrange — postCustomer does NOT fetch subscriptions (unlike GET); the response
        // data field should hold the customer DTO projection without a subscriptions list.
        CustomerRequest request = buildCustomerRequest("cust_003", "Carol", "White", "carol@example.com");
        Customer savedCustomer = buildCustomerEntity(UUID.randomUUID(), "cust_003", "Carol", "White", "carol@example.com");
        CustomerDto customerDto = buildCustomerDto("cust_003", "Carol", "White", "carol@example.com");
        CustomerClientResponse clientResponse = buildCustomerClientResponse("cust_003", "Carol", "White", "carol@example.com");
        // subscriptions field is intentionally left null by the mapper stub (mirrors real behavior)

        when(customerService.createCustomer(eq(accountId), eq(request))).thenReturn(savedCustomer);
        when(customerMapper.customerEntityToCustomerDto(savedCustomer)).thenReturn(customerDto);
        when(customerMapper.customerDtoToCustomerClientResponse(customerDto)).thenReturn(clientResponse);

        // Act
        ResponseEntity<ApiResponse<CustomerClientResponse>> response =
                customerClientController.postCustomer(userContext, request);

        // Assert — data is present but no subscriptions fetched on creation
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertNull(response.getBody().getData().getSubscriptions(),
                "POST /customers must not populate subscriptions — use GET to retrieve them");
    }

    // ─── getCustomer ─────────────────────────────────────────────────────────────

    @Test
    void getCustomer_shouldReturn200WithCustomerAndSubscriptions() {
        // Arrange
        String externalId = "cust_100";
        Customer customer = buildCustomerEntity(UUID.randomUUID(), externalId, "Dave", "Brown", "dave@example.com");
        CustomerDto customerDto = buildCustomerDto(externalId, "Dave", "Brown", "dave@example.com");
        CustomerClientResponse clientResponse = buildCustomerClientResponse(externalId, "Dave", "Brown", "dave@example.com");

        when(customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(externalId, accountId))
                .thenReturn(customer);
        when(customerMapper.customerEntityToCustomerDto(customer)).thenReturn(customerDto);
        when(customerMapper.customerDtoToCustomerClientResponse(customerDto)).thenReturn(clientResponse);
        when(subscriptionService.getSubscriptionsByCustomer(customer.getId().toString(), accountId))
                .thenReturn(Collections.emptyList());
        when(creditService.getCreditPoolsByCustomer(customer.getId().toString(), accountId))
                .thenReturn(Collections.emptyList());

        // Act
        ResponseEntity<ApiResponse<CustomerClientResponse>> response =
                customerClientController.getCustomer(userContext, externalId);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(externalId, response.getBody().getData().getCustomerReferenceId());
    }

    // ─── patchCustomer ───────────────────────────────────────────────────────────

    @Test
    void patchCustomer_shouldReturn200WithUpdatedCustomerData() {
        // Arrange
        String externalId = "cust_200";
        Customer customer = buildCustomerEntity(UUID.randomUUID(), externalId, "Eve", "Green", "eve@example.com");
        customer.setFirstName("Eve");
        customer.setLastName("Green");
        customer.setEmail("eve@example.com");

        CustomerUpdateRequest updateRequest = new CustomerUpdateRequest();
        updateRequest.setFirstName("Evelyn");

        CustomerDto customerDto = buildCustomerDto(externalId, "Evelyn", "Green", "eve@example.com");
        CustomerClientResponse clientResponse = buildCustomerClientResponse(externalId, "Evelyn", "Green", "eve@example.com");

        when(customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(externalId, accountId))
                .thenReturn(customer);
        when(customerMapper.customerEntityToCustomerDto(any(Customer.class))).thenReturn(customerDto);
        when(customerMapper.customerDtoToCustomerClientResponse(customerDto)).thenReturn(clientResponse);
        when(subscriptionService.getSubscriptionsByCustomer(customer.getId().toString(), accountId))
                .thenReturn(Collections.emptyList());
        when(creditService.getCreditPoolsByCustomer(customer.getId().toString(), accountId))
                .thenReturn(Collections.emptyList());

        // Act
        ResponseEntity<ApiResponse<CustomerClientResponse>> response =
                customerClientController.patchCustomer(userContext, externalId, updateRequest);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        verify(customerService).updateCustomer(eq(customer), eq(customerDto));
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private CustomerRequest buildCustomerRequest(String externalId, String firstName, String lastName, String email) {
        CustomerRequest req = new CustomerRequest();
        req.setCustomerReferenceId(externalId);
        req.setFirstName(firstName);
        req.setLastName(lastName);
        req.setEmail(email);
        return req;
    }

    private Customer buildCustomerEntity(UUID id, String externalId, String firstName, String lastName, String email) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setExternalClientCustomerId(externalId);
        customer.setFirstName(firstName);
        customer.setLastName(lastName);
        customer.setEmail(email);
        return customer;
    }

    private CustomerDto buildCustomerDto(String externalId, String firstName, String lastName, String email) {
        CustomerDto dto = new CustomerDto();
        dto.setCustomerReferenceId(externalId);
        dto.setFirstName(firstName);
        dto.setLastName(lastName);
        dto.setEmail(email);
        return dto;
    }

    private CustomerClientResponse buildCustomerClientResponse(String externalId, String firstName, String lastName, String email) {
        CustomerClientResponse resp = new CustomerClientResponse();
        resp.setCustomerReferenceId(externalId);
        resp.setFirstName(firstName);
        resp.setLastName(lastName);
        resp.setEmail(email);
        return resp;
    }
}
