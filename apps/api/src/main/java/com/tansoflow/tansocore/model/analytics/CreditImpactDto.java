package com.tansoflow.tansocore.model.analytics;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Credit invoice impact on MRR")
public class CreditImpactDto {
    @Schema(description = "Sum of all credit invoice amounts (negative value)")
    private BigDecimal totalCredits;

    @Schema(description = "Total number of credit invoices")
    private int creditInvoiceCount;

    @Schema(description = "Net effective MRR after credits (totalEffectiveMrr + totalCredits)")
    private BigDecimal netEffectiveMrr;

    @Schema(description = "Ratio of |totalCredits| to totalEffectiveMrr, null if totalEffectiveMrr is zero")
    private BigDecimal creditToMrrRatio;
}
