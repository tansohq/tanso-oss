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

import com.tansoflow.tansocore.model.spend.type.AttributionMatchKind;
import com.tansoflow.tansocore.model.spend.type.VendorProvider;
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

import java.time.Instant;
import java.util.UUID;

/** "Usage on this vendor dimension belongs to that unit." Lower priority number wins when several match. */
@Getter
@Setter
@Entity
@Table(name = "spend_attribution_rules")
public class SpendAttributionRule {
    @Id
    @GeneratedValue
    @Column(name = "spend_attribution_rule_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "spend_unit_id", nullable = false)
    private UUID spendUnitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private VendorProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_kind", nullable = false, length = 16)
    private AttributionMatchKind matchKind;

    @Column(name = "match_value", nullable = false, length = 255)
    private String matchValue;

    @Column(name = "priority", nullable = false)
    private int priority = 100;

    @Setter(AccessLevel.NONE)
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
