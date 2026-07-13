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
