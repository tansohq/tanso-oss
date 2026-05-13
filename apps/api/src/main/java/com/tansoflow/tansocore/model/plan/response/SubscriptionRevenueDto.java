package com.tansoflow.tansocore.model.plan.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionRevenueDto {
    private UUID subscriptionId;
    private UUID customerId;
    private String customerName;
    private List<FeatureRevenueDto> features;
    private BigDecimal totalUnits;
    private BigDecimal totalRevenue;
}
