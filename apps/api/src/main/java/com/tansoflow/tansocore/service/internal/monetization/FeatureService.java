package com.tansoflow.tansocore.service.internal.monetization;

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.Feature;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.model.feature.FeatureDto;
import com.tansoflow.tansocore.model.feature.request.FeatureRequest;
import com.tansoflow.tansocore.model.monetization.request.UuidListRequest;

import java.util.List;
import java.util.UUID;

public interface FeatureService {
    List<FeatureDto> getFeatures(String accountId);

    FeatureDto getFeature(String accountId, UUID id);

    FeatureDto getFeatureByKey(String accountId, String key);

    FeatureDto createFeature(String accountId, FeatureRequest featureRequest);

    FeatureDto updateFeature(String accountId, UUID uuid, FeatureRequest featureRequest);

    void deleteFeature(UUID id);

    void deleteFeatures(UuidListRequest uuids);

    Feature retrieveFeature(Account account, UUID featureUuid);

    List<FeatureDto> retrieveFeaturesLinkedToPlan(Plan plan);

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean isOwner(String accountId, String featureId);
}
