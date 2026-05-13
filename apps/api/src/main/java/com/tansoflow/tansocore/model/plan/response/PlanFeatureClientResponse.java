package com.tansoflow.tansocore.model.plan.response;

import com.tansoflow.tansocore.model.monetization.PlanFeatureLinkedDto;
import lombok.Data;

import java.util.List;

@Data
public class PlanFeatureClientResponse {
    List<PlanFeatureLinkedDto> plans;
}
