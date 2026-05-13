package com.tansoflow.tansocore.controller.tanso.data.stripe;

import com.tansoflow.tansocore.integration.stripe.StripeWebhook;
import com.tansoflow.tansocore.model.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/public")
@Tag(name = "Stripe Ingest", description = "Public endpoints for ingesting data from Stripe webhooks")
public class StripeIngestController {
    private final StripeWebhook stripeWebhook;

    // TODO: exposed ingest webhook
    @PostMapping("/stripe/ingest/webhook/{accountId}")
    @Operation(summary = "Ingest Stripe webhook", description = "Receives and processes webhook events from Stripe for a specific account")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Successfully ingested webhook request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad request or invalid signature", content = @Content)})
    public ResponseEntity<ApiResponse<Void>> postIngest(@RequestBody String body, @RequestHeader HttpHeaders headers, @PathVariable String accountId) {
        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder().build();
        try {
            stripeWebhook.ingestWebhookRequest(body, headers, accountId);

            apiResponse.setSuccess(true);

            return ResponseEntity.status(HttpStatus.CREATED).body(apiResponse);
        } catch (Exception e) {
            log.error("Webhook processing failed for accountId={}: {}", accountId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiResponse);
        }
    }
}

