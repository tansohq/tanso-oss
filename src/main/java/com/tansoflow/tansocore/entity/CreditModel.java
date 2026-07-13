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
@Table(name = "credit_models")
@SQLRestriction("deleted_at IS NULL")
@SQLDelete(sql = "UPDATE credit_models SET deleted_at = now() WHERE id = ?")
public class CreditModel {
    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", updatable = false, nullable = false)
    private Account account;

    @NotNull
    @Size(max = 150)
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @NotNull
    @Size(max = 32)
    @Column(name = "denomination", nullable = false, length = 32)
    private String denomination;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @NotNull
    @ColumnDefault("true")
    @Column(name = "hard_limit", nullable = false)
    private Boolean hardLimit = true;

    @NotNull
    @Size(max = 20)
    @ColumnDefault("'NONE'")
    @Column(name = "rollover_policy", nullable = false, length = 20)
    private String rolloverPolicy = "NONE";

    @Column(name = "rollover_cap", precision = 18, scale = 4)
    private BigDecimal rolloverCap;

    @NotNull
    @ColumnDefault("'{}'")
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false)
    private Map<String, Object> metadata = new HashMap<>();

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
}
