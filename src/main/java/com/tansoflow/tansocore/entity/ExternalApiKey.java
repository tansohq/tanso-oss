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
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "external_api_keys")
public class ExternalApiKey {
    @Id
    @GeneratedValue
    @Column(name = "external_api_key_id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(name = "account_id", updatable = false, nullable = false)
    private UUID account;

    @Size(max = 100)
    @NotNull
    @Column(name = "external_api_entity_name", nullable = false, length = 100)
    private String externalApiEntityName;

    @Size(max = 50)
    @NotNull
    @Column(name = "key_type", nullable = false, length = 50)
    private String keyType;

    @NotNull
    @Column(name = "key_value", nullable = false, length = Integer.MAX_VALUE)
    private String keyValue;

    @ColumnDefault("true")
    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "expires_at")
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

}