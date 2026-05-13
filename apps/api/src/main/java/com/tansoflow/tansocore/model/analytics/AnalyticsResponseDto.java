package com.tansoflow.tansocore.model.analytics;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Complete analytics response including portfolio summary and customer details")
public class AnalyticsResponseDto {
    @Schema(description = "Portfolio-level summary statistics")
    private PortfolioSummaryDto summary;

    @Schema(description = "List of individual customer analytics")
    private List<CustomerAnalyticsDto> customers;
}
