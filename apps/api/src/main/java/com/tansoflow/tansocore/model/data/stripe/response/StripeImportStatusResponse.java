package com.tansoflow.tansocore.model.data.stripe.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class StripeImportStatusResponse {
    private UUID jobId;
    private String status;
    private int totalItems;
    private int processedItems;
    private int failedItems;
    private String errorDetails;
    private Instant createdAt;
    private Instant updatedAt;
}
