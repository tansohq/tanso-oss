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

import com.tansoflow.tansocore.model.spend.type.OutcomeKind;
import com.tansoflow.tansocore.model.spend.type.OutcomeSource;
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

/** One unit of shipped work. Unique per (account, source, external id) so re-pulls upsert. */
@Getter
@Setter
@Entity
@Table(name = "outcomes")
public class Outcome {
    @Id
    @GeneratedValue
    @Column(name = "outcome_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "outcome_source_id")
    private UUID sourceConnectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private OutcomeSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private OutcomeKind kind;

    @Column(name = "external_id", nullable = false, length = 255)
    private String externalId;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "url", length = 1000)
    private String url;

    @Column(name = "actor_email", length = 255)
    private String actorEmail;

    @Column(name = "actor_login", length = 255)
    private String actorLogin;

    /** Resolved at write time: the person whose email/login matched, else the source's default unit. */
    @Column(name = "spend_unit_id")
    private UUID spendUnitId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Setter(AccessLevel.NONE)
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
