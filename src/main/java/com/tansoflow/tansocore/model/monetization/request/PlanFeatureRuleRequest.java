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
package com.tansoflow.tansocore.model.monetization.request;

import lombok.Data;

import java.util.Map;

@Data
public class PlanFeatureRuleRequest {
    private String planId;
    private String featureId;
    private Boolean isEnabled;
    private String type;
    @io.swagger.v3.oas.annotations.media.Schema(
            description = "Configuration values for the rule. For usage-based billing, include 'model': 'usage', " +
                    "'price_per_unit', and 'usage_unit_type'. Optionally add a cost model for COGS tracking with " +
                    "'cost_model': 'simple' and 'cost_per_unit'.",
            example = "{\"model\": \"usage\", \"usage_unit_type\": \"api_calls\", \"price_per_unit\": 0.10, " +
                    "\"cost_model\": \"simple\", \"cost_per_unit\": 0.03, \"cost_unit\": \"CURRENCY\"}")
    private Map<String, Object> value;
    private String creditModelId;

}
