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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "plans")
@SQLRestriction("deleted_at IS NULL")
@SQLDelete(sql = "UPDATE plans SET deleted_at = now() WHERE plan_id = ?")
public class Plan {
    @Id
    @GeneratedValue
    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID id;

    @Size(max = 100)
    @NotNull
    @Column(name = "key", nullable = false, length = 100)
    private String key;

    @Size(max = 150)
    @NotNull
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "price_amount", precision = 18, scale = 2)
    private BigDecimal priceAmount;

    @Column(name = "description", length = Integer.MAX_VALUE)
    private String description;

    @Setter(AccessLevel.NONE)
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Setter(AccessLevel.NONE)
    @Column(name = "modified_at", insertable = false)
    @UpdateTimestamp
    private Instant modifiedAt;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "account_id", updatable = false, nullable = false)
    private Account account;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "interval_months")
    private Integer intervalMonths;

    @Size(max = 64)
    @Column(name = "billing_timing", length = 64)
    private String billingTiming;

    @Size(max = 3)
    @ColumnDefault("'USD'")
    @Column(name = "currency", length = 3)
    private String currency = "USD";

    @NotNull
    @Size(max = 20)
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @NotNull
    @ColumnDefault("'{}'")
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false)
    private Map<String, Object> metadata = new HashMap<>();

}