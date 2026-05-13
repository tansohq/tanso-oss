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
