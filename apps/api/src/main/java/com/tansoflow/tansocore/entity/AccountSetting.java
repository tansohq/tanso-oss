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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_cost_config")
    private Map<String, Object> defaultCostConfig;

    public boolean isStripeEnabled() {
        return stripeMode != StripeMode.NONE;
    }

}