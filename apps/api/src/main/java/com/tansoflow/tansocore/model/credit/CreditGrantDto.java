package com.tansoflow.tansocore.model.credit;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Data
@Schema(description = "Data Transfer Object for Credit Grant information")
public class CreditGrantDto {
    @Schema(description = "Unique identifier of the grant")
    private String id;

    @Schema(description = "Credit pool this grant belongs to")
    private String creditPoolId;

    @Schema(description = "Subscription that triggered this grant, if any")
    private String subscriptionId;

    @Schema(description = "Invoice linked to this grant, if purchased")
    private String invoiceId;

    @Schema(description = "Grant type: PLAN_INCLUDED, PURCHASED, PROMOTIONAL, etc.")
    private String grantType;

    @Schema(description = "Original grant amount")
    private BigDecimal amount;

    @Schema(description = "Remaining credits from this grant")
    private BigDecimal remaining;

    @Schema(description = "When this grant expires")
    private Instant expiresAt;

    @Schema(description = "Human-readable description")
    private String description;

    @Schema(description = "When this grant was voided")
    private Instant voidedAt;

    @Schema(description = "Additional metadata")
    private Map<String, Object> metadata;

    @Schema(description = "Timestamp when the grant was created")
    private Instant createdAt;
}
