package com.tansoflow.tansocore.integration.stripe;

import com.tansoflow.tansocore.model.data.stripe.request.StripeDiscoverRequest;
import com.tansoflow.tansocore.model.data.stripe.request.StripeImportStartRequest;
import com.tansoflow.tansocore.model.data.stripe.request.StripeMapProductRequest;
import com.tansoflow.tansocore.model.data.stripe.response.StripeDiscoveryResponse;
import com.tansoflow.tansocore.model.data.stripe.response.StripeImportStatusResponse;

import java.util.UUID;

public interface StripeImportService {
    StripeDiscoveryResponse discover(UUID accountId, StripeDiscoverRequest request);

    StripeImportStatusResponse startImport(UUID accountId, StripeImportStartRequest request);

    StripeImportStatusResponse getImportStatus(UUID accountId, UUID jobId);

    void mapProduct(UUID accountId, StripeMapProductRequest request);

    StripeImportStatusResponse startAutoCreateImport(UUID accountId);
}
