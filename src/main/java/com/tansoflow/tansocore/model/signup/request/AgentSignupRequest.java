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
package com.tansoflow.tansocore.model.signup.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AgentSignupRequest {
    @Email
    @Size(max = 255)
    @Schema(description = "Optional contact email of the agent's principal. Recorded as the owner; nothing is sent to it. "
            + "Can be set later via PUT /api/v1/client/customers/{ref}/owner.")
    private String email;

    @Size(max = 100)
    @Schema(description = "Optional display name for the customer record")
    private String name;

    @Valid
    @JsonProperty("spend_mandate")
    @Schema(description = "Optional. Ask for a saved card up front so later purchases inside max_amount need no human. "
            + "Only honored when the operator enabled agentSpendMandateEnabled.")
    private SpendMandate spendMandate;

    @Data
    public static class SpendMandate {
        @NotNull
        @DecimalMin(value = "0.01")
        @JsonProperty("max_amount")
        private BigDecimal maxAmount;

        @Pattern(regexp = "^[A-Za-z]{3}$")
        @Schema(description = "ISO 4217, defaults to the account currency")
        private String currency;

        @Pattern(regexp = "^(day|week|month)$")
        @Schema(description = "Rolling window the cap applies to. Defaults to month.")
        private String period;
    }
}
