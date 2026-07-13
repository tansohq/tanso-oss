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
package com.tansoflow.tansocore.model.subscription;

import com.tansoflow.tansocore.model.plan.PlanDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Schema(description = "Data Transfer Object for Subscription Scheduled Change information")
public class SubscriptionScheduledChangeDto {
    @Schema(description = "Unique identifier of the scheduled change")
    private UUID id;

    @Schema(description = "Type of scheduled change (e.g., UPGRADE, DOWNGRADE)")
    private String type;

    @Schema(description = "ID of the subscription this change applies to")
    private UUID subscriptionId;

    @Schema(description = "Current plan details")
    private PlanDto fromPlan;

    @Schema(description = "Target plan details")
    private PlanDto toPlan;

    @Schema(description = "Status of the scheduled change")
    private String status;

    @Schema(description = "Timestamp when the change becomes effective")
    private Instant effectiveAt;

    @Schema(description = "Timestamp when the change was created")
    private Instant createdAt;
}
