package com.tansoflow.tansocore.service.internal.monetization;

import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.Entitlement;
import com.tansoflow.tansocore.entity.Subscription;

public interface EntitlementService {
    Entitlement retrieveEntitlement(String referenceCustomerId, String accountUuid, String featureKey);

    void deleteEntitlementsBySubscription(Subscription subscription);

    void deleteEntitlementsBySubscriptionAndCustomer(Customer customer, Subscription subscription);

    void processEntitlementsForSubscription(Subscription subscription);

    void processEntitlementRevokeForSubscription(Subscription subscription);

    boolean isEntitled(String featureKey, Customer customer);
}
