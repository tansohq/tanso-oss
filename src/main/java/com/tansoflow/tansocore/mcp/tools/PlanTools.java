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
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.service.client.ClientPlanService;
import com.tansoflow.tansocore.service.internal.monetization.FeatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mcp.enabled", havingValue = "true")
public class PlanTools {

    private final ClientPlanService clientPlanService;
    private final FeatureService featureService;
    private final ObjectMapper objectMapper;

    @Tool(description = "List all active plans with their pricing and linked features. "
            + "Returns plan names, prices, billing intervals, and the features included in each plan.")
    public String listPlans() {
        try {
            var plans = clientPlanService.retrieveActivePlansWithPricing(getAccountId());
            return objectMapper.writeValueAsString(plans);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize plans\"}";
        }
    }

    @Tool(description = "List all features defined in your account. "
            + "Features are individual capabilities (e.g. 'api_access', 'ai_messages') that can be included in plans.")
    public String listFeatures() {
        try {
            var features = featureService.getFeatures(getAccountId());
            return objectMapper.writeValueAsString(features);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize features\"}";
        }
    }

    @Tool(description = "Get details for a single feature by its unique key. "
            + "Returns the feature's name, type, and configuration.")
    public String getFeature(
            @ToolParam(description = "The unique feature key, e.g. 'api_access' or 'ai_messages'") String featureKey) {
        try {
            var feature = featureService.getFeatureByKey(getAccountId(), featureKey);
            return objectMapper.writeValueAsString(feature);
        } catch (ResourceNotFoundException e) {
            return "{\"error\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize feature\"}";
        }
    }

    private String getAccountId() {
        UserContext ctx = (UserContext) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return ctx.getAccountId();
    }
}
