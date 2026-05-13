package com.tansoflow.tansocore.model.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Client-facing credit pool information")
public class ClientCreditPoolDto {
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

    @Schema(description = "Additional metadata")
    private Map<String, Object> metadata;

    @Schema(description = "Timestamp when the pool was created")
    private Instant createdAt;
}
