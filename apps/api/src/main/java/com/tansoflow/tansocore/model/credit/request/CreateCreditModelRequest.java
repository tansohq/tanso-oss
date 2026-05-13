package com.tansoflow.tansocore.model.credit.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Request to create a new credit model")
public class CreateCreditModelRequest {
    @NotBlank
    @Size(max = 150)
    @Schema(description = "Human-readable name", example = "API Credits")
    private String name;

    @NotBlank
    @Size(max = 32)
    @Schema(description = "Credit denomination", example = "api_credits")
    private String denomination;

    @Schema(description = "Description of the credit model")
    private String description;

    @Schema(description = "When true, zero balance blocks feature access", defaultValue = "true")
    private Boolean hardLimit;

    @Schema(description = "Rollover policy: NONE, FULL, CAPPED", defaultValue = "NONE")
    private String rolloverPolicy;

    @Schema(description = "Max credits to roll over when policy is CAPPED")
    private BigDecimal rolloverCap;
}
