package com.tansoflow.tansocore.model.analytics;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Composite churn risk score with signal breakdown")
public class ChurnScoreDto {
    @Schema(description = "Composite churn risk score (0-100)")
    private Integer score;

    @Schema(description = "Risk label: low (0-25), moderate (26-50), high (51-75), critical (76-100)")
    private String riskLabel;

    @Schema(description = "Entitlement staleness score (0-100)")
    private Integer stalenessScore;

    @Schema(description = "Event frequency trend score (0-100)")
    private Integer eventTrendScore;

    @Schema(description = "Margin status score (0-100)")
    private Integer marginScore;

    @Schema(description = "Plan tier position score (0-100)")
    private Integer planTierScore;

    @Schema(description = "Feature utilization score (0-100)")
    private Integer utilizationScore;
}
