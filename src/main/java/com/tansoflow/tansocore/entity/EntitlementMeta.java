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
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "entitlement_metas")
@SQLRestriction("deleted_at IS NULL")
@SQLDelete(sql = "UPDATE entitlement_metas SET deleted_at = now() WHERE entitlement_meta_id = ?")
public class EntitlementMeta {
    @Id
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "entitlement_meta_id", nullable = false)
    private UUID id;

    @ColumnDefault("false")
    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "modified_at", nullable = false)
    private Instant modifiedAt;

}