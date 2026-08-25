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

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "account_api_keys")
@SQLRestriction("deleted_at IS NULL")
public class AccountApiKey {
    @Id
    @GeneratedValue
    @Column(name = "api_key_id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "account_id", updatable = false, nullable = false)
    private Account account;

    @Size(max = 50)
    @NotNull
    @Column(name = "key_type", nullable = false, length = 50)
    private String keyType;

    @NotNull
    @Column(name = "key_value", nullable = false, length = Integer.MAX_VALUE)
    private String keyValue;

    @Column(name = "key_hint", length = 50)
    private String keyHint;

    // Null for tenant (sk_) keys; set when the key is scoped to one customer (ck_)
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id", updatable = false)
    private Customer customer;

    // Comma-separated: "read", "purchase". Null = legacy tenant key, full tenant scope.
    @Size(max = 255)
    @Column(name = "scopes", length = 255)
    private String scopes;

    @ColumnDefault("false")
    @Column(name = "is_active")
    private Boolean isActive;

    @NotNull
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Setter(AccessLevel.NONE)
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Setter(AccessLevel.NONE)
    @Column(name = "modified_at", insertable = false)
    @UpdateTimestamp
    private Instant modifiedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    // ─── Per-key spend budget ─── null on either axis means unlimited.

    @Column(name = "budget_credits", precision = 18, scale = 4)
    private java.math.BigDecimal budgetCredits;

    @Column(name = "budget_amount", precision = 18, scale = 2)
    private java.math.BigDecimal budgetAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "budget_period", length = 16)
    private com.tansoflow.tansocore.model.apikey.type.BudgetPeriod budgetPeriod;

    @Column(name = "budget_started_at")
    private Instant budgetStartedAt;

    // Percent of the closest limit at which this key starts reporting itself as
    // near its ceiling. Null means it never does.
    @Column(name = "budget_alert_threshold")
    private Integer budgetAlertThreshold;

    // When the threshold was crossed. Compared against the current window, so a
    // stamp from a previous window reads as "not alerting" without a job to
    // clear it.
    @Column(name = "budget_alert_at")
    private Instant budgetAlertAt;

}