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
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "addresses")
@SQLRestriction("deleted_at IS NULL")
public class Address {
    @Id
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "address_id", nullable = false)
    private UUID id;

    @Size(max = 255)
    @Column(name = "street")
    private String street;

    @Size(max = 100)
    @Column(name = "city", length = 100)
    private String city;

    @Size(max = 100)
    @Column(name = "state", length = 100)
    private String state;

    @Size(max = 20)
    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Size(max = 100)
    @Column(name = "country", length = 100)
    private String country;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "modified_at", nullable = false)
    private Instant modifiedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

}