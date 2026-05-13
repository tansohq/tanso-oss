package com.tansoflow.tansocore.model.data.stripe.request;

import lombok.Data;

@Data
public class StripeDiscoverRequest {
    private boolean includeProducts = true;
    private boolean includeCustomers = true;
    private boolean includeSubscriptions = true;
}
