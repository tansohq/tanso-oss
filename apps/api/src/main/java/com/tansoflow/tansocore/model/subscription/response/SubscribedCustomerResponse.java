package com.tansoflow.tansocore.model.subscription.response;

import com.tansoflow.tansocore.model.billing.InvoiceDto;
import com.tansoflow.tansocore.model.subscription.SubscriptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "Response after a customer has successfully subscribed")
public class SubscribedCustomerResponse {
    @Schema(description = "Details of the created subscription")
    private SubscriptionDto subscription;

    @Schema(description = "Details of the first invoice generated")
    private InvoiceDto invoice;

    @Schema(description = "Additional metadata related to the subscription process")
    private Map<String, Object> metadata;

    @Schema(description = "Stripe Checkout URL for payment (IN_ADVANCE plans in STRIPE_INTEGRATION mode)")
    private String checkoutUrl;
}
