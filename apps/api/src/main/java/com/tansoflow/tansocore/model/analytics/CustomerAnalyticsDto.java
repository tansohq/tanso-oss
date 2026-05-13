package com.tansoflow.tansocore.model.analytics;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Analytics data for a single customer")
public class CustomerAnalyticsDto {
    @Schema(description = "Unique identifier of the customer")
    private UUID customerId;

    @Schema(description = "Customer display name")
    private String customerName;

    @Schema(description = "Customer email address")
    private String email;

    @Schema(description = "Customer external reference ID")
    private String customerReferenceId;

    @Schema(description = "Unique identifier of the customer's plan")
    private UUID planId;

    @Schema(description = "Name of the customer's plan")
    private String planName;

    @Schema(description = "Monthly recurring revenue in dollars")
    private BigDecimal mrr;

    @Schema(description = "Total cost from plan features in dollars")
    private BigDecimal totalCost;

    @Schema(description = "Margin as a decimal (0.0 to 1.0)")
    private BigDecimal margin;

    @Schema(description = "Margin health status: healthy (>=70%), at_risk (40-70%), underwater (<40%)")
    private String marginStatus;

    @Schema(description = "Per-feature profitability breakdown")
    private List<FeatureProfitabilityDto> featureProfitability;

    @Schema(description = "Projected usage-based revenue for the current billing period")
    private BigDecimal projectedUsageRevenue;

    @Schema(description = "Effective MRR including projected usage revenue (mrr + projectedUsageRevenue)")
    private BigDecimal effectiveMrr;

    @Schema(description = "Recurring gross profit (effectiveMrr - totalCost), null if no cost model")
    private BigDecimal rgp;

    @Schema(description = "Churn risk: null, pending_cancel, or pending_downgrade")
    private String churnRisk;

    @Schema(description = "When the cancellation takes effect, if applicable")
    private Instant cancelEffectiveAt;

    @Schema(description = "Detailed churn risk score with signal breakdown")
    private ChurnScoreDto churnScoreDetails;

    @Schema(description = "Total credit invoice amounts for this customer, null if no credits")
    private BigDecimal totalCredits;

    @Schema(description = "Per-model profitability aggregated across features")
    private List<ModelProfitabilityDto> modelProfitability;
}
