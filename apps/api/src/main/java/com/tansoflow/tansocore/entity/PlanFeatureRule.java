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
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "plan_feature_rules")
@SQLRestriction("deleted_at IS NULL")
// NOTE: For this SQLDelete query, the ordering of the columns is important.
// Hibernate will process the columns alphabetically for composite type
// regardless of database order or order of the fields in the class/embeddable ID class.
@SQLDelete(sql =
        "UPDATE plan_feature_rules " +
                "SET deleted_at = now() " +
                "WHERE id = ?")
public class PlanFeatureRule {
    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "feature_id", nullable = false)
    private Feature feature;

    @ColumnDefault("false")
    @Column(name = "is_enabled")
    private Boolean isEnabled;
    @Size(max = 255)
    @Column(name = "type")
    private String type;

    @Column(name = "value")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> value;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credit_model_id")
    private CreditModel creditModel;

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