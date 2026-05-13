package com.tansoflow.tansocore.model.monetization.request;

import lombok.Data;

import java.util.Map;

@Data
public class PlanFeatureRuleRequest {
    private String planId;
    private String featureId;
    private Boolean isEnabled;
    private String type;
    @io.swagger.v3.oas.annotations.media.Schema(
            description = "Configuration values for the rule. For usage-based billing, include 'model': 'usage', " +
                    "'price_per_unit', and 'usage_unit_type'. Optionally add a cost model for COGS tracking with " +
                    "'cost_model': 'simple' and 'cost_per_unit'.",
            example = "{\"model\": \"usage\", \"usage_unit_type\": \"api_calls\", \"price_per_unit\": 0.10, " +
                    "\"cost_model\": \"simple\", \"cost_per_unit\": 0.03, \"cost_unit\": \"CURRENCY\"}")
    private Map<String, Object> value;
    private String creditModelId;

}
