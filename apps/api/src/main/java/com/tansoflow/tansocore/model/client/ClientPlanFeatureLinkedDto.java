package com.tansoflow.tansocore.model.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tansoflow.tansocore.model.credit.PlanCreditAllocationDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Client-facing plan with its linked features and pricing details")
public class ClientPlanFeatureLinkedDto {
    @Schema(description = "Plan details")
    private ClientPlanDto plan;

    @Schema(description = "List of features linked to the plan with pricing information")
    private List<ClientFeatureDto> features;

    @Schema(description = "Credit allocations included with this plan")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<PlanCreditAllocationDto> creditAllocations;
}
