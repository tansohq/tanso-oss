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
package com.tansoflow.tansocore.model.monetization;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Data Transfer Object for Plan-Feature link rules")
public class PlanFeatureRuleDto {
    @Schema(description = "Unique identifier of the rule")
    private UUID id;

    @Schema(description = "ID of the associated plan")
    private UUID planId;

    @Schema(description = "ID of the associated feature")
    private UUID featureId;

    @Schema(description = "Configuration values for the rule. For usage-based billing, " +
            "include 'model': 'usage' and 'price_per_unit'.",
            example = "{\"model\": \"usage\", \"price_per_unit\": \"0.50\"}")
    private Map<String, Object> value;

    @Schema(description = "Type of the rule", example = "usage_limit")
    private String type;

    @Schema(description = "Status indicating if the rule is enabled")
    private Boolean enabled;

    @Schema(description = "ID of the linked credit model")
    private UUID creditModelId;

    @Schema(description = "Name of the linked credit model")
    private String creditModelName;

    @Schema(description = "Denomination of the linked credit model")
    private String creditDenomination;
}
