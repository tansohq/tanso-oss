package com.tansoflow.tansocore.integration.stripe;

import org.springframework.http.HttpHeaders;

public interface StripeWebhook {
    // TODO: Clean this up a bit
    void ingestWebhookRequest(String body, HttpHeaders headers, String accountId) throws Exception;
}
