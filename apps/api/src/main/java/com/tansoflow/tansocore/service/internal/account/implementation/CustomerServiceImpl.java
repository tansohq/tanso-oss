package com.tansoflow.tansocore.service.internal.account.implementation;

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.mapper.account.CustomerMapper;
import com.tansoflow.tansocore.model.customer.CustomerDto;
import com.tansoflow.tansocore.model.customer.CustomerSource;
import com.tansoflow.tansocore.model.customer.request.CustomerBulkRequest;
import com.tansoflow.tansocore.model.customer.request.CustomerRequest;
import com.tansoflow.tansocore.model.event.service.CustomerCreatedEvent;
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.repository.CustomerRepository;
import com.tansoflow.tansocore.service.internal.account.AccountService;
import com.tansoflow.tansocore.service.internal.account.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {
    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;
    private final AccountService accountService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public List<CustomerDto> createCustomers(String accountId, CustomerBulkRequest customerBulkRequest) {
        List<CustomerDto> customerDtoList = customerMapper.customerRequestListToCustomerDtoList(customerBulkRequest);
        List<CustomerDto> createdCustomerList = new ArrayList<>();
        for (CustomerDto customerDto : customerDtoList) {
            Account account = accountService.retrieveAccount(accountId);
            Customer createdCustomer = createCustomer(account, customerDto);
            CustomerDto createdCustomerDto = customerMapper
                    .customerEntityToCustomerDto(createdCustomer);
            createdCustomerList.add(createdCustomerDto);
        }

        return createdCustomerList;
    }

    @Override
    @Transactional
    public Customer createCustomer(String accountId, CustomerRequest customerRequest) {
        Account account = accountService.retrieveAccount(accountId);
        return createCustomer(account, customerMapper.customerRequestToCustomerDto(customerRequest));
    }

    @Override
    @Transactional
    public Customer createCustomer(Account account, CustomerDto customerDto) {
        try {
            Customer customer = customerMapper.customerDtoToCustomerEntity(customerDto);
            customer.setAccount(account);
            if (customer.getSource() == null) {
                customer.setSource(CustomerSource.MANUAL);
            }
            log.info("Created customer for accountId={} and referenceId={}", account.getId().toString(), customerDto.getCustomerReferenceId());
            Customer customerCreated = customerRepository.save(customer);
            eventPublisher.publishEvent(new CustomerCreatedEvent(account.getId(), customerCreated.getId()));
            return customerCreated;
        } catch (RuntimeException runtimeException) {
            log.error("Failed to create customer for accountId={} and referenceId={}", account.getId().toString(), customerDto.getCustomerReferenceId(), runtimeException);
            throw runtimeException;
        }
    }

    @Override
    public Customer retrieveCustomer(UUID uuid) {
        return customerRepository.getCustomerById(uuid).orElseThrow(() -> {
            log.warn("Customer not found for uuid={}", uuid);
            return new ResourceNotFoundException("Customer not found");
        });
    }

    @Override
    public Customer retrieveCustomerByExternalClientCustomerIdAndAccount(String referenceId, String accountId) {
        return customerRepository.getCustomerByReferenceIdAndAccountId(referenceId, UUID.fromString(accountId)).orElseThrow(() -> {
            log.warn("Customer not found for referenceId={} and accountId={}", referenceId, accountId);
            return new ResourceNotFoundException("Customer not found");
        });
    }

    @Override
    public Customer validateAndRetrieveCustomer(String customerUuid, String accountId) {
        Customer customer = retrieveCustomer(UUID.fromString(customerUuid));

        if (customer == null) {
            throw new IllegalArgumentException("Customer not found");
        }

        if (!customer.getAccount().getId().toString().equals(accountId)) {
            log.warn("CustomerId: {} does not belong to accountId: {}. Customer cannot be retrieved.", customerUuid, accountId);
            throw new IllegalArgumentException("Customer does not belong to the account");
        }

        return customer;
    }

    @Override
    public List<Customer> retrieveCustomersByAccountId(String accountId) {
        Account account = accountService.retrieveAccount(accountId);
        return customerRepository.getCustomersByAccount(account);
    }

    @Override
    public void updateCustomer(Customer customer, CustomerDto customerDto) {
        try {
            customerMapper.updateCustomerEntity(customerDto, customer);
            customerRepository.save(customer);

        } catch (RuntimeException runtimeException) {
            log.error("Failed to update customer for customerId={}", customer.getId(), runtimeException);
            throw runtimeException;
        }
    }

}
