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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "model",
    visible = true,
    defaultImpl = SimpleCostModel.class
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = SimpleCostModel.class, name = "simple"),
    @JsonSubTypes.Type(value = ModelAwareCostModel.class, name = "model_aware")
})
public abstract class CostModel {
    @JsonProperty("model")
    private String model;

    // Legacy discriminator — kept for backward compat deserialization of flat format
    @JsonProperty("cost_model")
    private String costModel;

    public abstract BigDecimal calculateCostAmount(BigDecimal usageUnits);

    /**
     * Calculate cost with typed model and costUnits arguments.
     * Default implementation ignores model info — overridden by ModelAwareCostModel.
     */
    public BigDecimal calculateCostAmount(BigDecimal usageUnits, String modelName, BigDecimal costUnits) {
        return calculateCostAmount(usageUnits);
    }

    /**
     * Calculate cost with separate input/output token counts for two-rate pricing.
     * Default implementation falls back to the three-arg method — overridden by ModelAwareCostModel.
     */
    public BigDecimal calculateCostAmount(BigDecimal usageUnits, String modelName,
                                           BigDecimal costUnits, BigDecimal inputTokens,
                                           BigDecimal outputTokens) {
        return calculateCostAmount(usageUnits, modelName, costUnits);
    }

    /**
     * @deprecated Use {@link #calculateCostAmount(BigDecimal, String, BigDecimal)} instead.
     */
    @Deprecated
    public BigDecimal calculateCostAmount(BigDecimal usageUnits, Map<String, Object> meta) {
        return calculateCostAmount(usageUnits);
    }

    public abstract Map<String, Object> getCostParameters();

    /**
     * Returns the effective model type name, checking both new and legacy fields.
     */
    public String getEffectiveModel() {
        if (model != null) return model;
        return costModel;
    }
}
