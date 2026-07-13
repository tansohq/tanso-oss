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

import com.tansoflow.tansocore.model.monetization.PlanFeatureRuleDto;
import com.tansoflow.tansocore.model.monetization.request.PlanFeatureLinkedDiffRequest;
import com.tansoflow.tansocore.model.monetization.request.PlanFeatureRuleRequest;
import com.tansoflow.tansocore.model.monetization.response.PlanFeatureLinkedDiffResponse;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface PlanFeatureRuleService {


    PlanFeatureRuleDto createPlanFeatureRule(String accountId, PlanFeatureRuleRequest planFeatureRuleRequest);

    PlanFeatureRuleDto updatePlanFeatureRule(String accountId, PlanFeatureRuleRequest planFeatureRuleRequest);

    void deletePlanFeatureRule(String accountId, String featureUuid, String planUuid);

    PlanFeatureRuleDto getPlanFeatureRule(String accountId, String featureUuid, String planUuid);

    @Transactional
    PlanFeatureLinkedDiffResponse addRemovePlanFeatureRulesByDiff(String accountId, PlanFeatureLinkedDiffRequest planFeatureLinkedDiffRequest, String planUuid);

    @Transactional
    void reconcilePlanFeature(UUID accountId,
                              UUID planId,
                              UUID featureId,
                              UUID entitlementMetaId);
}
