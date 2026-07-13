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

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Schema(description = "Usage context for simulating whether proposed usage would be allowed.")
public class UsageContext {

    @Size(max = 128)
    @Schema(description = "User-defined event name for what is being attempted.", example = "llm.generate")
    private String eventName;

    @Valid
    @Schema(description = "The amount of usage units to simulate (e.g., number of tokens, API calls). " +
            "Used to project whether the proposed usage would exceed the plan limit.",
            example = "1000.0")
    private BigDecimal usageUnits = BigDecimal.valueOf(1);

    @Schema(description = "Optional metadata (JSON object).")
    private Map<String, Object> meta;
}
