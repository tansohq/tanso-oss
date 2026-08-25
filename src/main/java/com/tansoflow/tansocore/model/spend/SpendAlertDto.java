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

import com.tansoflow.tansocore.model.apikey.type.BudgetPeriod;
import com.tansoflow.tansocore.model.spend.type.SpendAlertKind;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
public class SpendAlertDto {
    private String id;
    private String spendUnitId;
    private String unitName;
    private SpendAlertKind kind;
    private BudgetPeriod period;
    private Instant windowStart;
    private BigDecimal spentCents;
    private BigDecimal limitCents;
    private String message;
    private Instant firedAt;
    private Instant ackedAt;
    private String ackedBy;
}
