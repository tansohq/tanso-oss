package com.tansoflow.tansocore.model.data.stripe.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class StripeMapProductRequest {
    @NotBlank
    private String stripeProductId;
    @NotNull
    private UUID tansoPlanId;
}
