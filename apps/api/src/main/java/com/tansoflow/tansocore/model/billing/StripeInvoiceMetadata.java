package com.tansoflow.tansocore.model.billing;

import lombok.Data;

@Data
public class StripeInvoiceMetadata {
    private String hostedInvoiceUrl;
    private String paymentIntentId;

}
