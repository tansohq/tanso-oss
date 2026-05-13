package com.tansoflow.tansocore.mapper.monetization;

import com.tansoflow.tansocore.entity.Feature;
import com.tansoflow.tansocore.model.feature.FeatureDto;
import com.tansoflow.tansocore.model.feature.request.FeatureRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface FeatureMapper {

    List<FeatureDto> featureEntityListToFeatureDtoList(List<Feature> featureEntities);

    @Mapping(target = "modifiedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "account", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "archivedAt", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "isEnabled", ignore = true)
    @Mapping(target = "metadata", ignore = true)
    Feature featureRequestToFeatureEntity(FeatureRequest featureRequest);

    FeatureDto featureEntityToFeatureDto(Feature updatedFeature);

    @Mapping(target = "modifiedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "account", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "archivedAt", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    void updateFeatureEntity(FeatureRequest featureRequest, @MappingTarget Feature feature);

}
