package com.tansoflow.tansocore.model.credit;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Data
@Schema(description = "Data Transfer Object for Credit Model information")
public class CreditModelDto {
    @Schema(description = "Unique identifier of the credit model")
    private String id;

    @Schema(description = "Human-readable name")
    private String name;

    @Schema(description = "Credit denomination (e.g., api_credits, tokens)")
    private String denomination;

    @Schema(description = "Description of the credit model")
    private String description;

    @Schema(description = "When true, zero balance blocks feature access")
    private Boolean hardLimit;

    @Schema(description = "Rollover policy: NONE, FULL, CAPPED")
    private String rolloverPolicy;

    @Schema(description = "Max credits to roll over when policy is CAPPED")
    private BigDecimal rolloverCap;

    @Schema(description = "Additional metadata")
    private Map<String, Object> metadata;

    @Schema(description = "Timestamp when the model was created")
    private Instant createdAt;

    @Schema(description = "Timestamp when the model was last modified")
    private Instant modifiedAt;
}
