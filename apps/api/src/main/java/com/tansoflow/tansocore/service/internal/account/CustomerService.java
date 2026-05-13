package com.tansoflow.tansocore.service.internal.account;

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.model.customer.CustomerDto;
import com.tansoflow.tansocore.model.customer.request.CustomerBulkRequest;
import com.tansoflow.tansocore.model.customer.request.CustomerRequest;

import java.util.List;
import java.util.UUID;

public interface CustomerService {

    List<CustomerDto> createCustomers(String accountId, CustomerBulkRequest customerBulkRequest);

    Customer createCustomer(String accountId, CustomerRequest customerRequest);

    Customer createCustomer(Account account, CustomerDto customerDto);

    Customer retrieveCustomer(UUID uuid);

    Customer retrieveCustomerByExternalClientCustomerIdAndAccount(String referenceId, String accountId);

    Customer validateAndRetrieveCustomer(String customerUuid, String accountId);

    List<Customer> retrieveCustomersByAccountId(String accountId);

    void updateCustomer(Customer customer, CustomerDto customerDto);

}
