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
package com.tansoflow.tansocore.model.spend;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * "What if this traffic had gone to another model": the same tokens priced at
 * the other model's rates. Advice only — Tanso never routes.
 */
@Getter
@Builder
public class SpendRouteSimulationDto {
    private LocalDate from;
    private LocalDate to;
    private String fromModel;
    private String toModel;
    private String workspaceId;
    private long uncachedInputTokens;
    private long cacheReadTokens;
    private long cacheCreationTokens;
    private long outputTokens;
    private Long requests;
    @Schema(description = "What the matched traffic cost by the price book.")
    private BigDecimal currentCents;
    @Schema(description = "The same tokens at the target model's rates.")
    private BigDecimal simulatedCents;
    @Schema(description = "simulated − current; negative is a saving.")
    private BigDecimal deltaCents;
    @Schema(description = "Things the number does not know: quality, latency, token-count drift between tokenizers, missing cache rates.")
    private List<String> caveats;
}
