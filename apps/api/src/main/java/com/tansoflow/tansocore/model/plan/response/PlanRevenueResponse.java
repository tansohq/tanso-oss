package com.tansoflow.tansocore.model.plan.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanRevenueResponse {
    private UUID planId;
    private String planName;
    private Instant periodStart;
    private Instant periodEnd;
    private BigDecimal aggregateTotalUnits;
    private BigDecimal aggregateTotalRevenue;
    private PlanRevenuePagedResponse subscriptions;
}
