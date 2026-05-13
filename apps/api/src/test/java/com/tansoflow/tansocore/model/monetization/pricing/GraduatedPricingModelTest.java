package com.tansoflow.tansocore.model.monetization.pricing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GraduatedPricingModelTest {

    // --- Helper ---

    private GraduatedPricingModel.PriceTier tier(Object upTo, String pricePerUnit, String flatFee) {
        GraduatedPricingModel.PriceTier t = new GraduatedPricingModel.PriceTier();
        t.setUpTo(upTo);
        t.setPricePerUnit(new BigDecimal(pricePerUnit));
        if (flatFee != null) {
            t.setFlatFee(new BigDecimal(flatFee));
        }
        return t;
    }

    private GraduatedPricingModel model(List<GraduatedPricingModel.PriceTier> tiers) {
        GraduatedPricingModel m = new GraduatedPricingModel();
        m.setTiers(tiers);
        return m;
    }

    // --- calculateCost: null / empty edge cases ---

    @Test
    void calculateCost_NullUsage_ReturnsZero() {
        GraduatedPricingModel m = model(List.of(tier("inf", "0.01", null)));
        assertTrue(BigDecimal.ZERO.compareTo(m.calculateCost(null)) == 0);
    }

    @Test
    void calculateCost_NullTiers_ReturnsZero() {
        GraduatedPricingModel m = new GraduatedPricingModel();
        m.setTiers(null);
        assertTrue(BigDecimal.ZERO.compareTo(m.calculateCost(new BigDecimal("100"))) == 0);
    }

    @Test
    void calculateCost_EmptyTiers_ReturnsZero() {
        GraduatedPricingModel m = model(List.of());
        assertTrue(BigDecimal.ZERO.compareTo(m.calculateCost(new BigDecimal("100"))) == 0);
    }

    @Test
    void calculateCost_ZeroUsage_ReturnsZero() {
        GraduatedPricingModel m = model(List.of(
                tier(1000, "0.01", "10"),
                tier("inf", "0.001", "5")
        ));
        assertEquals(new BigDecimal("0.00"), m.calculateCost(BigDecimal.ZERO));
    }

    // --- calculateCost: basic tiered calculation without flat fees ---

    @Test
    void calculateCost_SingleTierInf_NoFlatFee() {
        // 500 units × $0.01 = $5.00
        GraduatedPricingModel m = model(List.of(tier("inf", "0.01", null)));
        assertEquals(new BigDecimal("5.00"), m.calculateCost(new BigDecimal("500")));
    }

    @Test
    void calculateCost_UsageWithinFirstTier_NoFlatFee() {
        // 500 units in tier 1 (up to 1000) × $0.00 = $0.00
        GraduatedPricingModel m = model(List.of(
                tier(1000, "0.00", null),
                tier("inf", "0.001", null)
        ));
        assertEquals(new BigDecimal("0.00"), m.calculateCost(new BigDecimal("500")));
    }

    @Test
    void calculateCost_UsageSpansTwoTiers_NoFlatFee() {
        // Tier 1: 1000 × $0.00 = $0.00
        // Tier 2: 4000 × $0.001 = $4.00
        // Total = $4.00
        GraduatedPricingModel m = model(List.of(
                tier(1000, "0.00", null),
                tier("inf", "0.001", null)
        ));
        assertEquals(new BigDecimal("4.00"), m.calculateCost(new BigDecimal("5000")));
    }

    @Test
    void calculateCost_UsageSpansThreeTiers_NoFlatFee() {
        // Tier 1: 1000 × $0.00 = $0.00
        // Tier 2: 9000 × $0.001 = $9.00
        // Tier 3: 5000 × $0.0001 = $0.50
        // Total = $9.50
        GraduatedPricingModel m = model(List.of(
                tier(1000, "0.00", null),
                tier(10000, "0.001", null),
                tier("inf", "0.0001", null)
        ));
        assertEquals(new BigDecimal("9.50"), m.calculateCost(new BigDecimal("15000")));
    }

    // --- calculateCost: with flat fees ---

    @Test
    void calculateCost_SingleTier_WithFlatFee() {
        // 500 units × $0.01 + $10.00 flat = $15.00
        GraduatedPricingModel m = model(List.of(tier("inf", "0.01", "10")));
        assertEquals(new BigDecimal("15.00"), m.calculateCost(new BigDecimal("500")));
    }

    @Test
    void calculateCost_FlatFeeOnlyTier_ZeroPricePerUnit() {
        // 100 units × $0.00 + $25.00 flat = $25.00
        GraduatedPricingModel m = model(List.of(tier("inf", "0.00", "25")));
        assertEquals(new BigDecimal("25.00"), m.calculateCost(new BigDecimal("100")));
    }

    @Test
    void calculateCost_SpecExample_15000Units_WithFlatFees() {
        // The example from the plan spec:
        // Tier 1: 1000 × $0.00 + $10.00 = $10.00
        // Tier 2: 9000 × $0.001 + $5.00 = $14.00
        // Tier 3: 5000 × $0.0001 + $0.00 = $0.50
        // Total = $24.50
        GraduatedPricingModel m = model(List.of(
                tier(1000, "0.00", "10"),
                tier(10000, "0.001", "5"),
                tier("inf", "0.0001", "0")
        ));
        assertEquals(new BigDecimal("24.50"), m.calculateCost(new BigDecimal("15000")));
    }

    @Test
    void calculateCost_UsageInFirstTierOnly_OnlyFirstFlatFeeApplied() {
        // 500 units in tier 1 (up to 1000) × $0.00 + $10.00 flat = $10.00
        // Tier 2 should NOT apply its flat fee since no usage reached it
        GraduatedPricingModel m = model(List.of(
                tier(1000, "0.00", "10"),
                tier("inf", "0.001", "5")
        ));
        assertEquals(new BigDecimal("10.00"), m.calculateCost(new BigDecimal("500")));
    }

    @Test
    void calculateCost_UsageExactlyAtTierBoundary() {
        // 1000 units exactly fills tier 1
        // Tier 1: 1000 × $0.00 + $10.00 = $10.00
        // Tier 2: 0 units, so flat fee should NOT apply
        GraduatedPricingModel m = model(List.of(
                tier(1000, "0.00", "10"),
                tier("inf", "0.001", "5")
        ));
        assertEquals(new BigDecimal("10.00"), m.calculateCost(new BigDecimal("1000")));
    }

    @Test
    void calculateCost_UsageJustOverBoundary_BothFlatFeesApply() {
        // 1001 units: tier 1 gets 1000, tier 2 gets 1
        // Tier 1: 1000 × $0.00 + $10.00 = $10.00
        // Tier 2: 1 × $0.001 + $5.00 = $5.001 → rounds
        // Total = $15.00 (rounded from 15.001)
        GraduatedPricingModel m = model(List.of(
                tier(1000, "0.00", "10"),
                tier("inf", "0.001", "5")
        ));
        assertEquals(new BigDecimal("15.00"), m.calculateCost(new BigDecimal("1001")));
    }

    @Test
    void calculateCost_NullFlatFee_TreatedAsZero() {
        // Tier with null flatFee should behave the same as flatFee=0
        // 500 × $0.01 = $5.00
        GraduatedPricingModel m = model(List.of(tier("inf", "0.01", null)));
        GraduatedPricingModel m2 = model(List.of(tier("inf", "0.01", "0")));
        BigDecimal usage = new BigDecimal("500");
        assertEquals(m2.calculateCost(usage), m.calculateCost(usage));
    }

    @Test
    void calculateCost_MixedNullAndSetFlatFees() {
        // Tier 1: null flat fee, Tier 2: $5 flat fee
        // 5000 units: tier 1 gets 1000, tier 2 gets 4000
        // Tier 1: 1000 × $0.00 + $0 (null) = $0.00
        // Tier 2: 4000 × $0.001 + $5.00 = $9.00
        GraduatedPricingModel m = model(List.of(
                tier(1000, "0.00", null),
                tier("inf", "0.001", "5")
        ));
        assertEquals(new BigDecimal("9.00"), m.calculateCost(new BigDecimal("5000")));
    }

    // --- calculateCost: numeric upTo values ---

    @Test
    void calculateCost_IntegerUpTo() {
        // upTo is an Integer, not a String
        GraduatedPricingModel.PriceTier t1 = new GraduatedPricingModel.PriceTier();
        t1.setUpTo(1000);
        t1.setPricePerUnit(new BigDecimal("0.01"));

        GraduatedPricingModel.PriceTier t2 = new GraduatedPricingModel.PriceTier();
        t2.setUpTo("inf");
        t2.setPricePerUnit(new BigDecimal("0.005"));

        GraduatedPricingModel m = model(List.of(t1, t2));
        // 2000 units: tier 1 = 1000 × 0.01 = 10, tier 2 = 1000 × 0.005 = 5
        assertEquals(new BigDecimal("15.00"), m.calculateCost(new BigDecimal("2000")));
    }

    // --- calculateCost: rounding ---

    @Test
    void calculateCost_ResultRoundedToTwoDecimalPlaces() {
        // 3 units × $0.333 = $0.999 → rounds to $1.00
        GraduatedPricingModel m = model(List.of(tier("inf", "0.333", null)));
        assertEquals(new BigDecimal("1.00"), m.calculateCost(new BigDecimal("3")));
    }

    // --- getRate ---

    @Test
    void getRate_ReturnFirstTierRate() {
        GraduatedPricingModel m = model(List.of(
                tier(1000, "0.05", "10"),
                tier("inf", "0.01", null)
        ));
        assertEquals(new BigDecimal("0.05"), m.getRate());
    }

    @Test
    void getRate_NullTiers_ReturnsZero() {
        GraduatedPricingModel m = new GraduatedPricingModel();
        m.setTiers(null);
        assertEquals(BigDecimal.ZERO, m.getRate());
    }

    @Test
    void getRate_EmptyTiers_ReturnsZero() {
        GraduatedPricingModel m = model(List.of());
        assertEquals(BigDecimal.ZERO, m.getRate());
    }
}
