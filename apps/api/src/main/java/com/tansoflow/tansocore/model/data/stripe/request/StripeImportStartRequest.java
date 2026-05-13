package com.tansoflow.tansocore.model.data.stripe.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class StripeImportStartRequest {
    @NotEmpty
    private List<ProductMapping> productMappings;
    private List<CustomerMapping> customerMappings;

    @Data
    public static class ProductMapping {
        private String stripeProductId;
        private UUID tansoPlanId;
    }

    @Data
    public static class CustomerMapping {
        private String stripeCustomerId;
        private UUID tansoCustomerId;
        private boolean autoCreate = true;
    }
}
