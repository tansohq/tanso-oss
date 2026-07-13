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
package com.tansoflow.tansocore.model.plan;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Schema(description = "Data Transfer Object for Plan information")
public class PlanDto {
    @Schema(description = "Unique identifier of the plan")
    private UUID id;

    @Schema(description = "Unique key for the plan", example = "premium_monthly")
    private String key;

    @Schema(description = "Display name of the plan", example = "Premium Plan")
    private String name;

    @Schema(description = "Detailed description of the plan")
    private String description;

    @Schema(description = "Price amount in dollars", example = "29.00")
    private BigDecimal priceAmount;

    @Schema(description = "Billing interval in months", example = "1")
    private String intervalMonths;

    @Schema(description = "Billing timing", example = "advance")
    private String billingTiming;

    @Schema(description = "Plan status", example = "ACTIVE")
    private String status;

    @Schema(description = "Timestamp when the plan was created")
    private Instant createdAt;

    @Schema(description = "Timestamp when the plan was last modified")
    private Instant modifiedAt;

    @Schema(description = "Additional metadata associated with the plan")
    private Map<String, Object> metadata;
}
