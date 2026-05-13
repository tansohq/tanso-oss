package com.tansoflow.tansocore.mapper.credit;

import com.tansoflow.tansocore.entity.CreditGrant;
import com.tansoflow.tansocore.entity.CreditModel;
import com.tansoflow.tansocore.entity.CreditPool;
import com.tansoflow.tansocore.entity.CreditTransaction;
import com.tansoflow.tansocore.entity.PlanCreditAllocation;
import com.tansoflow.tansocore.model.credit.CreditGrantDto;
import com.tansoflow.tansocore.model.credit.CreditModelDto;
import com.tansoflow.tansocore.model.credit.CreditPoolDto;
import com.tansoflow.tansocore.model.credit.CreditTransactionDto;
import com.tansoflow.tansocore.model.credit.PlanCreditAllocationDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface CreditMapper {

    @Mapping(target = "customerId", source = "customer.id")
    CreditPoolDto creditPoolToDto(CreditPool creditPool);

    List<CreditPoolDto> creditPoolListToDtoList(List<CreditPool> pools);

    @Mapping(target = "creditPoolId", source = "creditPool.id")
    @Mapping(target = "subscriptionId", source = "subscription.id")
    @Mapping(target = "invoiceId", source = "invoice.id")
    CreditGrantDto creditGrantToDto(CreditGrant grant);

    List<CreditGrantDto> creditGrantListToDtoList(List<CreditGrant> grants);

    @Mapping(target = "creditPoolId", source = "creditPool.id")
    @Mapping(target = "creditGrantId", source = "creditGrant.id")
    CreditTransactionDto creditTransactionToDto(CreditTransaction transaction);

    List<CreditTransactionDto> creditTransactionListToDtoList(List<CreditTransaction> transactions);

    CreditModelDto creditModelToDto(CreditModel creditModel);

    List<CreditModelDto> creditModelListToDtoList(List<CreditModel> creditModels);

    @Mapping(target = "creditModelId", source = "creditModel.id")
    @Mapping(target = "creditModelName", source = "creditModel.name")
    @Mapping(target = "denomination", source = "creditModel.denomination")
    PlanCreditAllocationDto planCreditAllocationToDto(PlanCreditAllocation allocation);

    List<PlanCreditAllocationDto> planCreditAllocationListToDtoList(List<PlanCreditAllocation> allocations);
}
