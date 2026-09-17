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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Agent signup funnel: signups -> first verified job -> claimed -> paid")
public class AgentFunnelResponse {
    private Period period;
    private Stages stages;
    private Rates rates;
    @JsonProperty("median_hours_signup_to_first_job")
    @Schema(description = "Median hours from signup to first usage event; null when no agent has sent one")
    private Double medianHoursSignupToFirstJob;
    @JsonProperty("median_hours_signup_to_paid")
    @Schema(description = "Median hours from signup to first PAID invoice or completed checkout; null when nobody paid")
    private Double medianHoursSignupToPaid;
    @Schema(description = "Signups in the period whose agent_status is EXPIRED")
    private long expired;
    @JsonProperty("by_day")
    private List<Day> byDay;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Period {
        private String from;
        private String to;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Stages {
        private long signups;
        @JsonProperty("first_verified_job")
        private long firstVerifiedJob;
        private long claimed;
        private long paid;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Each rate is a fraction of signups; 0 when there are no signups")
    public static class Rates {
        private double activation;
        private double claim;
        private double paid;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Counts keyed by signup date (UTC): how the cohort that signed up that day progressed")
    public static class Day {
        private String date;
        private long signups;
        @JsonProperty("first_verified_job")
        private long firstVerifiedJob;
        private long claimed;
        private long paid;
    }
}
