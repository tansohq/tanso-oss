package com.tansoflow.tansocore.mapper.monetization;

import com.tansoflow.tansocore.entity.SubscriptionScheduledChange;
import com.tansoflow.tansocore.model.subscription.SubscriptionScheduledChangeDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        uses = {PlanMapper.class},
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface SubscriptionScheduledChangeMapper {

    @Mapping(target = "subscriptionId", source = "subscription.id")
    @Mapping(target = "fromPlan", source = "fromPlan")
    @Mapping(target = "toPlan", source = "toPlan")
    SubscriptionScheduledChangeDto toDto(SubscriptionScheduledChange entity);

    List<SubscriptionScheduledChangeDto> toDtoList(List<SubscriptionScheduledChange> entities);
}
