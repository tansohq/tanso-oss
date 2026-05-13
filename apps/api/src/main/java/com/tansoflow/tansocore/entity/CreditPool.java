package com.tansoflow.tansocore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "credit_pools")
@SQLRestriction("deleted_at IS NULL")
@SQLDelete(sql = "UPDATE credit_pools SET deleted_at = now() WHERE credit_pool_id = ?")
public class CreditPool {
    @Id
    @GeneratedValue
    @Column(name = "credit_pool_id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", updatable = false, nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @NotNull
    @Size(max = 150)
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @NotNull
    @Size(max = 32)
    @Column(name = "denomination", nullable = false, length = 32)
    private String denomination;

    @Size(max = 3)
    @Column(name = "currency", length = 3)
    private String currency;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "balance", nullable = false, precision = 18, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "total_granted", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalGranted = BigDecimal.ZERO;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "total_consumed", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalConsumed = BigDecimal.ZERO;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "total_expired", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalExpired = BigDecimal.ZERO;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "total_reversed", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalReversed = BigDecimal.ZERO;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "hard_limit", nullable = false)
    private Boolean hardLimit = false;

    @NotNull
    @Size(max = 20)
    @ColumnDefault("'ACTIVE'")
    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @NotNull
    @Size(max = 20)
    @ColumnDefault("'NONE'")
    @Column(name = "rollover_policy", nullable = false, length = 20)
    private String rolloverPolicy = "NONE";

    @Column(name = "rollover_cap", precision = 18, scale = 4)
    private BigDecimal rolloverCap;

    @NotNull
    @ColumnDefault("'{}'")
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false)
    private Map<String, Object> metadata = new HashMap<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credit_model_id")
    private CreditModel creditModel;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Setter(AccessLevel.NONE)
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Setter(AccessLevel.NONE)
    @Column(name = "modified_at", insertable = false)
    @UpdateTimestamp
    private Instant modifiedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;
}
