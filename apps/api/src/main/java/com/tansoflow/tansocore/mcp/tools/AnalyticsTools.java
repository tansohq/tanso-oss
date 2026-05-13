package com.tansoflow.tansocore.mcp.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.service.internal.analytics.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mcp.enabled", havingValue = "true")
public class AnalyticsTools {

    private final AnalyticsService analyticsService;
    private final ObjectMapper objectMapper;

    @Tool(description = "Get portfolio-level margin analytics across all customers with active subscriptions. "
            + "Returns a summary with total MRR, costs, average margin, customer health distribution "
            + "(healthy/at-risk/underwater), churn scores, credit impact, revenue burn-down projections, "
            + "and per-customer profitability breakdowns including feature-level and model-level details.")
    public String getPortfolioAnalytics() {
        try {
            var result = analyticsService.getPortfolioAnalytics(getAccountId());
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize analytics response\"}";
        }
    }

    @Tool(description = "Get period-over-period revenue waterfall from paid invoices. "
            + "Returns base revenue, usage revenue, adjustment revenue, credit amounts, "
            + "net revenue, and customer counts (new/churned) for each billing period.")
    public String getRevenueBridge(
            @ToolParam(description = "Number of billing periods to include (default 6)", required = false) String periods) {
        try {
            int parsedPeriods = 6;
            if (periods != null && !periods.isBlank()) {
                try {
                    parsedPeriods = Integer.parseInt(periods);
                } catch (NumberFormatException e) {
                    return "{\"error\": \"invalid_request\", \"message\": \"periods must be a valid integer\"}";
                }
            }
            var result = analyticsService.getRevenueBridge(getAccountId(), parsedPeriods);
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize revenue bridge response\"}";
        }
    }

    @Tool(description = "Get cost and usage analytics grouped by AI model. "
            + "Returns total costs, revenue, event counts, provider breakdown percentages, "
            + "and per-model details including customer count, average cost per event, margin, and last seen timestamp.")
    public String getModelAnalytics() {
        try {
            var result = analyticsService.getModelAnalytics(getAccountId());
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize model analytics response\"}";
        }
    }

    private String getAccountId() {
        UserContext ctx = (UserContext) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return ctx.getAccountId();
    }
}
