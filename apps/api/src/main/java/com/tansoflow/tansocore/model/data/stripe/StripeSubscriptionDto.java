package com.tansoflow.tansocore.model.data.stripe;

import lombok.Data;

import java.time.Instant;

@Data
public class StripeSubscriptionDto {
    private String id;
    private Instant createdAt;
    private Instant modifiedAt;
    private String stripePriceId;
    private String stripeSubscriptionExternalId;
    private String subscriptionId;

}
