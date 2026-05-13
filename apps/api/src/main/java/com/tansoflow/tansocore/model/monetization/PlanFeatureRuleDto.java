package com.tansoflow.tansocore.model.monetization;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Data Transfer Object for Plan-Feature link rules")
public class PlanFeatureRuleDto {
    @Schema(description = "Unique identifier of the rule")
    private UUID id;

    @Schema(description = "ID of the associated plan")
    private UUID planId;

    @Schema(description = "ID of the associated feature")
    private UUID featureId;

    @Schema(description = "Configuration values for the rule. For usage-based billing, " +
            "include 'model': 'usage' and 'price_per_unit'.",
            example = "{\"model\": \"usage\", \"price_per_unit\": \"0.50\"}")
    private Map<String, Object> value;

    @Schema(description = "Type of the rule", example = "usage_limit")
    private String type;

    @Schema(description = "Status indicating if the rule is enabled")
    private Boolean enabled;

    @Schema(description = "ID of the linked credit model")
    private UUID creditModelId;

    @Schema(description = "Name of the linked credit model")
    private String creditModelName;

    @Schema(description = "Denomination of the linked credit model")
    private String creditDenomination;
}
