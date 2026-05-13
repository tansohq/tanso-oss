package com.tansoflow.tansocore.model.account.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
@Schema(description = "Response containing the account's subscription status")
public class SubscriptionStatusResponse {
    @Schema(description = "Whether the account has an active subscription", example = "true")
    private boolean hasActiveSubscription;

    @Schema(description = "Name of the active plan", example = "Free Plan")
    private String planName;

    @Schema(description = "Price amount of the active plan", example = "0.00")
    private BigDecimal planPriceAmount;

    @Schema(description = "Billing interval in months", example = "1")
    private String planIntervalMonths;

    @Schema(description = "Unique key of the active plan", example = "free_monthly")
    private String planKey;
}
