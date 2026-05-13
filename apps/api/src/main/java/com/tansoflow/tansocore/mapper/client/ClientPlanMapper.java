package com.tansoflow.tansocore.mapper.client;

import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.model.client.ClientPlanDto;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ClientPlanMapper {
    ClientPlanDto planToClientPlanDto(Plan plan);
}
