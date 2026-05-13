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
