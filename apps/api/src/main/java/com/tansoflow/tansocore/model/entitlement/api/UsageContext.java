package com.tansoflow.tansocore.model.entitlement.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Schema(description = "Usage context for simulating whether proposed usage would be allowed.")
public class UsageContext {

    @Size(max = 128)
    @Schema(description = "User-defined event name for what is being attempted.", example = "llm.generate")
    private String eventName;

    @Valid
    @Schema(description = "The amount of usage units to simulate (e.g., number of tokens, API calls). " +
            "Used to project whether the proposed usage would exceed the plan limit.",
            example = "1000.0")
    private BigDecimal usageUnits = BigDecimal.valueOf(1);

    @Schema(description = "Optional metadata (JSON object).")
    private Map<String, Object> meta;
}
