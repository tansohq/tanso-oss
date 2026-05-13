package com.tansoflow.tansocore.mcp.config;

import com.tansoflow.tansocore.mcp.tools.AdminCreditTools;
import com.tansoflow.tansocore.mcp.tools.AdminEventTools;
import com.tansoflow.tansocore.mcp.tools.AdminFeatureTools;
import com.tansoflow.tansocore.mcp.tools.AdminPlanFeatureRuleTools;
import com.tansoflow.tansocore.mcp.tools.AdminPlanTools;
import com.tansoflow.tansocore.mcp.tools.AiInsightTools;
import com.tansoflow.tansocore.mcp.tools.AnalyticsTools;
import com.tansoflow.tansocore.mcp.tools.BillingTools;
import com.tansoflow.tansocore.mcp.tools.CreditTools;
import com.tansoflow.tansocore.mcp.tools.CustomerTools;
import com.tansoflow.tansocore.mcp.tools.EntitlementTools;
import com.tansoflow.tansocore.mcp.tools.EventTools;
import com.tansoflow.tansocore.mcp.tools.PlanTools;
import com.tansoflow.tansocore.mcp.tools.StripeSetupTools;
import com.tansoflow.tansocore.mcp.tools.SubscriptionTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.mcp.enabled", havingValue = "true")
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider toolCallbackProvider(
            PlanTools planTools,
            EntitlementTools entitlementTools,
            CustomerTools customerTools,
            BillingTools billingTools,
            CreditTools creditTools,
            SubscriptionTools subscriptionTools,
            EventTools eventTools,
            AdminFeatureTools adminFeatureTools,
            AdminPlanTools adminPlanTools,
            AdminPlanFeatureRuleTools adminPlanFeatureRuleTools,
            StripeSetupTools stripeSetupTools,
            AnalyticsTools analyticsTools,
            AiInsightTools aiInsightTools,
            AdminEventTools adminEventTools,
            AdminCreditTools adminCreditTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(planTools, entitlementTools, customerTools,
                        billingTools, creditTools, subscriptionTools, eventTools,
                        adminFeatureTools, adminPlanTools, adminPlanFeatureRuleTools,
                        stripeSetupTools, analyticsTools, aiInsightTools,
                        adminEventTools, adminCreditTools)
                .build();
    }
}
