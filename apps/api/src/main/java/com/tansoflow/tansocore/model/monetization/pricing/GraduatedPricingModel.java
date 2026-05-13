package com.tansoflow.tansocore.model.monetization.pricing;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class GraduatedPricingModel extends PricingModel {
    private List<PriceTier> tiers;

    @Override
    public BigDecimal calculateCost(BigDecimal usageUnits) {
        if (usageUnits == null || tiers == null || tiers.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal remainingUsage = usageUnits;
        BigDecimal previousLimit = BigDecimal.ZERO;

        for (PriceTier tier : tiers) {
            BigDecimal limit;
            if (tier.getUpTo() == null || "inf".equalsIgnoreCase(tier.getUpTo().toString())) {
                limit = null; // Infinite
            } else {
                limit = new BigDecimal(tier.getUpTo().toString());
            }

            BigDecimal tierUsage;
            if (limit == null) {
                tierUsage = remainingUsage;
            } else {
                BigDecimal tierRange = limit.subtract(previousLimit);
                tierUsage = remainingUsage.min(tierRange);
                previousLimit = limit;
            }

            if (tierUsage.compareTo(BigDecimal.ZERO) > 0) {
                totalCost = totalCost.add(tierUsage.multiply(tier.getPricePerUnit()));
                if (tier.getFlatFee() != null) {
                    totalCost = totalCost.add(tier.getFlatFee());
                }
                remainingUsage = remainingUsage.subtract(tierUsage);
            }

            if (remainingUsage.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
        }

        return totalCost.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates the incremental cost for a billing period under accumulate mode.
     * Formula: graduatedCost(cumulativeTotal) - graduatedCost(cumulativeTotal - periodDelta)
     */
    public BigDecimal calculateIncrementalCost(BigDecimal cumulativeTotal, BigDecimal periodDelta) {
        BigDecimal costAtCumulative = calculateCost(cumulativeTotal);
        BigDecimal costBeforePeriod = calculateCost(cumulativeTotal.subtract(periodDelta));
        return costAtCumulative.subtract(costBeforePeriod);
    }

    @Override
    public BigDecimal getRate() {
        // For graduated pricing, there isn't a single rate. 
        // Returning the first tier's rate as a fallback or ZERO.
        if (tiers != null && !tiers.isEmpty()) {
            return tiers.getFirst().getPricePerUnit();
        }
        return BigDecimal.ZERO;
    }

    @Data
    public static class PriceTier {
        @JsonProperty("up_to")
        private Object upTo; // Can be "inf" or a number

        @JsonProperty("price_per_unit")
        private BigDecimal pricePerUnit;

        @JsonProperty("flat_fee")
        private BigDecimal flatFee;
    }
}
