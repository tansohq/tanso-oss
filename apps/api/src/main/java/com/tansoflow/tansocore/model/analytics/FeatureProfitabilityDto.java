package com.tansoflow.tansocore.model.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureProfitabilityDto {
    private UUID featureId;
    private String featureName;
    private String featureKey;
    private BigDecimal usageUnits;
    private BigDecimal revenue;
    private BigDecimal cost;
    private BigDecimal margin;
    private List<ModelProfitabilityDto> modelBreakdown;
}
