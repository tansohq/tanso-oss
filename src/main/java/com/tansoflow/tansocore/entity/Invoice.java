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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "invoices")
@SQLRestriction("deleted_at IS NULL")
@SQLDelete(sql = "UPDATE invoices SET deleted_at = now() WHERE invoice_id = ?")
public class Invoice {
    @Id
    @GeneratedValue
    @Column(name = "invoice_id", nullable = false, updatable = false)
    private UUID id;

    @Setter(AccessLevel.NONE)
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Setter(AccessLevel.NONE)
    @Column(name = "modified_at", insertable = false)
    @UpdateTimestamp
    private Instant modifiedAt;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    @NotFound(action = NotFoundAction.IGNORE)
    private Subscription subscription;

    @NotNull
    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @NotNull
    @Column(name = "due_date", nullable = false)
    private Instant dueDate;

    @Size(max = 50)
    @NotNull
    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Size(max = 3)
    @NotNull
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", updatable = false, nullable = false)
    @NotFound(action = NotFoundAction.IGNORE)
    private Account account;

    @Column(name = "invoice_period_start")
    private Instant invoicePeriodStart;

    @Column(name = "invoice_period_end")
    private Instant invoicePeriodEnd;

    @Size(max = 32)
    @Column(name = "type", length = 32)
    private String type;

}