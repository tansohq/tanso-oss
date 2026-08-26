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

/** The weekly digest: last seven full UTC days against the seven before, per unit, with budget standing. */
@Getter
@Builder
public class SpendDigestDto {
    private LocalDate from;
    @Schema(description = "Exclusive.")
    private LocalDate to;
    private BigDecimal totalCents;
    private BigDecimal previousTotalCents;
    private BigDecimal unattributedCents;
    private int alertsFired;
    private List<DigestRow> rows;
    @Schema(description = "Set by POST /digest/send: what happened on each channel.")
    private DeliveryDto delivery;

    @Getter
    @Builder
    public static class DeliveryDto {
        @Schema(description = "SENT, FAILED or NOT_CONFIGURED")
        private String slack;
        private String webhook;
        private String email;
    }

    @Getter
    @Builder
    public static class DigestRow {
        private String unitId;
        private String name;
        private BigDecimal cents;
        private BigDecimal previousCents;
        @Schema(description = "Month-to-date against the effective monthly ceiling; null when the unit has no monthly budget.")
        private BigDecimal monthlySpentCents;
        private BigDecimal monthlyLimitCents;
        private String bumpReason;
    }
}
