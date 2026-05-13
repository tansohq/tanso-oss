package com.tansoflow.tansocore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only financial ledger. Records are never updated or deleted.
 * Undoing a transaction creates a REVERSAL entry.
 */
@Getter
@Setter(AccessLevel.NONE)
@Entity
@Table(name = "credit_transactions")
public class CreditTransaction {
    @Id
    @GeneratedValue
    @Column(name = "credit_transaction_id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credit_pool_id", nullable = false, updatable = false)
    @Setter(AccessLevel.PUBLIC)
    private CreditPool creditPool;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credit_grant_id", updatable = false)
    @Setter(AccessLevel.PUBLIC)
    private CreditGrant creditGrant;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", updatable = false, nullable = false)
    @Setter(AccessLevel.PUBLIC)
    private Account account;

    @Column(name = "subscription_id", updatable = false)
    @Setter(AccessLevel.PUBLIC)
    private UUID subscriptionId;

    @Column(name = "customer_id", updatable = false)
    @Setter(AccessLevel.PUBLIC)
    private UUID customerId;

    @Column(name = "event_id", updatable = false)
    @Setter(AccessLevel.PUBLIC)
    private UUID eventId;

    @Column(name = "invoice_id", updatable = false)
    @Setter(AccessLevel.PUBLIC)
    private UUID invoiceId;

    @NotNull
    @Size(max = 32)
    @Column(name = "transaction_type", nullable = false, length = 32, updatable = false)
    @Setter(AccessLevel.PUBLIC)
    private String transactionType;

    @NotNull
    @Column(name = "amount", nullable = false, precision = 18, scale = 4, updatable = false)
    @Setter(AccessLevel.PUBLIC)
    private BigDecimal amount;

    @NotNull
    @Column(name = "balance_before", nullable = false, precision = 18, scale = 4, updatable = false)
    @Setter(AccessLevel.PUBLIC)
    private BigDecimal balanceBefore;

    @NotNull
    @Column(name = "balance_after", nullable = false, precision = 18, scale = 4, updatable = false)
    @Setter(AccessLevel.PUBLIC)
    private BigDecimal balanceAfter;

    @Size(max = 500)
    @Column(name = "description", length = 500, updatable = false)
    @Setter(AccessLevel.PUBLIC)
    private String description;

    @Size(max = 128)
    @Column(name = "idempotency_key", length = 128, updatable = false)
    @Setter(AccessLevel.PUBLIC)
    private String idempotencyKey;

    @Column(name = "reversed_transaction_id", updatable = false)
    @Setter(AccessLevel.PUBLIC)
    private UUID reversedTransactionId;

    @NotNull
    @ColumnDefault("'{}'")
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, updatable = false)
    @Setter(AccessLevel.PUBLIC)
    private Map<String, Object> metadata = new HashMap<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
