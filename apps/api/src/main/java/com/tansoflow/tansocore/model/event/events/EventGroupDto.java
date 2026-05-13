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
