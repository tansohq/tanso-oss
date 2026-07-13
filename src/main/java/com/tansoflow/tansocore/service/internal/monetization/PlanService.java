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
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.model.monetization.PlanFeatureLinkedDto;
import com.tansoflow.tansocore.model.monetization.request.UuidListRequest;
import com.tansoflow.tansocore.model.plan.PlanDto;
import com.tansoflow.tansocore.model.plan.request.PlanRequest;

import java.util.List;
import java.util.UUID;

public interface PlanService {

    PlanDto createPlans(String accountId, PlanRequest planRequest);

    PlanDto updatePlan(String accountId, String uuid, PlanRequest planRequest);

    void deletePlan(String uuid);

    void deletePlans(UuidListRequest uuids);

    List<PlanDto> getPlans(String accountId);

    Plan retrievePlan(UUID accountId, UUID planUuid);

    Plan retrievePlan(Account account, UUID planUuid);

    PlanFeatureLinkedDto retrievePlanFeatureLinkByPlanUuid(String planUuid, String accountId);

    List<PlanFeatureLinkedDto> retrievePlanFeaturesMapByAccount(String accountUuid);

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean isOwner(String accountId, String planId);
}
