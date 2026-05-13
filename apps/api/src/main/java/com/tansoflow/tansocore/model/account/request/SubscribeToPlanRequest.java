package com.tansoflow.tansocore.model.account.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request to subscribe the account to a plan")
public class SubscribeToPlanRequest {
    @Schema(description = "The UUID of the plan to subscribe to", example = "550e8400-e29b-41d4-a716-446655440000")
    private String planId;
}
