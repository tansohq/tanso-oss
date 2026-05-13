package com.tansoflow.tansocore.model.subscription.request;

import com.tansoflow.tansocore.model.subscription.type.SubscriptionChangeType;
import lombok.Data;

@Data
public class ClientChangeSubscriptionRequest {
    private String changeToPlanId;
    private SubscriptionChangeType changeType;
}
