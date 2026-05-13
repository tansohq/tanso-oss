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
