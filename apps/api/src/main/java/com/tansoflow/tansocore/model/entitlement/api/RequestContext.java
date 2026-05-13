package com.tansoflow.tansocore.model.entitlement.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Correlation/debug context for joining requests across systems.")
public class RequestContext {

    @Size(max = 128)
    @Schema(description = "Caller-provided idempotency key.", example = "req_abc123")
    private String idempotencyKey;

    @Size(max = 128)
    @Schema(description = "Flow/correlation identifier.", example = "flow_chat_turn_42")
    private String flowId;
}
