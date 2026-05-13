package com.tansoflow.tansocore.model.credit;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Data
@Schema(description = "Data Transfer Object for Credit Pool information")
public class CreditPoolDto {
    @Schema(description = "Unique identifier of the credit pool")
    private String id;

    @Schema(description = "Human-readable pool name")
    private String name;

    @Schema(description = "Credit denomination (CREDITS, TOKENS, etc.)")
    private String denomination;

    @Schema(description = "ISO currency code when denomination is monetary")
    private String currency;

    @Schema(description = "Current available balance")
    private BigDecimal balance;

    @Schema(description = "Total credits ever granted")
    private BigDecimal totalGranted;

    @Schema(description = "Total credits consumed")
    private BigDecimal totalConsumed;

    @Schema(description = "Total credits expired")
    private BigDecimal totalExpired;

    @Schema(description = "Total credits reversed")
    private BigDecimal totalReversed;

    @Schema(description = "When true, zero balance blocks feature access")
    private Boolean hardLimit;

    @Schema(description = "Pool status: ACTIVE, FROZEN, DEPLETED, ARCHIVED")
    private String status;

    @Schema(description = "Rollover policy: NONE, FULL, CAPPED")
    private String rolloverPolicy;

    @Schema(description = "Max credits to roll over when policy is CAPPED")
    private BigDecimal rolloverCap;

    @Schema(description = "Customer ID if pool is customer-scoped")
    private String customerId;

    @Schema(description = "Additional metadata")
    private Map<String, Object> metadata;

    @Schema(description = "Timestamp when the pool was created")
    private Instant createdAt;

    @Schema(description = "Timestamp when the pool was last modified")
    private Instant modifiedAt;
}
