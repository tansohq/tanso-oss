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

import com.tansoflow.tansocore.model.api.external.StripeMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "account_settings")
public class AccountSetting {
    @Id
    @GeneratedValue
    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account accounts;

    @NotNull
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'NONE'")
    @Column(name = "stripe_mode", nullable = false, length = 30)
    private StripeMode stripeMode = StripeMode.NONE;

    @Column(name = "stripe_checkout_success_url", length = Integer.MAX_VALUE)
    private String stripeCheckoutSuccessUrl;

    @Column(name = "stripe_checkout_cancel_url", length = Integer.MAX_VALUE)
    private String stripeCheckoutCancelUrl;

    @NotNull
    @ColumnDefault("'USD'")
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @ColumnDefault("false")
    @Column(name = "public_catalog_enabled", nullable = false)
    private boolean publicCatalogEnabled = false;

    @ColumnDefault("false")
    @Column(name = "agent_signup_enabled", nullable = false)
    private boolean agentSignupEnabled = false;

    @Column(name = "agent_signup_default_plan_id")
    private UUID agentSignupDefaultPlanId;

    @ColumnDefault("10")
    @Column(name = "agent_signup_hourly_cap", nullable = false)
    private int agentSignupHourlyCap = 10;

    // Attributing spend to a named employee is a monitoring capability; off until the
    // operator says so and has told their staff (the notice is required to enable it).
    @ColumnDefault("false")
    @Column(name = "spend_person_level_enabled", nullable = false)
    private boolean spendPersonLevelEnabled = false;

    @Column(name = "spend_worker_notice", length = Integer.MAX_VALUE)
    private String spendWorkerNotice;

    /** Comma-separated recipients for spend alerts and the weekly digest. */
    @Column(name = "spend_alert_emails", length = Integer.MAX_VALUE)
    private String spendAlertEmails;

    @Column(name = "spend_digest_enabled", nullable = false)
    private boolean spendDigestEnabled = false;

    // Max money one agent-initiated purchase may move; null = uncapped
    @Column(name = "agent_max_topup_amount", precision = 18, scale = 2)
    private java.math.BigDecimal agentMaxTopupAmount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_cost_config")
    private Map<String, Object> defaultCostConfig;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "onboarding_progress")
    private Map<String, Object> onboardingProgress;

    public boolean isStripeEnabled() {
        return stripeMode != StripeMode.NONE;
    }

}