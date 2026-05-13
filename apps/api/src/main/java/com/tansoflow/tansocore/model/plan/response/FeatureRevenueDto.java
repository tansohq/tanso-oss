package com.tansoflow.tansocore.model.plan.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureRevenueDto {
    private UUID featureId;
    private String featureKey;
    private String featureName;
    private Instant periodStart;
    private Instant periodEnd;
    private BigDecimal units;
    private BigDecimal revenue;
    private String model;
    private String resetMode;
}
