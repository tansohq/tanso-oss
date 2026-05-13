package com.tansoflow.tansocore.model.plan.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class PlanRequest {
    private String key;
    private String name;
    private BigDecimal priceAmount;
    private String description;
    private String intervalMonths;
    private String billingTiming;
    private String status;
    private Map<String, Object> metadata;
}
