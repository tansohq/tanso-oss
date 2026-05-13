package com.tansoflow.tansocore.model.credit.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Data
@Schema(description = "Request to deduct credits from a pool")
public class CreditDeductionRequest {
    @NotBlank
    @Schema(description = "Credit pool ID to deduct from")
    private String creditPoolId;

    @NotNull
    @Positive
    @Schema(description = "Amount of credits to deduct", example = "50")
    private BigDecimal amount;

    @Schema(description = "Subscription consuming the credits")
    private String subscriptionId;

    @Schema(description = "Customer consuming the credits")
    private String customerId;

    @Schema(description = "Usage event that triggered this deduction")
    private UUID eventId;

    @Schema(description = "Human-readable description")
    private String description;

    @Schema(description = "Idempotency key to prevent duplicate deductions")
    private String idempotencyKey;

    @Schema(description = "Additional metadata")
    private Map<String, Object> metadata;
}
