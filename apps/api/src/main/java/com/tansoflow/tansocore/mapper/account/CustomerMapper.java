package com.tansoflow.tansocore.mapper.account;

import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.model.customer.CustomerDto;
import com.tansoflow.tansocore.model.customer.request.CustomerBulkRequest;
import com.tansoflow.tansocore.model.customer.request.CustomerRequest;
import com.tansoflow.tansocore.model.customer.response.CustomerBulkResponse;
import com.tansoflow.tansocore.model.customer.response.CustomerClientResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.ArrayList;
import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface CustomerMapper {
    @Mapping(target = "externalClientCustomerId", source = "customerReferenceId")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "firstName")
    @Mapping(target = "lastName")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "phoneNumber", ignore = true)
    @Mapping(target = "address", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "modifiedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "archivedAt", ignore = true)
    @Mapping(target = "account", ignore = true)
    @Mapping(target = "source", ignore = true)
    Customer customerDtoToCustomerEntity(CustomerDto customerDto);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "customerReferenceId", source = "externalClientCustomerId")
    @Mapping(target = "firstName", source = "firstName")
    @Mapping(target = "lastName", source = "lastName")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "phoneNumber", source = "phoneNumber")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "modifiedAt", source = "modifiedAt")
    CustomerDto customerEntityToCustomerDto(Customer customer);

    @Mapping(target = "externalClientCustomerId", source = "customerReferenceId")
    @Mapping(target = "firstName", source = "firstName")
    @Mapping(target = "lastName", source = "lastName")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "modifiedAt", ignore = true)
    @Mapping(target = "account", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "archivedAt", ignore = true)
    @Mapping(target = "address", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "phoneNumber", ignore = true)
    @Mapping(target = "source", ignore = true)
    void updateCustomerEntity(CustomerDto customerDto, @MappingTarget Customer customer);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "customerReferenceId", source = "referenceId")
    @Mapping(target = "firstName", source = "firstName")
    @Mapping(target = "lastName", source = "lastName")
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "modifiedAt", ignore = true)
    CustomerDto customerRequestElementToCustomerDto(CustomerBulkRequest.CustomerRequestElement customerRequestElement);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "customerReferenceId", source = "customerReferenceId")
    @Mapping(target = "firstName", source = "firstName")
    @Mapping(target = "lastName", source = "lastName")
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "modifiedAt", ignore = true)
    CustomerDto customerRequestToCustomerDto(CustomerRequest customerRequest);

    @Mapping(target = "subscriptions", ignore = true)
    @Mapping(target = "creditPools", ignore = true)
    CustomerClientResponse customerDtoToCustomerClientResponse(CustomerDto customerDto);

    default List<CustomerDto> customerRequestListToCustomerDtoList(CustomerBulkRequest customerBulkRequest) {
        if (customerBulkRequest.getCustomers().isEmpty()) {
            return new ArrayList<>();
        }

        List<CustomerDto> result = new ArrayList<>();
        for (CustomerBulkRequest.CustomerRequestElement customerRequestElement : customerBulkRequest.getCustomers()) {
            CustomerDto customerDto = customerRequestElementToCustomerDto(customerRequestElement);
            result.add(customerDto);
        }

        return result;
    }

    default CustomerBulkResponse customerEntityListToCustomerBulkResponse(List<Customer> customerEntities) {
        if (customerEntities.isEmpty()) {
            return new CustomerBulkResponse();
        }

        List<CustomerBulkResponse.CustomerElement> customerElements = new ArrayList<>();
        for (Customer customer : customerEntities) {
            CustomerBulkResponse.CustomerElement customerElement = new CustomerBulkResponse.CustomerElement();
            customerElement.setId(customer.getId().toString());
            customerElement.setReferenceId(customer.getExternalClientCustomerId());
            customerElement.setFirstName(customer.getFirstName());
            customerElement.setLastName(customer.getLastName());
            customerElement.setEmail(customer.getEmail());
            customerElement.setCreatedAt(customer.getCreatedAt() != null ? customer.getCreatedAt().toString() : null);

            customerElements.add(customerElement);
        }
        CustomerBulkResponse customerBulkResponse = new CustomerBulkResponse();
        customerBulkResponse.setCustomers(customerElements);
        return customerBulkResponse;
    }
}