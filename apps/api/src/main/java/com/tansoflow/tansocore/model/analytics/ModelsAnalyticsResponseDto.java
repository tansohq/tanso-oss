package com.tansoflow.tansocore.model.analytics;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Model-level analytics response")
public class ModelsAnalyticsResponseDto {

    @Schema(description = "Total distinct models tracked")
    private int totalModels;

    @Schema(description = "Total cost across all models")
    private BigDecimal totalCost;

    @Schema(description = "Total revenue across all models")
    private BigDecimal totalRevenue;

    @Schema(description = "Total events with model attribution")
    private long totalEvents;

    @Schema(description = "Cost breakdown by provider")
    private List<ProviderCostDto> providerBreakdown;

    @Schema(description = "Per-model detailed analytics, sorted by cost desc")
    private List<ModelSummaryDto> models;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProviderCostDto {
        private String provider;
        private BigDecimal cost;
        private BigDecimal percentage;
    }
}
