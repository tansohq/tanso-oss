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
package com.tansoflow.tansocore.model.analytics;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Composite churn risk score with signal breakdown")
public class ChurnScoreDto {
    @Schema(description = "Composite churn risk score (0-100)")
    private Integer score;

    @Schema(description = "Risk label: low (0-25), moderate (26-50), high (51-75), critical (76-100)")
    private String riskLabel;

    @Schema(description = "Entitlement staleness score (0-100)")
    private Integer stalenessScore;

    @Schema(description = "Event frequency trend score (0-100)")
    private Integer eventTrendScore;

    @Schema(description = "Margin status score (0-100)")
    private Integer marginScore;

    @Schema(description = "Plan tier position score (0-100)")
    private Integer planTierScore;

    @Schema(description = "Feature utilization score (0-100)")
    private Integer utilizationScore;
}
