package com.tansoflow.tansocore.service.internal.monetization;

import com.tansoflow.tansocore.model.plan.response.PlanRevenueResponse;

import java.time.Instant;
import java.util.UUID;

public interface PlanRevenueService {
    PlanRevenueResponse getPlanRevenue(String accountId, UUID planId, Instant periodStart, Instant periodEnd, int page, int size, UUID subscriptionId);
}
