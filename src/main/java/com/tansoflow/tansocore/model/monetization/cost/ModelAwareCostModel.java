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
package com.tansoflow.tansocore.model.monetization.cost;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@Slf4j
public class ModelAwareCostModel extends CostModel {
    @Override
    public String getModel() {
        return "model_aware";
    }

    @Override
    public String getCostModel() {
        return "model_aware";
    }

    @JsonProperty("default_input_cost_per_unit")
    @JsonAlias("default_cost_per_unit")
    private BigDecimal defaultInputCostPerUnit;

    @JsonProperty("default_output_cost_per_unit")
    private BigDecimal defaultOutputCostPerUnit;

    @JsonProperty("cost_unit")
    private String costUnit;

    @JsonProperty("model_costs")
    private Map<String, ModelCostRate> modelCosts;

    // Legacy fields — kept for backward compat deserialization of existing rules
    @JsonProperty("meta_model_key")
    private String metaModelKey;

    @JsonProperty("meta_cost_units_key")
    private String metaCostUnitsKey;

    @Override
    public BigDecimal calculateCostAmount(BigDecimal usageUnits) {
        if (usageUnits == null || defaultInputCostPerUnit == null) return BigDecimal.ZERO;
        return usageUnits.multiply(defaultInputCostPerUnit).setScale(4, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal calculateCostAmount(BigDecimal usageUnits, String modelName, BigDecimal costUnits) {
        return calculateCostAmount(usageUnits, modelName, costUnits, null, null);
    }

    @Override
    public BigDecimal calculateCostAmount(BigDecimal usageUnits, String modelName,
                                           BigDecimal costUnits, BigDecimal inputTokens,
                                           BigDecimal outputTokens) {
        // New path: input/output tokens → two-rate calculation
        if (inputTokens != null || outputTokens != null) {
            BigDecimal inTokens = inputTokens != null ? inputTokens : BigDecimal.ZERO;
            BigDecimal outTokens = outputTokens != null ? outputTokens : BigDecimal.ZERO;

            BigDecimal inputRate = defaultInputCostPerUnit;
            BigDecimal outputRate = defaultOutputCostPerUnit != null ? defaultOutputCostPerUnit : BigDecimal.ZERO;

            if (modelName != null && modelCosts != null && modelCosts.containsKey(modelName)) {
                ModelCostRate mcr = modelCosts.get(modelName);
                if (mcr.getInput() != null) inputRate = mcr.getInput();
                if (mcr.getOutput() != null) outputRate = mcr.getOutput();
            }

            if (inputRate == null) inputRate = BigDecimal.ZERO;
            BigDecimal cost = inTokens.multiply(inputRate).add(outTokens.multiply(outputRate));
            return cost.setScale(4, RoundingMode.HALF_UP);
        }

        // Legacy path: single rate × total quantity
        BigDecimal quantity = costUnits != null ? costUnits : usageUnits;
        BigDecimal rate = defaultInputCostPerUnit;
        if (modelName != null && modelCosts != null && modelCosts.containsKey(modelName)) {
            ModelCostRate mcr = modelCosts.get(modelName);
            rate = mcr.getInput() != null ? mcr.getInput() : rate;
        }
        if (quantity == null || rate == null) return BigDecimal.ZERO;
        return quantity.multiply(rate).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * @deprecated Legacy meta-based calculation. Use {@link #calculateCostAmount(BigDecimal, String, BigDecimal, BigDecimal, BigDecimal)}.
     */
    @Override
    @Deprecated
    public BigDecimal calculateCostAmount(BigDecimal usageUnits, Map<String, Object> meta) {
        if (meta == null) return calculateCostAmount(usageUnits);

        // Legacy path: extract from meta using configured keys
        BigDecimal costQuantity = extractBigDecimal(meta, metaCostUnitsKey);
        if (costQuantity == null) costQuantity = usageUnits;

        String modelName = extractString(meta, metaModelKey != null ? metaModelKey : "model");

        BigDecimal rate = defaultInputCostPerUnit;
        if (modelName != null && modelCosts != null && modelCosts.containsKey(modelName)) {
            ModelCostRate mcr = modelCosts.get(modelName);
            rate = mcr.getInput() != null ? mcr.getInput() : rate;
        }

        if (costQuantity == null || rate == null) return BigDecimal.ZERO;
        return costQuantity.multiply(rate).setScale(4, RoundingMode.HALF_UP);
    }

    @Override
    public Map<String, Object> getCostParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("default_input_cost_per_unit", defaultInputCostPerUnit != null ? defaultInputCostPerUnit : BigDecimal.ZERO);
        params.put("default_output_cost_per_unit", defaultOutputCostPerUnit != null ? defaultOutputCostPerUnit : BigDecimal.ZERO);
        params.put("cost_unit", costUnit != null ? costUnit : "CURRENCY");
        params.put("model_costs", modelCosts != null ? modelCosts : Map.of());
        return params;
    }

    private static BigDecimal extractBigDecimal(Map<String, Object> meta, String key) {
        if (key == null || !meta.containsKey(key)) return null;
        Object val = meta.get(key);
        switch (val) {
            case null -> {
                return null;
            }
            case BigDecimal bd -> {
                return bd;
            }
            case Number n -> {
                return BigDecimal.valueOf(n.doubleValue());
            }
            default -> {
            }
        }
        try {
            return new BigDecimal(val.toString());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse numeric value for key '{}': {}", key, val);
            return null;
        }
    }

    private static String extractString(Map<String, Object> meta, String key) {
        if (key == null || !meta.containsKey(key)) return null;
        Object val = meta.get(key);
        return val != null ? val.toString() : null;
    }
}
