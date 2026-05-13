package com.tansoflow.tansocore.model.entitlement.response;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CustomerEntitlementsResponse {
    private String referenceCustomerId;
    private List<SubscriptionEntitlements> subscriptions;

    @Data
    public static class SubscriptionEntitlements {
        private UUID subscriptionId;
        private List<EntitlementSummary> entitlements;
    }

    @Data
    public static class EntitlementSummary {
        private String featureKey;
        private boolean isAllowed;
    }
}
