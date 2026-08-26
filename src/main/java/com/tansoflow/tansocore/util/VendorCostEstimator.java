/*
 * Tanso Core - open-source B2B SaaS monetization engine
 * Copyright (C) 2026  Douglas Baek
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.tansoflow.tansocore.util;

import com.tansoflow.tansocore.entity.ModelPricing;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * What the price book says a bucket of tokens should have cost, in cents.
 *
 * The reconcile compares this against what the vendor's cost report says
 * and what the invoice says. It is only as good as model_pricing: an unknown
 * model prices to zero and is flagged, and a missing cache rate falls back
 * to the full input rate — an overestimate, also flagged — rather than a
 * guessed multiplier.
 */
@Component
@RequiredArgsConstructor
public class VendorCostEstimator {
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);
    private static final BigDecimal CENTS = BigDecimal.valueOf(100);

    private final ModelPricingResolver modelPricingResolver;

    /**
     * @param cents           estimated cost in cents; zero when unpriced
     * @param priced          the model resolved to a price-book row
     * @param cacheRatesKnown both cache rates were present (only meaningful when priced)
     */
    public record Estimate(BigDecimal cents, boolean priced, boolean cacheRatesKnown) {
        public static final Estimate UNPRICED = new Estimate(BigDecimal.ZERO, false, false);
    }

    public Estimate estimate(String model, long uncachedInput, long cacheRead, long cacheCreation, long output) {
        ModelPricingResolver.ResolvedPricing resolved = modelPricingResolver.resolve(model);
        if (resolved == null) {
            return Estimate.UNPRICED;
        }
        ModelPricing p = resolved.pricing();
        BigDecimal in = p.getInputCostPerMillion();
        BigDecimal cacheReadRate = p.getCacheReadCostPerMillion() != null ? p.getCacheReadCostPerMillion() : in;
        BigDecimal cacheWriteRate = p.getCacheWriteCostPerMillion() != null ? p.getCacheWriteCostPerMillion() : in;
        BigDecimal dollars = perMillion(uncachedInput, in)
                .add(perMillion(cacheRead, cacheReadRate))
                .add(perMillion(cacheCreation, cacheWriteRate))
                .add(perMillion(output, p.getOutputCostPerMillion()));
        boolean cacheRatesKnown = p.getCacheReadCostPerMillion() != null && p.getCacheWriteCostPerMillion() != null;
        return new Estimate(dollars.multiply(CENTS).setScale(6, RoundingMode.HALF_UP), true, cacheRatesKnown);
    }

    private static BigDecimal perMillion(long tokens, BigDecimal ratePerMillion) {
        if (tokens == 0 || ratePerMillion == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(tokens).multiply(ratePerMillion).divide(MILLION, 10, RoundingMode.HALF_UP);
    }
}
