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
package com.tansoflow.tansocore.model.entitlement.api;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Request to check an entitlement and optionally attach tracking context for analytics.")
public class EntitlementEvaluationRequest {

    @NotBlank
    @Size(max = 128)
    @Schema(description = "External customer reference ID (scoped to tenant/account).", example = "cust_123")
    private String customerReferenceId;

    @NotBlank
    @Size(max = 128)
    @Schema(description = "Feature key to check.", example = "llm.generate")
    private String featureKey;

    @Valid
    @JsonAlias("track")
    @Schema(description = "Optional usage context for simulating whether proposed usage would be allowed. Does not record real usage.")
    private UsageContext usage;

    @Schema(description = "Optional correlation/debug context. Useful for joining logs/events.")
    private RequestContext context;
}
