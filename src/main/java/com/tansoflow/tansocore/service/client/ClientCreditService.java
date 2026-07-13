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
package com.tansoflow.tansocore.service.client;

import com.tansoflow.tansocore.model.client.ClientCreditGrantDto;
import com.tansoflow.tansocore.model.client.ClientCreditPoolDto;
import com.tansoflow.tansocore.model.credit.CreditTransactionDto;

import java.util.List;

public interface ClientCreditService {

    List<ClientCreditPoolDto> getCreditPools(String customerReferenceId, String accountId);

    ClientCreditPoolDto getCreditPool(String customerReferenceId, String poolId, String accountId);

    List<CreditTransactionDto> getPoolTransactions(String customerReferenceId, String poolId, String accountId);

    List<ClientCreditGrantDto> getPoolGrants(String customerReferenceId, String poolId, String accountId);
}
