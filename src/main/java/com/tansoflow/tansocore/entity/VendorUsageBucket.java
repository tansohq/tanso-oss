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
import com.tansoflow.tansocore.model.spend.type.VendorUsageSource;
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
import java.util.UUID;

/**
 * One day-bucket of a vendor's usage or cost report, in the vendor's own
 * dimensions. Written by the sync; a window is deleted and rewritten on every
 * pull so re-syncing is idempotent.
 */
@Getter
@Setter
@Entity
@Table(name = "vendor_usage_buckets")
public class VendorUsageBucket {
    @Id
    @GeneratedValue
    @Column(name = "vendor_usage_bucket_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "vendor_connection_id", nullable = false, updatable = false)
    private UUID connectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private VendorProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 32)
    private VendorUsageSource source;

    @Column(name = "bucket_start", nullable = false)
    private Instant bucketStart;

    @Column(name = "bucket_end", nullable = false)
    private Instant bucketEnd;

    @Column(name = "model", length = 128)
    private String model;

    /** Anthropic workspace_id or OpenAI project_id. */
    @Column(name = "workspace_id", length = 128)
    private String workspaceId;

    @Column(name = "vendor_api_key_id", length = 128)
    private String vendorApiKeyId;

    /** Claude Code actor email / api key name, or OpenAI user_id. */
    @Column(name = "actor_id", length = 255)
    private String actorId;

    @Column(name = "service_tier", length = 32)
    private String serviceTier;

    /** Cost-report line description / OpenAI line_item. */
    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "uncached_input_tokens", nullable = false)
    private long uncachedInputTokens;

    @Column(name = "cache_read_tokens", nullable = false)
    private long cacheReadTokens;

    @Column(name = "cache_creation_tokens", nullable = false)
    private long cacheCreationTokens;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    @Column(name = "requests")
    private Long requests;

    /** The vendor's own price for this row, in cents. Only COST_API and CLAUDE_CODE_API rows carry one. */
    @Column(name = "vendor_cost_cents", precision = 18, scale = 6)
    private BigDecimal vendorCostCents;

    @Column(name = "currency", length = 8)
    private String currency;

    @Setter(AccessLevel.NONE)
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
