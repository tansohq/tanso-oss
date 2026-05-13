package com.tansoflow.tansocore.model.billing.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Response containing the Stripe Checkout session URL")
public class StripeCheckoutSessionsResponse {
    @Schema(description = "The URL for the Stripe Checkout session", example = "https://checkout.stripe.com/pay/cs_test_...")
    private String url;
}
