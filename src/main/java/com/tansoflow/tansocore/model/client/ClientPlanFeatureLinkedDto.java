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
package com.tansoflow.tansocore.model.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tansoflow.tansocore.model.credit.PlanCreditAllocationDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Client-facing plan with its linked features and pricing details")
public class ClientPlanFeatureLinkedDto {
    @Schema(description = "Plan details")
    private ClientPlanDto plan;

    @Schema(description = "List of features linked to the plan with pricing information")
    private List<ClientFeatureDto> features;

    @Schema(description = "Credit allocations included with this plan")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<PlanCreditAllocationDto> creditAllocations;
}
