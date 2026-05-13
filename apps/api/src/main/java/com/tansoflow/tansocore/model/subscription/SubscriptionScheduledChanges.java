package com.tansoflow.tansocore.model.subscription;

import lombok.Data;

@Data
public class SubscriptionScheduledChanges {
    private String changeType;
    private String changeStatus;
    private String effectiveAt;
}
