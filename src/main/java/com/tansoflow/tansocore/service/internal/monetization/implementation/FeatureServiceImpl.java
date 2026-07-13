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
package com.tansoflow.tansocore.service.internal.monetization.implementation;

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.Feature;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.entity.PlanFeatureRule;
import com.tansoflow.tansocore.mapper.monetization.FeatureMapper;
import com.tansoflow.tansocore.model.feature.FeatureDto;
import com.tansoflow.tansocore.model.feature.request.FeatureRequest;
import com.tansoflow.tansocore.model.monetization.request.UuidListRequest;
import com.tansoflow.tansocore.repository.FeatureRepository;
import com.tansoflow.tansocore.repository.PlanFeatureRuleRepository;
import com.tansoflow.tansocore.service.internal.account.AccountService;
import com.tansoflow.tansocore.service.internal.monetization.FeatureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class FeatureServiceImpl implements FeatureService {
    private final FeatureRepository featureRepository;
    private final FeatureMapper featureMapper;
    private final AccountService accountService;
    private final PlanFeatureRuleRepository planFeatureRuleRepository;

    @Override
    public List<FeatureDto> getFeatures(String accountId) {
        Account account = accountService.retrieveAccount(accountId);
        List<Feature> features = featureRepository.findAllByAccount(account);
        return featureMapper.featureEntityListToFeatureDtoList(features);
    }

    @Override
    public FeatureDto getFeature(String accountId, UUID id) {
        Account account = accountService.retrieveAccount(accountId);
        Feature feature = featureRepository.findByIdAndAccount(id, account)
                .orElseThrow(() -> new IllegalArgumentException("Feature not found with id: " + id));
        return featureMapper.featureEntityToFeatureDto(feature);
    }

    @Override
    public FeatureDto getFeatureByKey(String accountId, String key) {
        Feature feature = featureRepository.findByKeyAndAccountId(key, UUID.fromString(accountId))
                .orElseThrow(() -> new IllegalArgumentException("Feature not found with key: " + key));
        return featureMapper.featureEntityToFeatureDto(feature);
    }

    @Override
    public FeatureDto createFeature(String accountId, FeatureRequest featureRequest) {
        Account account = accountService.retrieveAccount(accountId);

        Feature feature = featureMapper.featureRequestToFeatureEntity(featureRequest);

        if (feature.getKey() == null) {
            throw new IllegalArgumentException("Feature key cannot be null");
        }

        Boolean exists = featureRepository.existsByKeyAndAccountId(feature.getKey(), account.getId());
        if (Boolean.TRUE.equals(exists)) {
            throw new IllegalArgumentException("Feature with key: " + feature.getKey() + " already exists");
        }

        feature.setAccount(account);

        Feature result = featureRepository.save(feature);
        return featureMapper.featureEntityToFeatureDto(result);
    }

    @Override
    public FeatureDto updateFeature(String accountId, UUID uuid, FeatureRequest featureRequest) {
        Account account = accountService.retrieveAccount(accountId);

        Feature feature = featureRepository.findByIdAndAccount(uuid, account)
                .orElseThrow(() -> new IllegalArgumentException("Feature not found with id: " + uuid));

        if (featureRequest.getKey() != null && !featureRequest.getKey().equals(feature.getKey())) {
            Boolean exists = featureRepository.existsByKeyAndAccountId(featureRequest.getKey(), account.getId());
            if (Boolean.TRUE.equals(exists)) {
                throw new IllegalArgumentException("Feature with key: " + featureRequest.getKey() + " already exists");
            }
        }

        featureMapper.updateFeatureEntity(featureRequest, feature);

        Feature updatedFeature = featureRepository.save(feature);
        return featureMapper.featureEntityToFeatureDto(updatedFeature);
    }

    @Override
    @Transactional
    public void deleteFeature(UUID id) {
        List<PlanFeatureRule> planFeatureRules = planFeatureRuleRepository.findPlanFeatureRulesByFeatureId(id);
        planFeatureRuleRepository.deleteAll(planFeatureRules);
        featureRepository.deleteById(id);
    }

    @Override
    public void deleteFeatures(UuidListRequest uuids) {
        try {
            Collection<UUID> uuidCollection = uuids.getIds().stream()
                    .map(UUID::fromString)
                    .toList();
            for (UUID uuid : uuidCollection) {
                featureRepository.deleteById(uuid);
            }
        } catch (Exception e) {
            log.error("Unable to delete feature records for: {}", uuids);
        }
    }

    @Override
    public Feature retrieveFeature(Account account, UUID featureUuid) {
        return featureRepository.findByIdAndAccount(featureUuid, account)
                .orElseThrow(() -> new IllegalArgumentException("Feature not found with id: " + featureUuid.toString()));
    }

    @Override
    public List<FeatureDto> retrieveFeaturesLinkedToPlan(Plan plan) {
        return featureMapper
                .featureEntityListToFeatureDtoList(planFeatureRuleRepository
                        .findAllFeaturesByPlan(plan));
    }

    @Override
    public boolean isOwner(String accountId, String featureId) {
        Account account = accountService.retrieveAccount(accountId);
        Feature feature = retrieveFeature(account, UUID.fromString(featureId));
        return feature.getAccount().getId().equals(account.getId());
    }

}
