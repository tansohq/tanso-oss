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
package com.tansoflow.tansocore.service.client.implementation;

import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.mapper.client.ClientCreditMapper;
import com.tansoflow.tansocore.model.client.ClientCreditGrantDto;
import com.tansoflow.tansocore.model.client.ClientCreditPoolDto;
import com.tansoflow.tansocore.model.credit.CreditGrantDto;
import com.tansoflow.tansocore.model.credit.CreditPoolDto;
import com.tansoflow.tansocore.model.credit.CreditTransactionDto;
import com.tansoflow.tansocore.service.client.ClientCreditService;
import com.tansoflow.tansocore.service.internal.account.CustomerService;
import com.tansoflow.tansocore.service.internal.monetization.CreditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ClientCreditServiceImpl implements ClientCreditService {
    private final CustomerService customerService;
    private final CreditService creditService;
    private final ClientCreditMapper clientCreditMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ClientCreditPoolDto> getCreditPools(String customerReferenceId, String accountId) {
        Customer customer = customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(customerReferenceId, accountId);
        List<CreditPoolDto> pools = creditService.getCreditPoolsByCustomer(customer.getId().toString(), accountId);
        return clientCreditMapper.toClientCreditPoolDtoList(pools);
    }

    @Override
    @Transactional(readOnly = true)
    public ClientCreditPoolDto getCreditPool(String customerReferenceId, String poolId, String accountId) {
        Customer customer = customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(customerReferenceId, accountId);
        CreditPoolDto pool = creditService.getCreditPool(poolId, accountId);
        validatePoolOwnership(pool, customer);
        return clientCreditMapper.toClientCreditPoolDto(pool);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CreditTransactionDto> getPoolTransactions(String customerReferenceId, String poolId, String accountId) {
        Customer customer = customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(customerReferenceId, accountId);
        CreditPoolDto pool = creditService.getCreditPool(poolId, accountId);
        validatePoolOwnership(pool, customer);
        return creditService.getTransactionsByPool(poolId, accountId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClientCreditGrantDto> getPoolGrants(String customerReferenceId, String poolId, String accountId) {
        Customer customer = customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(customerReferenceId, accountId);
        CreditPoolDto pool = creditService.getCreditPool(poolId, accountId);
        validatePoolOwnership(pool, customer);
        List<CreditGrantDto> grants = creditService.getGrantsByPool(poolId, accountId);
        return clientCreditMapper.toClientCreditGrantDtoList(grants);
    }

    private void validatePoolOwnership(CreditPoolDto pool, Customer customer) {
        if (pool.getCustomerId() != null && !pool.getCustomerId().equals(customer.getId().toString())) {
            throw new IllegalArgumentException("Credit pool does not belong to the specified customer");
        }
    }
}
