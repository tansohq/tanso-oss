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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "subscription_scheduled_changes")
public class SubscriptionScheduledChange {
    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Size(max = 32)
    @NotNull
    @Column(name = "type", nullable = false, length = 32)
    private String type;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_plan_id", nullable = false)
    private Plan fromPlan;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_plan_id", nullable = false)
    private Plan toPlan;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adjustment_invoice_id")
    private Invoice adjustmentInvoice;

    @Size(max = 32)
    @NotNull
    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @NotNull
    @Column(name = "effective_at", nullable = false)
    private Instant effectiveAt;

    @Setter(AccessLevel.NONE)
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Setter(AccessLevel.NONE)
    @Column(name = "modified_at", insertable = false)
    @UpdateTimestamp
    private Instant modifiedAt;

    @Column(name = "fulfilled_at")
    private Instant fulfilledAt;

}