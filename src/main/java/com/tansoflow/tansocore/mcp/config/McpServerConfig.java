/*
 * Tanso Core - open-source B2B SaaS monetization engine
 * Copyright (C) 2026  Douglas Baek
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConditionalOnProperty(name = "app.mcp.enabled", havingValue = "true")
public class McpServerConfig {

    // Admin tool beans only exist when app.mcp.admin-tools.enabled is also true —
    // a plain client API key must never reach tenant-configuration tools.
    @Bean
    public ToolCallbackProvider toolCallbackProvider(
            PlanTools planTools,
            EntitlementTools entitlementTools,
            CustomerTools customerTools,
            BillingTools billingTools,
            CreditTools creditTools,
            SubscriptionTools subscriptionTools,
            EventTools eventTools,
            AnalyticsTools analyticsTools,
            AiInsightTools aiInsightTools,
            ObjectProvider<AdminFeatureTools> adminFeatureTools,
            ObjectProvider<AdminPlanTools> adminPlanTools,
            ObjectProvider<AdminPlanFeatureRuleTools> adminPlanFeatureRuleTools,
            ObjectProvider<StripeSetupTools> stripeSetupTools,
            ObjectProvider<AdminEventTools> adminEventTools,
            ObjectProvider<AdminCreditTools> adminCreditTools) {
        List<Object> tools = new ArrayList<>(List.of(
                planTools, entitlementTools, customerTools,
                billingTools, creditTools, subscriptionTools, eventTools,
                analyticsTools, aiInsightTools));
        adminFeatureTools.ifAvailable(tools::add);
        adminPlanTools.ifAvailable(tools::add);
        adminPlanFeatureRuleTools.ifAvailable(tools::add);
        stripeSetupTools.ifAvailable(tools::add);
        adminEventTools.ifAvailable(tools::add);
        adminCreditTools.ifAvailable(tools::add);
        return MethodToolCallbackProvider.builder()
                .toolObjects(tools.toArray())
                .build();
    }
}
