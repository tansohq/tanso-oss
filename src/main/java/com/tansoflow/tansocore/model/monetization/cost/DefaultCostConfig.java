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
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DefaultCostConfig {
    @JsonProperty("model_costs")
    private Map<String, ModelCostRate> modelCosts;

    @JsonProperty("default_input_cost_per_unit")
    @JsonAlias("default_cost_per_unit")
    private BigDecimal defaultInputCostPerUnit;

    @JsonProperty("default_output_cost_per_unit")
    private BigDecimal defaultOutputCostPerUnit;

    @JsonProperty("cost_unit")
    private String costUnit;

    public BigDecimal calculateCostAmount(BigDecimal usageUnits, String modelName,
                                           BigDecimal costUnits, BigDecimal inputTokens,
                                           BigDecimal outputTokens) {
        // New path: input/output tokens provided → two-rate calculation
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

        // Legacy fallback: single rate × total quantity
        BigDecimal quantity = costUnits != null ? costUnits : usageUnits;
        BigDecimal rate = defaultInputCostPerUnit;
        if (modelName != null && modelCosts != null && modelCosts.containsKey(modelName)) {
            ModelCostRate mcr = modelCosts.get(modelName);
            rate = mcr.getInput() != null ? mcr.getInput() : rate;
        }
        if (quantity == null || rate == null) return null;
        return quantity.multiply(rate).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * @deprecated Use {@link #calculateCostAmount(BigDecimal, String, BigDecimal, BigDecimal, BigDecimal)}.
     */
    @Deprecated
    public BigDecimal calculateCostAmount(BigDecimal usageUnits, String modelName, BigDecimal costUnits) {
        return calculateCostAmount(usageUnits, modelName, costUnits, null, null);
    }
}
