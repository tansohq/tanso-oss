package com.tansoflow.tansocore.model.data.stripe.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Response containing masked Stripe API keys for an account")
public class StripeApiKeysResponse {
    @Schema(description = "The masked Stripe API key", example = "sk_test_...5v3")
    private String stripeApiKey;

    @Schema(description = "The masked Stripe webhook secret", example = "whsec_...9x2")
    private String webhookSecret;
}
