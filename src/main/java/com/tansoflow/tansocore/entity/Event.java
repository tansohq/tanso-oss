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

import com.tansoflow.tansocore.model.event.events.type.CostUnit;
import com.tansoflow.tansocore.model.event.events.type.EventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "events")
public class Event {
    @Id
    @GeneratedValue
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Size(max = 64)
    @NotNull
    @Column(name = "event_idempotency_key", nullable = false, length = 64)
    private String eventIdempotencyKey;

    @Size(max = 64)
    @Column(name = "flow_id", length = 64)
    private String flowId;

    @Size(max = 128)
    @NotNull
    @Column(name = "event_name", nullable = false, length = 128)
    private String eventName;

    @NotNull
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "feature_id")
    private UUID featureId;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(name = "entitlement_id")
    private UUID entitlementId;

    @Column(name = "invoice_id")
    private UUID invoiceId;
    
    @Column(name = "cost_amount", precision = 18, scale = 6)
    private java.math.BigDecimal costAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "cost_unit", length = 32)
    private CostUnit costUnit;

    @Column(name = "revenue_amount", precision = 18, scale = 2)
    private java.math.BigDecimal revenueAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "revenue_unit", length = 32)
    private CostUnit revenueUnit;

    @Column(name = "usage_units", precision = 18, scale = 4)
    private java.math.BigDecimal usageUnits;

    @Size(max = 32)
    @Column(name = "usage_unit_type", length = 32)
    private String usageUnitType;

    @Size(max = 128)
    @Column(name = "model", length = 128)
    private String model;

    @Size(max = 64)
    @Column(name = "model_provider", length = 64)
    private String modelProvider;

    @Column(name = "cost_units", precision = 18, scale = 4)
    private java.math.BigDecimal costUnits;

    @Column(name = "input_tokens", precision = 18, scale = 4)
    private java.math.BigDecimal inputTokens;

    @Column(name = "output_tokens", precision = 18, scale = 4)
    private java.math.BigDecimal outputTokens;

    @NotNull
    @ColumnDefault("'{}'")
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "properties", nullable = false)
    private Map<String, Object> properties = new HashMap<>();

    @NotNull
    @ColumnDefault("'{}'")
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meta", nullable = false)
    private Map<String, Object> meta = new HashMap<>();

    @NotNull
    @ColumnDefault("'{}'")
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context", nullable = false)
    private Map<String, Object> context = new HashMap<>();

    @Column(name = "customer_is_native")
    private Boolean customerIsNative;

    @Column(name = "feature_is_native")
    private Boolean featureIsNative;

    @Column(name = "subscription_is_native")
    private Boolean subscriptionIsNative;

    @Column(name = "entitlement_is_native")
    private Boolean entitlementIsNative;

    @Column(name = "invoice_is_native")
    private Boolean invoiceIsNative;

    @Column(name = "ingest_error", length = Integer.MAX_VALUE)
    private String ingestError;

    @Setter(AccessLevel.NONE)
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Setter(AccessLevel.NONE)
    @Column(name = "modified_at", insertable = false)
    @UpdateTimestamp
    private Instant modifiedAt;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64)
    private EventType eventType;
}