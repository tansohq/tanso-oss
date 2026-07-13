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
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Schema(description = "Request to create a new credit pool")
public class CreateCreditPoolRequest {
    @NotBlank
    @Size(max = 150)
    @Schema(description = "Human-readable pool name", example = "API Credits")
    private String name;

    @NotBlank
    @Size(max = 32)
    @Schema(description = "Credit denomination", example = "CREDITS")
    private String denomination;

    @Size(max = 3)
    @Schema(description = "ISO currency code when denomination is monetary", example = "USD")
    private String currency;

    @Schema(description = "Customer ID for customer-scoped pools")
    private String customerId;

    @Schema(description = "When true, zero balance blocks feature access", defaultValue = "false")
    private Boolean hardLimit;

    @Schema(description = "Rollover policy: NONE, FULL, CAPPED", defaultValue = "NONE")
    private String rolloverPolicy;

    @Schema(description = "Max credits to roll over when policy is CAPPED")
    private BigDecimal rolloverCap;

    @Schema(description = "Additional metadata")
    private Map<String, Object> metadata;
}
