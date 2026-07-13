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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "model",
    visible = true,
    defaultImpl = SimpleUsageModel.class
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = SimpleUsageModel.class, name = "usage"),
    @JsonSubTypes.Type(value = GraduatedPricingModel.class, name = "graduated")
})
public abstract class PricingModel {
    private String model;

    @JsonProperty("usage_unit_type")
    private String usageUnitType;

    @JsonProperty("reset_mode")
    private String resetMode = "reset";

    @JsonProperty("max_usage")
    private BigDecimal maxUsage;

    public abstract BigDecimal calculateCost(BigDecimal usageUnits);
    public abstract BigDecimal getRate();

    public boolean isAccumulateMode() {
        return "accumulate".equalsIgnoreCase(resetMode);
    }

    public boolean hasMaxUsage() {
        return maxUsage != null && maxUsage.compareTo(BigDecimal.ZERO) > 0;
    }
}
