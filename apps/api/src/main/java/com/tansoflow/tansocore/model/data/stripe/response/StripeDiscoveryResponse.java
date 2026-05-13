package com.tansoflow.tansocore.model.data.stripe.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class StripeDiscoveryResponse {
    private List<DiscoveredProduct> products;
    private List<DiscoveredCustomer> customers;
    private List<DiscoveredSubscription> subscriptions;

    @Data
    @Builder
    public static class DiscoveredProduct {
        private String stripeProductId;
        private String name;
        private String description;
        private boolean alreadyMapped;
    }

    @Data
    @Builder
    public static class DiscoveredCustomer {
        private String stripeCustomerId;
        private String name;
        private String email;
        private boolean alreadyMapped;
    }

    @Data
    @Builder
    public static class DiscoveredSubscription {
        private String stripeSubscriptionId;
        private String stripeCustomerId;
        private String stripeProductId;
        private String status;
        private boolean alreadyMapped;
    }
}
