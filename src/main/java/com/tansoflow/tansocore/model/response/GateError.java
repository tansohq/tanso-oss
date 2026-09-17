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

/**
 * The gate envelope: an {@link Error} that also tells an agent which gate it
 * hit and what clears it. Every 402 and every access/limit 403 carries one.
 */
@Getter
@Schema(description = "Error with a machine-readable gate and the action that clears it")
public class GateError extends Error {

    @Schema(description = "payment | budget | claim | scope")
    private final String gate;
    @Schema(description = "complete_checkout | wait | claim_account | request_scope | raise_spend_cap")
    private final String action;
    @Schema(description = "Checkout URL to hand to a human, or null", nullable = true)
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
        return new GateError(ErrorCode.PAYMENT_REQUIRED, "payment", "complete_checkout", checkoutUrl, pollUrl, null,
                "Payment is required: hand url to a human to complete checkout, then poll for the outcome.");
    }

    public static GateError paymentRequiredNoProcessor() {
        return new GateError(ErrorCode.PAYMENT_REQUIRED, "payment", "complete_checkout", null, null, null,
                "Payment is required but no payment processor is connected to this instance; contact the operator.");
    }

    public static GateError budgetExceeded(Long retryAfterSeconds, String message) {
        return new GateError(ErrorCode.BUDGET_EXCEEDED, "budget", "wait", null, null, retryAfterSeconds, message);
    }

    public static GateError spendCapExceeded(String message) {
        return new GateError(ErrorCode.SPEND_CAP_EXCEEDED, "budget", "raise_spend_cap", null, null, null, message);
    }

    public static GateError claimRequired(String statusUrl) {
        return new GateError(ErrorCode.CLAIM_REQUIRED, "claim", "claim_account", null, statusUrl, null,
                "This account is provisional; paying for a plan or credits claims it and unlocks this operation.");
    }

    public static GateError scopeDenied(String message) {
        return new GateError(ErrorCode.SCOPE_DENIED, "scope", "request_scope", null, null, null, message);
    }
}
