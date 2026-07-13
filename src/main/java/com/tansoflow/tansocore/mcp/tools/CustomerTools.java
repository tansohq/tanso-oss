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
package com.tansoflow.tansocore.mcp.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.mapper.account.CustomerMapper;
import com.tansoflow.tansocore.model.customer.CustomerDto;
import com.tansoflow.tansocore.model.customer.response.CustomerClientResponse;
import com.tansoflow.tansocore.model.customer.request.CustomerRequest;
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.service.internal.account.CustomerService;
import com.tansoflow.tansocore.service.internal.monetization.CreditService;
import com.tansoflow.tansocore.service.internal.monetization.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mcp.enabled", havingValue = "true")
public class CustomerTools {

    private final CustomerService customerService;
    private final CustomerMapper customerMapper;
    private final SubscriptionService subscriptionService;
    private final CreditService creditService;
    private final ObjectMapper objectMapper;

    @Tool(description = "Get customer details by their external reference ID. "
            + "Returns the customer's name, email, active subscriptions, and credit pools.")
    public String getCustomer(
            @ToolParam(description = "The customer's external reference ID (your system's user/customer ID)") String customerReferenceId) {
        try {
            String accountId = getAccountId();
            Customer customer = customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(
                    customerReferenceId, accountId);
            CustomerDto customerDto = customerMapper.customerEntityToCustomerDto(customer);
            CustomerClientResponse response = customerMapper.customerDtoToCustomerClientResponse(customerDto);

            var subscriptions = subscriptionService.getSubscriptionsByCustomer(
                    customer.getId().toString(), accountId);
            response.setSubscriptions(subscriptions);

            var creditPools = creditService.getCreditPoolsByCustomer(
                    customer.getId().toString(), accountId);
            if (creditPools != null && !creditPools.isEmpty()) {
                response.setCreditPools(creditPools);
            }

            return objectMapper.writeValueAsString(response);
        } catch (ResourceNotFoundException e) {
            return "{\"error\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize customer\"}";
        }
    }

    @Tool(description = "CREATES a new customer in your billing system. "
            + "SIDE EFFECT: This permanently creates a customer record. "
            + "Requires at minimum an email address. Returns the created customer details.")
    public String createCustomer(
            @ToolParam(description = "A unique reference ID for this customer in your system, e.g. 'user_abc123'") String customerReferenceId,
            @ToolParam(description = "Customer's email address (required)") String email,
            @ToolParam(description = "Customer's first name", required = false) String firstName,
            @ToolParam(description = "Customer's last name", required = false) String lastName) {
        try {
            String accountId = getAccountId();
            CustomerRequest request = new CustomerRequest();
            request.setCustomerReferenceId(customerReferenceId);
            request.setEmail(email);
            request.setFirstName(firstName != null ? firstName : "");
            request.setLastName(lastName != null ? lastName : "");

            Customer customer = customerService.createCustomer(accountId, request);
            CustomerDto customerDto = customerMapper.customerEntityToCustomerDto(customer);
            CustomerClientResponse response = customerMapper.customerDtoToCustomerClientResponse(customerDto);

            return objectMapper.writeValueAsString(response);
        } catch (IllegalArgumentException e) {
            return "{\"error\": \"invalid_request\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize customer\"}";
        }
    }

    @Tool(description = "UPDATES an existing customer's details. "
            + "SIDE EFFECT: Modifies the customer record. Only provided fields are updated; "
            + "omitted fields remain unchanged.")
    public String updateCustomer(
            @ToolParam(description = "The customer's external reference ID") String customerReferenceId,
            @ToolParam(description = "New first name", required = false) String firstName,
            @ToolParam(description = "New last name", required = false) String lastName,
            @ToolParam(description = "New email address", required = false) String email,
            @ToolParam(description = "New phone number", required = false) String phoneNumber) {
        try {
            String accountId = getAccountId();
            Customer customer = customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(
                    customerReferenceId, accountId);

            if (firstName != null) customer.setFirstName(firstName);
            if (lastName != null) customer.setLastName(lastName);
            if (email != null) customer.setEmail(email);
            if (phoneNumber != null) customer.setPhoneNumber(phoneNumber);

            CustomerDto customerDto = customerMapper.customerEntityToCustomerDto(customer);
            customerService.updateCustomer(customer, customerDto);

            CustomerClientResponse response = customerMapper.customerDtoToCustomerClientResponse(customerDto);

            var subscriptions = subscriptionService.getSubscriptionsByCustomer(
                    customer.getId().toString(), accountId);
            response.setSubscriptions(subscriptions);

            var creditPools = creditService.getCreditPoolsByCustomer(
                    customer.getId().toString(), accountId);
            if (creditPools != null && !creditPools.isEmpty()) {
                response.setCreditPools(creditPools);
            }

            return objectMapper.writeValueAsString(response);
        } catch (ResourceNotFoundException e) {
            return "{\"error\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize customer\"}";
        }
    }

    private String getAccountId() {
        UserContext ctx = (UserContext) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return ctx.getAccountId();
    }
}
