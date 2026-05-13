package com.tansoflow.tansocore.model.credit;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Schema(description = "Data Transfer Object for Plan Credit Allocation")
public class PlanCreditAllocationDto {
    @Schema(description = "Unique identifier of the allocation")
    private String id;

    @Schema(description = "Credit model ID")
    private String creditModelId;

    @Schema(description = "Credit model name")
    private String creditModelName;

    @Schema(description = "Credit denomination")
    private String denomination;

    @Schema(description = "Number of credits allocated per period")
    private BigDecimal creditAmount;

    @Schema(description = "Number of months before granted credits expire")
    private Integer grantExpiresMonths;

    @Schema(description = "Whether to block events when credits are depleted (true) or allow overage (false). Null inherits from credit model.")
    private Boolean hardLimit;

    @Schema(description = "Timestamp when the allocation was created")
    private Instant createdAt;
}
