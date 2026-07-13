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
package com.tansoflow.tansocore.mapper.monetization;

import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.model.plan.PlanDto;
import com.tansoflow.tansocore.model.plan.request.PlanRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface PlanMapper {
    List<PlanDto> planEntityListToPlanDtoList(List<Plan> planEntities);

    @Mapping(target = "intervalMonths", source = "intervalMonths")
    PlanDto planEntityToPlanDto(Plan plan);

    @Mapping(target = "modifiedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "account", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "archivedAt", ignore = true)
    @Mapping(target = "currency", ignore = true)
    @Mapping(target = "intervalMonths", source = "intervalMonths")
    void updatePlanFromPlanRequest(PlanRequest planRequest, @MappingTarget Plan plan);

    @Mapping(target = "modifiedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "account", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "archivedAt", ignore = true)
    @Mapping(target = "currency", ignore = true)
    @Mapping(target = "intervalMonths", source = "intervalMonths", defaultValue = "3")
    @Mapping(target = "metadata", ignore = true)
    Plan planRequestToPlanEntity(PlanRequest planRequest);
}