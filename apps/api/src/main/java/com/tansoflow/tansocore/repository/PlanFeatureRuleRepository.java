package com.tansoflow.tansocore.repository;

import com.tansoflow.tansocore.entity.Feature;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.entity.PlanFeatureRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface PlanFeatureRuleRepository extends JpaRepository<PlanFeatureRule, UUID> {
    @Query("SELECT pfr.feature FROM PlanFeatureRule pfr WHERE pfr.plan = :plan")
    List<Feature> findAllFeaturesByPlan(@Param("plan") Plan plan);

    List<PlanFeatureRule> getPlanFeatureRuleByPlanIn(Collection<Plan> plans);

    @Query("SELECT pfr from PlanFeatureRule pfr WHERE pfr.feature.id = :featureUuid")
    List<PlanFeatureRule> findPlanFeatureRulesByFeatureId(@Param("featureUuid") UUID featureUuid);

    @Query("SELECT pfr from PlanFeatureRule pfr WHERE pfr.plan.id = :planUuid")
    List<PlanFeatureRule> findPlanFeatureRulesByPlanId(@Param("planUuid") UUID planUuid);

    @Query("SELECT pfr from PlanFeatureRule pfr WHERE pfr.plan.id = :planUuid AND pfr.feature.id = :featureUuid")
    PlanFeatureRule findPlanFeatureRuleByPlan_IdAndFeature_Id(UUID planUuid, UUID featureUuid);

    boolean existsByPlanIdAndFeatureIdAndDeletedAtIsNull(UUID planId, UUID featureId);
}
