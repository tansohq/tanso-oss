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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One person's day with one vendor's tool, as the vendor reports it. Which
 * columns are filled depends on the vendor: Claude Code has sessions, commits
 * and PRs; Cursor has accepts/rejects and lines; Copilot has interactions,
 * accepted code and AI credits. Null means the vendor does not report it.
 */
@Getter
@Setter
@Entity
@Table(name = "vendor_actor_metrics")
public class VendorActorMetric {
    @Id
    @GeneratedValue
    @Column(name = "vendor_actor_metric_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "vendor_connection_id", nullable = false, updatable = false)
    private UUID connectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private VendorProvider provider;

    @Column(name = "day", nullable = false)
    private LocalDate day;

    /** Email (Claude Code, Cursor) or login (Copilot). */
    @Column(name = "actor_id", nullable = false, length = 255)
    private String actorId;

    @Column(name = "tool", length = 64)
    private String tool;

    @Column(name = "sessions")
    private Integer sessions;

    @Column(name = "requests")
    private Integer requests;

    @Column(name = "lines_added")
    private Integer linesAdded;

    @Column(name = "lines_removed")
    private Integer linesRemoved;

    @Column(name = "lines_suggested")
    private Integer linesSuggested;

    @Column(name = "accepted")
    private Integer accepted;

    @Column(name = "rejected")
    private Integer rejected;

    @Column(name = "commits")
    private Integer commits;

    @Column(name = "pull_requests")
    private Integer pullRequests;

    @Column(name = "credits_used", precision = 18, scale = 4)
    private BigDecimal creditsUsed;

    @Column(name = "estimated_cost_cents", precision = 18, scale = 6)
    private BigDecimal estimatedCostCents;

    @Setter(AccessLevel.NONE)
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
