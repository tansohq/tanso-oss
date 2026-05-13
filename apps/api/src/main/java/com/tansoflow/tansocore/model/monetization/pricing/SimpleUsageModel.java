package com.tansoflow.tansocore.model.monetization.pricing;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SimpleUsageModel extends PricingModel {
    @JsonProperty("price_per_unit")
    private BigDecimal pricePerUnit;

    @JsonProperty("cost_rate")
    private BigDecimal costRate;

    @JsonProperty("cost_per_unit")
    private BigDecimal costPerUnit;

    @JsonProperty("cost_unit")
    private String costUnit;

    @Override
    public BigDecimal calculateCost(BigDecimal usageUnits) {
        if (usageUnits == null) return BigDecimal.ZERO;
        return usageUnits.multiply(getRate()).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal getRate() {
        if (pricePerUnit != null) return pricePerUnit;
        if (costRate != null) return costRate;
        if (costPerUnit != null) return costPerUnit;
        return BigDecimal.ZERO;
    }
}
