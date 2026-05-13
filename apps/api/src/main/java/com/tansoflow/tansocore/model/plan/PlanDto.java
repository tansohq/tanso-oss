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
