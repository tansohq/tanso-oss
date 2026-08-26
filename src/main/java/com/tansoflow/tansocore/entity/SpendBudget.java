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

import com.tansoflow.tansocore.model.spend.type.BudgetMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Two clocks on one unit: a small daily ceiling to catch a runaway agent, a monthly one for the real budget. Calendar-aligned, UTC. */
@Getter
@Setter
@Entity
@Table(name = "spend_budgets")
public class SpendBudget {
    @Id
    @GeneratedValue
    @Column(name = "spend_budget_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "spend_unit_id", nullable = false, updatable = false)
    private UUID spendUnitId;

    @Column(name = "daily_cents", precision = 18, scale = 2)
    private BigDecimal dailyCents;

    @Column(name = "monthly_cents", precision = 18, scale = 2)
    private BigDecimal monthlyCents;

    @Column(name = "alert_threshold", nullable = false)
    private int alertThreshold = 80;

    @Enumerated(EnumType.STRING)
    @Column(name = "monthly_mode", nullable = false, length = 16)
    private BudgetMode monthlyMode = BudgetMode.ALERT;

    /** A temporary lift of the monthly ceiling: applies while now < bumpExpiresAt, then the standing ceiling is back. */
    @Column(name = "bump_monthly_cents", precision = 18, scale = 2)
    private BigDecimal bumpMonthlyCents;

    @Column(name = "bump_expires_at")
    private Instant bumpExpiresAt;

    @Column(name = "bump_reason", length = 255)
    private String bumpReason;

    public boolean bumpActive(Instant now) {
        return bumpMonthlyCents != null && bumpExpiresAt != null && now.isBefore(bumpExpiresAt);
    }

    public BigDecimal effectiveMonthlyCents(Instant now) {
        return bumpActive(now) ? bumpMonthlyCents : monthlyCents;
    }

    /** Where a Block budget was pushed as a hard limit ("litellm:team:backend"), when, or why it could not be. */
    @Column(name = "enforced_at")
    private Instant enforcedAt;

    @Column(name = "enforcement_target", length = 255)
    private String enforcementTarget;

    @Column(name = "enforcement_error", length = Integer.MAX_VALUE)
    private String enforcementError;

    @Setter(AccessLevel.NONE)
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Setter(AccessLevel.NONE)
    @UpdateTimestamp
    @Column(name = "modified_at", insertable = false)
    private Instant modifiedAt;
}
