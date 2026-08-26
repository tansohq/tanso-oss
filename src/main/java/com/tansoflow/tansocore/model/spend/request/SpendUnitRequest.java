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

import com.tansoflow.tansocore.model.spend.type.SpendUnitType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SpendUnitRequest {
    @NotNull
    private SpendUnitType type;
    @NotBlank
    @Size(max = 120)
    private String name;
    @Size(max = 255)
    @Schema(description = "For PERSON units: the address Claude Code reports them under.")
    private String email;
    @Size(max = 120)
    @Schema(description = "For PERSON units: their GitHub login, so merged pull requests attribute to them.")
    private String githubLogin;
    @Schema(description = "Unit this one rolls up into (a team for a person, a project for a team). Optional.")
    private String parentId;
}
