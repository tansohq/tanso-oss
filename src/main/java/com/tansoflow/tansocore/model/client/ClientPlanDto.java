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

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Data
@Schema(description = "Client-facing plan information")
public class ClientPlanDto {
    @Schema(description = "Unique identifier of the plan")
    private UUID id;

    @Schema(description = "Unique key for the plan", example = "pro_monthly")
    private String key;

    @Schema(description = "Display name of the plan", example = "Pro Plan")
    private String name;

    @Schema(description = "Detailed description of the plan")
    private String description;

    @Schema(description = "Price amount in dollars", example = "49.00")
    private BigDecimal priceAmount;

    @Schema(description = "Currency code", example = "USD")
    private String currency;

    @Schema(description = "Billing interval in months", example = "1")
    private Integer intervalMonths;

    @Schema(description = "Billing timing", example = "IN_ARREARS")
    private String billingTiming;

    @Schema(description = "Additional metadata associated with the plan")
    private Map<String, Object> metadata;
}
