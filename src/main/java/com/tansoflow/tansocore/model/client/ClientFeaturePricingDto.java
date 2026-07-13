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

import java.math.BigDecimal;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Client-facing pricing details for a feature")
public class ClientFeaturePricingDto {
    @Schema(description = "Pricing model type", example = "usage")
    private String model;

    @Schema(description = "Price per unit of usage", example = "0.05")
    private BigDecimal pricePerUnit;

    @Schema(description = "Label for the usage unit", example = "messages")
    private String unitLabel;

    @Schema(description = "Maximum usage allowed", example = "10000")
    private BigDecimal maxUsage;

    @Schema(description = "Usage reset mode", example = "reset")
    private String resetMode;

    @Schema(description = "Graduated pricing tiers")
    private List<ClientPriceTierDto> tiers;
}
