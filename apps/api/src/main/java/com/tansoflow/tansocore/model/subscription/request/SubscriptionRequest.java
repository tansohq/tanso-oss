package com.tansoflow.tansocore.model.subscription.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SubscriptionRequest {
    @NotBlank
    private String planId;

    @NotBlank
    private String customerId;

    private Integer gracePeriod;
}
