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
package com.tansoflow.tansocore.model.monetization.pricing;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SimpleUsageModel extends PricingModel {
    @JsonProperty("price_per_unit")
    private BigDecimal pricePerUnit;

    @JsonProperty("cost_rate")
    private BigDecimal costRate;

    @JsonProperty("cost_per_unit")
    private BigDecimal costPerUnit;

    @JsonProperty("cost_unit")
    private String costUnit;

    @Override
    public BigDecimal calculateCost(BigDecimal usageUnits) {
        if (usageUnits == null) return BigDecimal.ZERO;
        return usageUnits.multiply(getRate()).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal getRate() {
        if (pricePerUnit != null) return pricePerUnit;
        if (costRate != null) return costRate;
        if (costPerUnit != null) return costPerUnit;
        return BigDecimal.ZERO;
    }
}
