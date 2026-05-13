package com.tansoflow.tansocore.service.client;
import com.tansoflow.tansocore.model.entitlement.api.EntitlementEvaluationRequest;
import com.tansoflow.tansocore.model.entitlement.response.CustomerEntitlementsResponse;
import com.tansoflow.tansocore.model.entitlement.response.EntitlementResponse;

public interface ClientEntitlementService {
    EntitlementResponse checkEntitlement(String referenceCustomerId, String accountUuid, String featureKey);

    EntitlementResponse checkEntitlement(String referenceCustomerId, String accountUuid, String featureKey, boolean recordEvent);

    EntitlementResponse evaluateEntitlement(String accountUuid, EntitlementEvaluationRequest request);

    CustomerEntitlementsResponse getCustomerEntitlements(String referenceCustomerId, String accountUuid);
}
