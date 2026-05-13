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
