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
package com.tansoflow.tansocore.model.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.UUID;

@Data
@Schema(description = "Client-facing feature information with pricing type")
public class ClientFeatureDto {
    @Schema(description = "Unique identifier of the feature")
    private UUID id;

    @Schema(description = "Display name of the feature", example = "API Access")
    private String name;

    @Schema(description = "Unique key for the feature", example = "api_access")
    private String key;

    @Schema(description = "Detailed description of the feature")
    private String description;

    @Schema(description = "Pricing type: included, usage_based, or graduated", example = "usage_based")
    private String pricingType;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Pricing details, null for included features")
    private ClientFeaturePricingDto pricing;
}
