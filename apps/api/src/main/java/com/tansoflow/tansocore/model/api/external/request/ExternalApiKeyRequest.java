package com.tansoflow.tansocore.model.api.external.request;

import lombok.Data;

@Data
public class ExternalApiKeyRequest {
    private String keyValue;
    private String keyType;
    private String externalApiEntityName;
    private String stripeCheckoutSuccessUrl;
    private String stripeCheckoutCancelUrl;

}
