package com.tansoflow.tansocore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "model_pricing")
public class ModelPricing {
    @Id
    @Column(name = "model", length = 128, nullable = false)
    private String model;

    @NotNull
    @Column(name = "provider", length = 64, nullable = false)
    private String provider;

    @NotNull
    @Column(name = "input_cost_per_million", precision = 18, scale = 6, nullable = false)
    private BigDecimal inputCostPerMillion;

    @NotNull
    @Column(name = "output_cost_per_million", precision = 18, scale = 6, nullable = false)
    private BigDecimal outputCostPerMillion;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
