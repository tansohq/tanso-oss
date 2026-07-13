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

import com.tansoflow.tansocore.model.customer.CustomerDto;
import com.tansoflow.tansocore.model.plan.PlanDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Schema(description = "Data Transfer Object for Subscription information")
public class SubscriptionDto {
    @Schema(description = "Unique identifier of the subscription")
    private String id;

    @Schema(description = "Status indicating if the subscription is active")
    private Boolean isActive;

    @Schema(description = "Billing interval in months", example = "1")
    private String intervalMonths;

    @Schema(description = "Associated customer details")
    private CustomerDto customer;

    @Schema(description = "Associated plan details")
    private PlanDto plan;

    @Schema(description = "Number of grace period days for payment", example = "3")
    private Integer gracePeriodDays;

    @Schema(description = "Timestamp when the current period started")
    private Instant currentPeriodStart;

    @Schema(description = "Timestamp when the current period ends")
    private Instant currentPeriodEnd;

    @Schema(description = "Cancellation mode (e.g., IMMEDIATELY, END_OF_PERIOD)")
    private String cancelMode;

    @Schema(description = "Timestamp when the cancellation becomes effective")
    private Instant cancelEffectiveAt;

    @Schema(description = "Timestamp when the subscription was cancelled")
    private Instant cancelledAt;

    @Schema(description = "Billing anchor day of the month", example = "1")
    private Short billingAnchorDay;

    @Schema(description = "Additional metadata associated with the subscription")
    private Map<String, List<Object>> metadata;

    @Schema(description = "Scheduled changes for the subscription")
    private SubscriptionScheduledChangeDto scheduledChange;
}
