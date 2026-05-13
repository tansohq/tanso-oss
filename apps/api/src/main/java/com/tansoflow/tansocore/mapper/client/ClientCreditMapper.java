package com.tansoflow.tansocore.mapper.client;

import com.tansoflow.tansocore.model.client.ClientCreditGrantDto;
import com.tansoflow.tansocore.model.client.ClientCreditPoolDto;
import com.tansoflow.tansocore.model.credit.CreditGrantDto;
import com.tansoflow.tansocore.model.credit.CreditPoolDto;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface ClientCreditMapper {

    ClientCreditPoolDto toClientCreditPoolDto(CreditPoolDto creditPoolDto);

    List<ClientCreditPoolDto> toClientCreditPoolDtoList(List<CreditPoolDto> creditPoolDtos);

    ClientCreditGrantDto toClientCreditGrantDto(CreditGrantDto creditGrantDto);

    List<ClientCreditGrantDto> toClientCreditGrantDtoList(List<CreditGrantDto> creditGrantDtos);
}
