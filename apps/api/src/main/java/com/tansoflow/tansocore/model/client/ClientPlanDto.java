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
