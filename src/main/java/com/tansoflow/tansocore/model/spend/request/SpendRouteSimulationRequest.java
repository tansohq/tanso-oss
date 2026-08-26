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
package com.tansoflow.tansocore.model.spend.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class SpendRouteSimulationRequest {
    @Schema(description = "Inclusive; default 30 days back.")
    private LocalDate from;
    @Schema(description = "Exclusive; default tomorrow.")
    private LocalDate to;
    @NotBlank
    @Schema(description = "The model whose traffic to re-price, as it appears on the usage report.")
    private String fromModel;
    @NotBlank
    @Schema(description = "A model in the price book.")
    private String toModel;
    @Schema(description = "Only traffic from this vendor workspace / project / team id. Null = all.")
    private String workspaceId;
}
