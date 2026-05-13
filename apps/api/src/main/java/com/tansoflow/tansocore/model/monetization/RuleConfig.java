package com.tansoflow.tansocore.model.monetization;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.tansoflow.tansocore.model.monetization.cost.CostModel;
import com.tansoflow.tansocore.model.monetization.pricing.PricingModel;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RuleConfig {
    private PricingModel pricing;
    private CostModel cost;
}
