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
