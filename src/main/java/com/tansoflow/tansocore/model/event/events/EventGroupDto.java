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
package com.tansoflow.tansocore.model.event.events;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@AllArgsConstructor
@Schema(description = "Aggregated event group")
public class EventGroupDto {
    @Schema(description = "The value of the grouped field")
    private String groupKey;

    @Schema(description = "Display label for the group")
    private String groupLabel;

    @Schema(description = "Number of events in this group")
    private Long eventCount;

    @Schema(description = "Total cost across events in this group")
    private BigDecimal totalCost;

    @Schema(description = "Total revenue across events in this group")
    private BigDecimal totalRevenue;

    @Schema(description = "Total usage units across events in this group")
    private BigDecimal totalUsageUnits;

    @Schema(description = "Most recent event timestamp in this group")
    private Instant lastOccurredAt;
}
