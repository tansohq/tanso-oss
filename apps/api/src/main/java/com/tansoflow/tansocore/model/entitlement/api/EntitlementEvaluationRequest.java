package com.tansoflow.tansocore.model.entitlement.api;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Request to check an entitlement and optionally attach tracking context for analytics.")
public class EntitlementEvaluationRequest {

    @NotBlank
    @Size(max = 128)
    @Schema(description = "External customer reference ID (scoped to tenant/account).", example = "cust_123")
    private String customerReferenceId;

    @NotBlank
    @Size(max = 128)
    @Schema(description = "Feature key to check.", example = "llm.generate")
    private String featureKey;

    @Valid
    @JsonAlias("track")
    @Schema(description = "Optional usage context for simulating whether proposed usage would be allowed. Does not record real usage.")
    private UsageContext usage;

    @Schema(description = "Optional correlation/debug context. Useful for joining logs/events.")
    private RequestContext context;
}
