package com.tansoflow.tansocore.model.monetization.cost;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(value = {"costParameters", "effectiveModel"}, ignoreUnknown = true)
public class SimpleCostModel extends CostModel {
    @Override
    public String getModel() {
        return "simple";
    }

    @Override
    public String getCostModel() {
        return "simple";
    }

    @JsonProperty("cost_per_unit")
    private BigDecimal costPerUnit;

    @JsonProperty("cost_unit")
    private String costUnit;

    @Override
    public BigDecimal calculateCostAmount(BigDecimal usageUnits) {
        if (usageUnits == null || costPerUnit == null) return BigDecimal.ZERO;
        return usageUnits.multiply(costPerUnit).setScale(4, RoundingMode.HALF_UP);
    }

    @Override
    public Map<String, Object> getCostParameters() {
        return Map.of(
            "cost_per_unit", costPerUnit != null ? costPerUnit : BigDecimal.ZERO,
            "cost_unit", costUnit != null ? costUnit : "CURRENCY"
        );
    }
}
