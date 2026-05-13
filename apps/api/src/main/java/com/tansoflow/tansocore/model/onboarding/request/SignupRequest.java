package com.tansoflow.tansocore.model.onboarding.request;

import com.tansoflow.tansocore.model.customer.request.CustomerRequest;
import lombok.Data;

import java.util.UUID;

@Data
public class SignupRequest {
    private CustomerRequest customerDetails;
    private String organizationName;
    private String password;
    private String planId;

    // Only populated during cross-env replication to preserve UUIDs across environments
    private UUID accountId;
    private UUID userId;
}
