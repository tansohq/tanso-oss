package com.tansoflow.tansocore.model.subscription.request;

import lombok.Data;

@Data
public class ClientSubscriptionRequest {
    private String planId;

    private String customerReferenceId;

    private Integer gracePeriod;
}
