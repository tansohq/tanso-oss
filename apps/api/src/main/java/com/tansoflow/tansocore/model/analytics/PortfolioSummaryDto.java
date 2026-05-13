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
@Schema(description = "Summary analytics for the entire customer portfolio")
public class PortfolioSummaryDto {
    @Schema(description = "Total monthly recurring revenue across all customers in dollars")
    private BigDecimal totalMrr;

    @Schema(description = "Total costs across all customers in dollars")
    private BigDecimal totalCosts;

    @Schema(description = "Average margin across all customers as a decimal (0.0 to 1.0)")
    private BigDecimal avgMargin;

    @Schema(description = "Customer counts by margin status")
    private CustomersByStatus customersByStatus;

    @Schema(description = "MRR amounts by margin status")
    private MrrByStatus mrrByStatus;

    @Schema(description = "Customer lifetime value (ARPU / monthly churn rate), null if churn is zero")
    private BigDecimal ltv;

    @Schema(description = "Net revenue retention rate as a decimal (e.g., 1.05 = 105%), null if no historical data")
    private BigDecimal nrr;

    @Schema(description = "Number of customers with pending cancellations")
    private int pendingCancelCount;

    @Schema(description = "MRR at risk from pending cancellations")
    private BigDecimal pendingCancelMrr;

    @Schema(description = "Number of customers with pending downgrades")
    private int pendingDowngradeCount;

    @Schema(description = "Total projected usage-based revenue across all customers")
    private BigDecimal totalProjectedUsageRevenue;

    @Schema(description = "Total effective MRR including projected usage revenue (totalMrr + totalProjectedUsageRevenue)")
    private BigDecimal totalEffectiveMrr;

    @Schema(description = "Total recurring gross profit (totalEffectiveMrr - totalCosts)")
    private BigDecimal totalRgp;

    @Schema(description = "RGP margin (totalRgp / totalEffectiveMrr), null if totalEffectiveMrr is zero")
    private BigDecimal rgpMargin;

    @Schema(description = "Average churn risk score across scored customers")
    private BigDecimal avgChurnScore;

    @Schema(description = "Number of customers with critical churn risk (score >= 76)")
    private int criticalChurnCount;

    @Schema(description = "MRR from customers with high or critical churn risk (score >= 51)")
    private BigDecimal highRiskMrr;

    @Schema(description = "Credit impact on portfolio MRR")
    private CreditImpactDto creditImpact;

    @Schema(description = "Projected MRR loss over 30/60/90 day windows")
    private RevenueBurnDownDto revenueBurnDown;

    @Schema(description = "Top models by cost across portfolio")
    private List<ModelProfitabilityDto> topModelsByCost;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Customer counts grouped by margin health status")
    public static class CustomersByStatus {
        @Schema(description = "Number of customers with healthy margins (>=70%)")
        private int healthy;

        @Schema(description = "Number of customers with at-risk margins (40-70%)")
        private int atRisk;

        @Schema(description = "Number of customers with underwater margins (<40%)")
        private int underwater;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "MRR amounts grouped by margin health status")
    public static class MrrByStatus {
        @Schema(description = "MRR from customers with healthy margins")
        private BigDecimal healthy;

        @Schema(description = "MRR from customers with at-risk margins")
        private BigDecimal atRisk;

        @Schema(description = "MRR from customers with underwater margins")
        private BigDecimal underwater;
    }
}
