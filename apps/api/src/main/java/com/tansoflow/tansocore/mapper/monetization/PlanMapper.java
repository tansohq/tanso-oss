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