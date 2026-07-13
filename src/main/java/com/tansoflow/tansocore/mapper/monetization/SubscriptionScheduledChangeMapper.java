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
