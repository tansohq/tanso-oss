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
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(value = {"costParameters", "effectiveModel"}, ignoreUnknown = true)
public class SimpleCostModel extends CostModel {
    @Override
    public String getModel() {
        return "simple";
    }

    @Override
    public String getCostModel() {
        return "simple";
    }

    @JsonProperty("cost_per_unit")
    private BigDecimal costPerUnit;

    @JsonProperty("cost_unit")
    private String costUnit;

    @Override
    public BigDecimal calculateCostAmount(BigDecimal usageUnits) {
        if (usageUnits == null || costPerUnit == null) return BigDecimal.ZERO;
        return usageUnits.multiply(costPerUnit).setScale(4, RoundingMode.HALF_UP);
    }

    @Override
    public Map<String, Object> getCostParameters() {
        return Map.of(
            "cost_per_unit", costPerUnit != null ? costPerUnit : BigDecimal.ZERO,
            "cost_unit", costUnit != null ? costUnit : "CURRENCY"
        );
    }
}
