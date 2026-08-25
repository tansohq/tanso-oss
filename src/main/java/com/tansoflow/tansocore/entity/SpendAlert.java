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
package com.tansoflow.tansocore.entity;

import com.tansoflow.tansocore.model.apikey.type.BudgetPeriod;
import com.tansoflow.tansocore.model.spend.type.SpendAlertKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Something a budget said, once per (unit, kind, window), and whether anyone acknowledged it. */
@Getter
@Setter
@Entity
@Table(name = "spend_alerts")
public class SpendAlert {
    @Id
    @GeneratedValue
    @Column(name = "spend_alert_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "spend_unit_id", nullable = false, updatable = false)
    private UUID spendUnitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private SpendAlertKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "period", length = 16)
    private BudgetPeriod period;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "spent_cents", nullable = false, precision = 18, scale = 2)
    private BigDecimal spentCents;

    @Column(name = "limit_cents", precision = 18, scale = 2)
    private BigDecimal limitCents;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Column(name = "fired_at", nullable = false, updatable = false)
    private Instant firedAt;

    @Column(name = "acked_at")
    private Instant ackedAt;

    @Column(name = "acked_by", length = 255)
    private String ackedBy;
}
