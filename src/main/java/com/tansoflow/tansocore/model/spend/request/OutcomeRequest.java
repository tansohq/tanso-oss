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

import com.tansoflow.tansocore.model.spend.type.OutcomeKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

/** What a CI job or script posts when something ships. */
@Data
public class OutcomeRequest {
    @NotNull
    private OutcomeKind kind;
    @NotBlank
    @Size(max = 255)
    @Schema(description = "Stable id in your system, e.g. a PR URL or ticket key. Posting the same id again updates the outcome.")
    private String externalId;
    @Size(max = 500)
    private String title;
    @Size(max = 1000)
    private String url;
    @Size(max = 255)
    @Schema(description = "Who did it, for attribution to a person (needs person level on).")
    private String actorEmail;
    @Size(max = 255)
    private String actorLogin;
    @Schema(description = "Unit to attribute to when no person matches. Optional.")
    private String spendUnitId;
    @Schema(description = "When it shipped. Default: now.")
    private Instant occurredAt;
    @Schema(description = "An AI assistant was in the work.")
    private Boolean aiAssisted;
    @Size(max = 64)
    @Schema(description = "Which one, if known: claude-code, copilot, cursor, codex, …")
    private String aiTool;
}
