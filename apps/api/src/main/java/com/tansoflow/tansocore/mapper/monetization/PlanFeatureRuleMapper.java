package com.tansoflow.tansocore.mapper.monetization;

import com.tansoflow.tansocore.entity.PlanFeatureRule;
import com.tansoflow.tansocore.model.monetization.PlanFeatureRuleDto;
import com.tansoflow.tansocore.model.monetization.request.PlanFeatureRuleRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface PlanFeatureRuleMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "plan", ignore = true)
    @Mapping(target = "feature", ignore = true)
    @Mapping(target = "creditModel", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "modifiedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "archivedAt", ignore = true)
    PlanFeatureRule planFeatureRuleRequestToPlanFeatureRuleEntity(PlanFeatureRuleRequest planFeatureRuleRequest);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "enabled", source = "isEnabled")
    @Mapping(target = "type", source = "type")
    @Mapping(target = "value", source = "value")
    @Mapping(target = "planId", source = "plan.id")
    @Mapping(target = "featureId", source = "feature.id")
    @Mapping(target = "creditModelId", source = "creditModel.id")
    @Mapping(target = "creditModelName", source = "creditModel.name")
    @Mapping(target = "creditDenomination", source = "creditModel.denomination")
    PlanFeatureRuleDto planFeatureRuleEntityToPlanFeatureRuleDto(PlanFeatureRule savedRule);

    @Mapping(target = "modifiedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "plan", ignore = true)
    @Mapping(target = "feature", ignore = true)
    @Mapping(target = "creditModel", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "archivedAt", ignore = true)
    void updatePlanFeatureRuleEntity(PlanFeatureRuleRequest planFeatureRuleRequest, @MappingTarget PlanFeatureRule planFeatureRule);
}