package com.tansoflow.tansocore.model.onboarding.request;

import lombok.Data;

@Data
public class ReplicateSubscriptionRequest {
    private String accountId;  // The account's external customer ID (account UUID)
    private String planKey;    // Plan key to match across environments
}
