package com.tansoflow.tansocore.model.onboarding.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ExchangeTokenRequest {
    @NotNull
    private UUID userId;

    @NotNull
    private UUID accountId;

    @NotBlank
    private String email;

    // Optional fields for auto-repair (account replication if user doesn't exist in sandbox)
    private String firstName;
    private String lastName;
    private String organizationName;
    private String password;
}
