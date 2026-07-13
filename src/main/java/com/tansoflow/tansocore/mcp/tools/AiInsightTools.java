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
package com.tansoflow.tansocore.mcp.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.service.internal.analytics.AiInsightService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mcp.enabled", havingValue = "true")
public class AiInsightTools {

    private final AiInsightService aiInsightService;
    private final ObjectMapper objectMapper;

    @Tool(description = "List all previously generated AI insights for the account. "
            + "Returns insights with severity (CRITICAL, WARNING, POSITIVE, INFO), title, description, "
            + "category, associated feature/customer, metric values, and token usage costs.")
    public String listInsights() {
        try {
            var insights = aiInsightService.listInsights(getAccountId());
            return objectMapper.writeValueAsString(insights);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize insights\"}";
        }
    }

    @Tool(description = "GENERATES new AI-powered insights by analyzing event data and customer patterns. "
            + "SIDE EFFECT: Calls an external AI model (costs tokens/money), replaces any existing insights, "
            + "and stores the results. Returns the newly generated insights.")
    public String generateInsights(
            @ToolParam(description = "Set to true to confirm this action. Required because it costs money (AI tokens) "
                    + "and replaces existing insights.") boolean confirmAction) {
        if (!confirmAction) {
            return "{\"error\": \"confirmation_required\", \"message\": \"Set confirmAction to true to generate insights. "
                    + "This calls an external AI model and incurs token costs.\"}";
        }
        try {
            var insights = aiInsightService.generateInsights(getAccountId());
            return objectMapper.writeValueAsString(insights);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize insights\"}";
        } catch (Exception e) {
            return "{\"error\": \"generation_failed\", \"message\": \"" + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "DELETES all generated AI insights for the account. "
            + "SIDE EFFECT: Permanently removes all stored insights. They can be regenerated later.")
    public String clearInsights(
            @ToolParam(description = "Set to true to confirm deletion of all insights.") boolean confirmAction) {
        if (!confirmAction) {
            return "{\"error\": \"confirmation_required\", \"message\": \"Set confirmAction to true to delete all insights.\"}";
        }
        try {
            aiInsightService.clearInsights(getAccountId());
            return "{\"success\": true, \"message\": \"All insights cleared\"}";
        } catch (Exception e) {
            return "{\"error\": \"clear_failed\", \"message\": \"" + e.getMessage() + "\"}";
        }
    }

    private String getAccountId() {
        UserContext ctx = (UserContext) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return ctx.getAccountId();
    }
}
