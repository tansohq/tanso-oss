package com.tansoflow.tansocore.model.billing.request;

import lombok.Data;

@Data
public class CheckoutRequest {
    private Payment payment;
    private String idempotencyKey; // TODO: not used yet but we should use it near future

    @Data
    public static class Payment {
        private String status;
    }

}
