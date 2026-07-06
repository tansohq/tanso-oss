package com.tansoflow.tansocore.util.monetization;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tansoflow.tansocore.entity.PlanFeatureRule;
import com.tansoflow.tansocore.model.monetization.RuleConfig;
import com.tansoflow.tansocore.model.monetization.cost.CostModel;
import com.tansoflow.tansocore.model.monetization.pricing.PricingModel;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class RuleCalculationUtil {
    private static final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);

    /**
     * Extract a typed RuleConfig from a PlanFeatureRule, handling both nested and legacy flat formats.
     */
    public static RuleConfig extractRuleConfig(PlanFeatureRule rule) {
        if (rule == null || rule.getValue() == null) return null;
        return extractRuleConfig(rule.getValue());
    }

    /**
     * Extract a typed RuleConfig from a raw value map, handling both nested and legacy flat formats.
     */
    public static RuleConfig extractRuleConfig(Map<String, Object> value) {
        if (value == null) return null;

        try {
            // Nested format: { "pricing": {...}, "cost": {...} }
            if (value.containsKey("pricing")) {
                return mapper.convertValue(value, RuleConfig.class);
            }

            // Legacy flat format: { "model": "usage", "cost_model": "simple", ... }
            RuleConfig config = new RuleConfig();
            config.setPricing(mapper.convertValue(value, PricingModel.class));

            if (value.containsKey("cost_model") || value.containsKey("cost_per_unit") || value.containsKey("model_costs")) {
                // For flat format, the cost model uses "cost_model" as its discriminator.
                // We need to map "cost_model" -> "model" for the new CostModel discriminator.
                config.setCost(extractCostModelFromFlatMap(value));
            }
            return config;
        } catch (Exception e) {
            log.error("Failed to extract RuleConfig from rule value {}: {}", value, e.getMessage(), e);
            throw new IllegalStateException("Malformed plan feature rule configuration", e);
        }
    }

    /**
     * Extract cost model from a flat-format map where the discriminator is "cost_model" (legacy).
     * Remaps "cost_model" → "model" so Jackson's @JsonTypeInfo can resolve the subtype.
     */
    private static CostModel extractCostModelFromFlatMap(Map<String, Object> value) {
        try {
            Map<String, Object> costMap = new HashMap<>(value);
            if (costMap.containsKey("cost_model")) {
                costMap.put("model", costMap.get("cost_model"));
            } else {
                costMap.put("model", "simple");
            }
            return mapper.convertValue(costMap, CostModel.class);
        } catch (Exception e) {
            log.error("Failed to extract CostModel from flat map {}: {}", value, e.getMessage(), e);
            throw new IllegalStateException("Malformed plan feature rule cost configuration", e);
        }
    }

    public static PricingModel extractPricingModel(PlanFeatureRule rule) {
        RuleConfig config = extractRuleConfig(rule);
        return config != null ? config.getPricing() : null;
    }

    public static PricingModel extractPricingModel(Map<String, Object> value) {
        RuleConfig config = extractRuleConfig(value);
        return config != null ? config.getPricing() : null;
    }

    public static CostModel extractCostModel(PlanFeatureRule rule) {
        RuleConfig config = extractRuleConfig(rule);
        return config != null ? config.getCost() : null;
    }

    public static CostModel extractCostModel(Map<String, Object> value) {
        RuleConfig config = extractRuleConfig(value);
        return config != null ? config.getCost() : null;
    }

    public static BigDecimal calculateRevenueAmount(BigDecimal usage, PlanFeatureRule rule) {
        PricingModel pricingModel = extractPricingModel(rule);
        return pricingModel != null ? pricingModel.calculateCost(usage) : BigDecimal.ZERO;
    }

    public static BigDecimal calculateCostAmount(BigDecimal usage, PlanFeatureRule rule) {
        CostModel model = extractCostModel(rule);
        return model != null ? model.calculateCostAmount(usage) : null;
    }

    /**
     * @deprecated Use {@link #calculateCostAmount(BigDecimal, PlanFeatureRule, String, BigDecimal)} instead.
     */
    @Deprecated
    public static BigDecimal calculateCostAmount(BigDecimal usage, PlanFeatureRule rule, Map<String, Object> meta) {
        CostModel costModel = extractCostModel(rule);
        return costModel != null ? costModel.calculateCostAmount(usage, meta) : null;
    }

    /**
     * Calculate cost with typed model name and costUnits — the preferred path.
     */
    public static BigDecimal calculateCostAmount(BigDecimal usage, PlanFeatureRule rule,
                                                  String modelName, BigDecimal costUnits) {
        CostModel costModel = extractCostModel(rule);
        return costModel != null ? costModel.calculateCostAmount(usage, modelName, costUnits) : null;
    }

    /**
     * Calculate cost with separate input/output token counts for two-rate pricing.
     */
    public static BigDecimal calculateCostAmount(BigDecimal usage, PlanFeatureRule rule,
                                                  String modelName, BigDecimal costUnits,
                                                  BigDecimal inputTokens, BigDecimal outputTokens) {
        CostModel costModel = extractCostModel(rule);
        return costModel != null
                ? costModel.calculateCostAmount(usage, modelName, costUnits, inputTokens, outputTokens)
                : null;
    }

    public static boolean isMaxUsageExceeded(PricingModel model, BigDecimal cumulative) {
        return model != null && model.hasMaxUsage()
                && cumulative.compareTo(model.getMaxUsage()) >= 0;
    }

}
