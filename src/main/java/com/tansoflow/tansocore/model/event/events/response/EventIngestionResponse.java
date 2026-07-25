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
package com.tansoflow.tansocore.model.event.events.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventIngestionResponse {
    private Boolean usageLimitExceeded;
    private String message;

    /** Credits burned for this event across all pools. Absent when no credit model applies. */
    private java.math.BigDecimal creditsDeducted;
    /** Credits-per-unit weight the server applied (usageUnits × weight = credit charge). */
    private java.math.BigDecimal weightApplied;
    /** Tariff row the weight came from. Absent when the identity default applied. */
    private String weightId;
    /** Which tariff tier matched: MODEL, FEATURE_DEFAULT, or NONE (identity 1.0). */
    private String weightMatch;
    /** Remaining balance across the pools this subscription draws from, after deduction. */
    private java.math.BigDecimal remainingBalance;
}
