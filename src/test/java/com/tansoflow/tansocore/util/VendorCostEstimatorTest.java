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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VendorCostEstimatorTest {

    @Mock
    private ModelPricingResolver resolver;

    private static ModelPricing pricing(String in, String out, String cacheRead, String cacheWrite) {
        ModelPricing p = new ModelPricing();
        p.setModel("claude-sonnet-4-5");
        p.setProvider("anthropic");
        p.setInputCostPerMillion(new BigDecimal(in));
        p.setOutputCostPerMillion(new BigDecimal(out));
        p.setCacheReadCostPerMillion(cacheRead == null ? null : new BigDecimal(cacheRead));
        p.setCacheWriteCostPerMillion(cacheWrite == null ? null : new BigDecimal(cacheWrite));
        return p;
    }

    @Test
    void pricesEachTokenClassAtItsOwnRateInCents() {
        when(resolver.resolve("claude-sonnet-4-5"))
                .thenReturn(new ModelPricingResolver.ResolvedPricing(pricing("3.00", "15.00", "0.30", "3.75"), false));
        VendorCostEstimator.Estimate e = new VendorCostEstimator(resolver)
                .estimate("claude-sonnet-4-5", 1_000_000, 1_000_000, 1_000_000, 1_000_000);
        // 3.00 + 0.30 + 3.75 + 15.00 dollars = 22.05 → 2205 cents
        assertEquals(0, new BigDecimal("2205").compareTo(e.cents()));
        assertTrue(e.priced());
        assertTrue(e.cacheRatesKnown());
    }

    @Test
    void missingCacheRatesFallBackToInputAndAreFlagged() {
        when(resolver.resolve("claude-sonnet-4-5"))
                .thenReturn(new ModelPricingResolver.ResolvedPricing(pricing("3.00", "15.00", null, null), false));
        VendorCostEstimator.Estimate e = new VendorCostEstimator(resolver)
                .estimate("claude-sonnet-4-5", 0, 1_000_000, 0, 0);
        assertEquals(0, new BigDecimal("300").compareTo(e.cents()));
        assertTrue(e.priced());
        assertFalse(e.cacheRatesKnown());
    }

    @Test
    void unknownModelIsZeroAndUnpriced() {
        when(resolver.resolve("mystery-9000")).thenReturn(null);
        VendorCostEstimator.Estimate e = new VendorCostEstimator(resolver).estimate("mystery-9000", 10, 0, 0, 10);
        assertEquals(0, BigDecimal.ZERO.compareTo(e.cents()));
        assertFalse(e.priced());
    }
}
