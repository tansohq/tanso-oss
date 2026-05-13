package com.tansoflow.tansocore.model.analytics;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Detailed analytics for a single LLM model")
public class ModelSummaryDto {
    private String model;
    private String modelProvider;
    private long eventCount;
    private long customerCount;
    private long featureCount;
    private BigDecimal totalCost;
    private BigDecimal totalRevenue;
    private BigDecimal totalUsage;
    private BigDecimal avgCostPerEvent;
    private BigDecimal margin;
    private Instant lastSeen;
}
