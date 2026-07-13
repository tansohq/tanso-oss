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
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "subscriptions")
@SQLRestriction("deleted_at IS NULL")
@SQLDelete(sql = "UPDATE subscriptions SET deleted_at = now() WHERE subscription_id = ?")
public class Subscription {
    @Id
    @GeneratedValue
    @Column(name = "subscription_id", nullable = false, updatable = false)
    private UUID id;

    @Setter(AccessLevel.NONE)
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Setter(AccessLevel.NONE)
    @Column(name = "modified_at", insertable = false)
    @UpdateTimestamp
    private Instant modifiedAt;

    @ColumnDefault("false")
    @Column(name = "is_active")
    private Boolean isActive = false;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", updatable = false, nullable = false)
    private Account account;

    @ColumnDefault("3")
    @Column(name = "grace_period_days")
    private Integer gracePeriodDays = 3;

    @Column(name = "interval_months")
    private Integer intervalMonths;

    @Column(name = "current_period_start")
    private Instant currentPeriodStart;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Size(max = 16)
    @Column(name = "cancel_mode", length = 16)
    private String cancelMode;

    @Column(name = "cancel_effective_at")
    private Instant cancelEffectiveAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "billing_anchor_day")
    private Short billingAnchorDay;

}