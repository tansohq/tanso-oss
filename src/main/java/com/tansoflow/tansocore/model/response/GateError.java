/*
 * Tanso Core - open-source B2B SaaS monetization engine
 * Copyright (C) 2026  Douglas Baek
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.tansoflow.tansocore.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * The gate envelope: an {@link Error} that also tells an agent which gate it
 * hit and what clears it. Every 402 and every access/limit 403 carries one.
 */
@Getter
@Slf4j
@Schema(description = "Error with a machine-readable gate and the action that clears it")
public class GateError extends Error {

    @Schema(description = "payment | budget | scope")
    private final String gate;
    @Schema(description = "complete_checkout | nominate_owner | wait | raise_spend_cap | raise_mandate | use_own_reference | request_scope")
    private final String action;
    @Schema(description = "URL to hand to a human (checkout page, owner endpoint, or the spend-mandate endpoint), or null", nullable = true)
    private final String url;
    @Schema(description = "URL to poll until the gate clears, or null", nullable = true)
    private final String poll;
    @JsonProperty("retry_after")
    @Schema(name = "retry_after", description = "Seconds until the budget window resets, or null", nullable = true)
    private final Long retryAfter;

    public GateError(ErrorCode code, String gate, String action, String url, String poll, Long retryAfter, String message) {
        super(code, message);
        this.gate = gate;
        this.action = action;
        this.url = url;
        this.poll = poll;
        this.retryAfter = retryAfter;
    }

    public static GateError paymentRequired(String checkoutUrl, String pollUrl) {
        return withErrorId(new GateError(ErrorCode.PAYMENT_REQUIRED, "payment", "complete_checkout", checkoutUrl, pollUrl, null,
                "Payment is required: hand url to a human to complete checkout, then poll for the outcome."));
    }

    public static GateError ownerEmailRequired(String ownerUrl) {
        return withErrorId(new GateError(ErrorCode.PAYMENT_REQUIRED, "payment", "nominate_owner", ownerUrl, null, null,
                "Stripe needs an email to send the invoice to; PUT {\"email\": ...} to url, then retry this call."));
    }

    public static GateError paymentRequiredNoProcessor() {
        return withErrorId(new GateError(ErrorCode.PAYMENT_REQUIRED, "payment", "complete_checkout", null, null, null,
                "Payment is required but no payment processor is connected to this instance; contact the operator."));
    }

    /**
     * The 402s are answered by controllers, not the exception handler, so nothing gave them the error id every gate
     * promises in detail. Logged, so the id an agent quotes can be found. The url is not logged: it is a payment page.
     */
    private static GateError withErrorId(GateError gate) {
        String errorId = UUID.randomUUID().toString();
        gate.setDetail("errorId=" + errorId);
        log.info("Payment gate {} answered [errorId={}]", gate.getAction(), errorId);
        return gate;
    }

    public static GateError budgetExceeded(Long retryAfterSeconds, String message) {
        return new GateError(ErrorCode.BUDGET_EXCEEDED, "budget", "wait", null, null, retryAfterSeconds, message);
    }

    public static GateError spendCapExceeded(String message) {
        return new GateError(ErrorCode.SPEND_CAP_EXCEEDED, "budget", "raise_spend_cap", null, null, null, message);
    }

    /** The charge alone is above the whole mandate; url opens a page for the principal to approve more. */
    public static GateError raiseMandate(String mandateUrl, String message) {
        return new GateError(ErrorCode.SPEND_CAP_EXCEEDED, "budget", "raise_mandate", mandateUrl, null, null, message);
    }

    public static GateError scopeDenied(String message) {
        return new GateError(ErrorCode.SCOPE_DENIED, "scope", "request_scope", null, null, null, message);
    }

    public static GateError otherCustomer() {
        return new GateError(ErrorCode.FORBIDDEN, "scope", "use_own_reference", null, null, null,
                "This API key belongs to another customer; use your own customerReferenceId or omit it.");
    }

    public static GateError endpointNotOpen() {
        return new GateError(ErrorCode.FORBIDDEN, "scope", "request_scope", null, null, null,
                "This endpoint is not open to this kind of key; use a tenant key or ask the account owner.");
    }
}
