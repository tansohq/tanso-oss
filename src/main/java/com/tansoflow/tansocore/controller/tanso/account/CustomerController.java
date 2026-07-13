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
package com.tansoflow.tansocore.controller.tanso.account;

import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.mapper.account.CustomerMapper;
import com.tansoflow.tansocore.model.customer.CustomerDto;
import com.tansoflow.tansocore.model.customer.request.CustomerBulkRequest;
import com.tansoflow.tansocore.model.customer.request.CustomerRequest;
import com.tansoflow.tansocore.model.customer.response.CustomerBulkResponse;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.service.internal.account.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/monetization/customers")
@PreAuthorize("hasRole('TANSO_UI')")
@Tag(name = "Customer", description = "Customer management operations")
public class CustomerController {
    private final CustomerService customerService;
    private final CustomerMapper customerMapper;

    @PostMapping
    @Operation(summary = "Create customer", description = "Creates a new customer under the authenticated account", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Successfully created a new customer"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> postCustomer(@AuthenticationPrincipal UserContext userContext, @Valid @RequestBody CustomerRequest customerRequest) {
        customerService.createCustomer(userContext.getAccountId(), customerRequest);

        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder().success(true).build();

        return ResponseEntity.status(201).body(apiResponse);
    }

    @GetMapping("/{customerId}")
    @Operation(summary = "Get customer", description = "Retrieves a specific customer by ID belonging to the authenticated account", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved customer information"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Customer not found", content = @Content)
    })
    public ResponseEntity<ApiResponse<CustomerDto>> getCustomer(@AuthenticationPrincipal UserContext userContext, @PathVariable("customerId") String customerId) {
        Customer customer = customerService.validateAndRetrieveCustomer(customerId, userContext.getAccountId());
        CustomerDto customerDto = customerMapper.customerEntityToCustomerDto(customer);

        ApiResponse<CustomerDto> apiResponse = ApiResponse.<CustomerDto>builder()
                .success(true)
                .data(customerDto)
                .build();

        return ResponseEntity.ok(apiResponse);
    }

    @GetMapping
    @Operation(summary = "List customers", description = "Retrieves all customers belonging to the authenticated account", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved customers"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content)
    })
    public ResponseEntity<ApiResponse<CustomerBulkResponse>> getCustomers(@AuthenticationPrincipal UserContext userContext) {

        List<Customer> customers = customerService.retrieveCustomersByAccountId(userContext.getAccountId());
        CustomerBulkResponse bulkResponse = customerMapper.customerEntityListToCustomerBulkResponse(customers);

        ApiResponse<CustomerBulkResponse> apiResponse = ApiResponse.<CustomerBulkResponse>builder()
                .success(true)
                .data(bulkResponse)
                .build();

        return ResponseEntity.ok(apiResponse);
    }

    @PatchMapping("/{customerId}")
    @Operation(summary = "Update customer", description = "Modifies an existing customer record", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully modified record"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Customer not found", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> updateCustomer(@AuthenticationPrincipal UserContext userContext, @Valid @RequestBody CustomerBulkRequest.CustomerRequestElement customerRequest, @PathVariable("customerId") String customerId) {
        Customer customer = customerService.validateAndRetrieveCustomer(customerId, userContext.getAccountId());
        customerService.updateCustomer(customer, customerMapper.customerRequestElementToCustomerDto(customerRequest));

        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder().success(true).build();

        return ResponseEntity.status(HttpStatus.OK).body(apiResponse);
    }
}
