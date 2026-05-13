package com.tansoflow.tansocore.model.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelProfitabilityDto {
    private String model;
    private String modelProvider;
    private BigDecimal usageUnits;
    private BigDecimal cost;
    private BigDecimal revenue;
    private BigDecimal margin;
}
