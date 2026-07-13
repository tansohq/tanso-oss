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
package com.tansoflow.tansocore.model.credit.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Data
@Schema(description = "Request to deduct credits from a pool")
public class CreditDeductionRequest {
    @NotBlank
    @Schema(description = "Credit pool ID to deduct from")
    private String creditPoolId;

    @NotNull
    @Positive
    @Schema(description = "Amount of credits to deduct", example = "50")
    private BigDecimal amount;

    @Schema(description = "Subscription consuming the credits")
    private String subscriptionId;

    @Schema(description = "Customer consuming the credits")
    private String customerId;

    @Schema(description = "Usage event that triggered this deduction")
    private UUID eventId;

    @Schema(description = "Human-readable description")
    private String description;

    @Schema(description = "Idempotency key to prevent duplicate deductions")
    private String idempotencyKey;

    @Schema(description = "Additional metadata")
    private Map<String, Object> metadata;
}
