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
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.mapper.account.CustomerMapper;
import com.tansoflow.tansocore.model.customer.CustomerDto;
import com.tansoflow.tansocore.model.customer.request.CustomerRequest;
import com.tansoflow.tansocore.model.customer.request.CustomerUpdateRequest;
import com.tansoflow.tansocore.model.customer.response.CustomerClientResponse;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.model.subscription.SubscriptionDto;
import com.tansoflow.tansocore.model.credit.CreditPoolDto;
import com.tansoflow.tansocore.service.internal.account.CustomerService;
import com.tansoflow.tansocore.service.internal.monetization.CreditService;
import com.tansoflow.tansocore.service.internal.monetization.SubscriptionService;
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
@RequestMapping("/api/v1/client/customers")
@PreAuthorize("hasRole('CLIENT')")
@Tag(name = "Client Customer", description = "Customer management for client applications")
public class CustomerClientController {
    private final CustomerService customerService;
    private final CustomerMapper customerMapper;
    private final SubscriptionService subscriptionService;
    private final CreditService creditService;

    @GetMapping("/{externalClientCustomerId}")
    @Operation(summary = "Retrieve a customer", description = "Retrieves customer details and subscriptions by external client customer ID", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved customer details"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Customer not found", content = @Content)
    })
    public ResponseEntity<ApiResponse<CustomerClientResponse>> getCustomer(@AuthenticationPrincipal UserContext userContext,
                                                   @PathVariable("externalClientCustomerId") String externalClientCustomerId) {
        Customer customer = customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(externalClientCustomerId, userContext.getAccountId());
        CustomerDto customerDto = customerMapper.customerEntityToCustomerDto(customer);
        CustomerClientResponse customerClientResponse = customerMapper.customerDtoToCustomerClientResponse(customerDto);

        List<SubscriptionDto> subscriptionDtos = subscriptionService.getSubscriptionsByCustomer(customer.getId().toString(), userContext.getAccountId());
        customerClientResponse.setSubscriptions(subscriptionDtos);

        List<CreditPoolDto> creditPools = creditService.getCreditPoolsByCustomer(customer.getId().toString(), userContext.getAccountId());
        if (creditPools != null && !creditPools.isEmpty()) {
            customerClientResponse.setCreditPools(creditPools);
        }

        ApiResponse<CustomerClientResponse> apiResponse = ApiResponse.<CustomerClientResponse>builder()
                .success(true)
                .data(customerClientResponse)
                .build();


        return ResponseEntity.ok(apiResponse);
    }

    @PostMapping()
    @Operation(summary = "Create a customer", description = "Creates a new customer under the current account", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Successfully created a new customer"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content)
    })
    public ResponseEntity<ApiResponse<CustomerClientResponse>> postCustomer(@AuthenticationPrincipal UserContext userContext, @Valid @RequestBody CustomerRequest customerRequest) {
        Customer customer = customerService.createCustomer(userContext.getAccountId(), customerRequest);
        CustomerDto customerDto = customerMapper.customerEntityToCustomerDto(customer);
        CustomerClientResponse customerClientResponse = customerMapper.customerDtoToCustomerClientResponse(customerDto);

        ApiResponse<CustomerClientResponse> apiResponse = ApiResponse.<CustomerClientResponse>builder()
                .success(true)
                .data(customerClientResponse)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(apiResponse);
    }

    @PatchMapping("/{externalClientCustomerId}")
    @Operation(summary = "Update a customer", description = "Partially updates a customer's details. Only provided fields are updated.", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully updated the customer"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Customer not found", content = @Content)
    })
    public ResponseEntity<ApiResponse<CustomerClientResponse>> patchCustomer(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable("externalClientCustomerId") String externalClientCustomerId,
            @Valid @RequestBody CustomerUpdateRequest updateRequest) {
        Customer customer = customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(externalClientCustomerId, userContext.getAccountId());

        if (updateRequest.getFirstName() != null) {
            customer.setFirstName(updateRequest.getFirstName());
        }
        if (updateRequest.getLastName() != null) {
            customer.setLastName(updateRequest.getLastName());
        }
        if (updateRequest.getEmail() != null) {
            customer.setEmail(updateRequest.getEmail());
        }
        if (updateRequest.getPhoneNumber() != null) {
            customer.setPhoneNumber(updateRequest.getPhoneNumber());
        }

        CustomerDto customerDto = customerMapper.customerEntityToCustomerDto(customer);
        customerService.updateCustomer(customer, customerDto);

        CustomerClientResponse customerClientResponse = customerMapper.customerDtoToCustomerClientResponse(customerDto);

        List<SubscriptionDto> subscriptionDtos = subscriptionService.getSubscriptionsByCustomer(customer.getId().toString(), userContext.getAccountId());
        customerClientResponse.setSubscriptions(subscriptionDtos);

        List<CreditPoolDto> creditPools = creditService.getCreditPoolsByCustomer(customer.getId().toString(), userContext.getAccountId());
        if (creditPools != null && !creditPools.isEmpty()) {
            customerClientResponse.setCreditPools(creditPools);
        }

        ApiResponse<CustomerClientResponse> apiResponse = ApiResponse.<CustomerClientResponse>builder()
                .success(true)
                .data(customerClientResponse)
                .build();

        return ResponseEntity.ok(apiResponse);
    }

}
