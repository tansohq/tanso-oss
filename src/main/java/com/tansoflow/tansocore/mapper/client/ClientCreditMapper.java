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
