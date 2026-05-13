package com.tansoflow.tansocore.model.data.stripe;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StripeCustomerDto {
    private String stripeCustomerId;
    private UUID customerId;
    private UUID accountId;
}
