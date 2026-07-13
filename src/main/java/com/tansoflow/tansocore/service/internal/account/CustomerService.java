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
