package com.tansoflow.tansocore.model.account.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EnvironmentTokenRequest {
    @NotBlank
    private String targetEnvironment;
}
