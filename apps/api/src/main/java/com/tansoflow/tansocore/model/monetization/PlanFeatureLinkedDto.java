package com.tansoflow.tansocore.model.monetization;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tansoflow.tansocore.model.credit.PlanCreditAllocationDto;
import com.tansoflow.tansocore.model.feature.FeatureDto;
import com.tansoflow.tansocore.model.plan.PlanDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Data Transfer Object representing a plan and its linked features")
public class PlanFeatureLinkedDto {
    @Schema(description = "Plan details")
    private PlanDto plan;

    @Schema(description = "List of features linked to the plan")
    private List<FeatureDto> features;

    @Schema(description = "Credit allocations configured for this plan")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<PlanCreditAllocationDto> creditAllocations;
}
