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
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Tanso-owned record of a hosted Stripe Checkout flow, so an agent that
 * received a checkout URL can poll the outcome by a stable id instead of
 * dead-ending at a browser redirect.
 */
@Entity
@Table(name = "checkout_sessions")
@Getter
@Setter
@NoArgsConstructor
public class CheckoutSession {

    public static final String PURPOSE_SUBSCRIPTION = "SUBSCRIPTION";
    public static final String PURPOSE_CREDIT_TOPUP = "CREDIT_TOPUP";
    public static final String PURPOSE_INVOICE = "INVOICE";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_EXPIRED = "EXPIRED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "checkout_session_id")
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "purpose", nullable = false, length = 30)
    private String purpose;

    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_PENDING;

    @Column(name = "stripe_session_id", length = 255)
    private String stripeSessionId;

    @Column(name = "plan_id")
    private UUID planId;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(name = "credit_pool_id")
    private UUID creditPoolId;

    @Column(name = "credits", precision = 18, scale = 4)
    private BigDecimal credits;

    @Column(name = "checkout_url")
    private String checkoutUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
