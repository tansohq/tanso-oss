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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.model.monetization.request.PlanFeatureRuleRequest;
import com.tansoflow.tansocore.service.internal.monetization.PlanFeatureRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mcp.enabled", havingValue = "true")
public class AdminPlanFeatureRuleTools {

    private final PlanFeatureRuleService planFeatureRuleService;
    private final ObjectMapper objectMapper;

    @Tool(description = "Get the pricing/cost rule for a plan-feature link. "
            + "Returns the rule type, value configuration (pricing model, tiers, etc.), and enabled status.")
    public String adminGetFeatureRule(
            @ToolParam(description = "The plan ID (UUID)") String planId,
            @ToolParam(description = "The feature ID (UUID)") String featureId) {
        try {
            var result = planFeatureRuleService.getPlanFeatureRule(getAccountId(), featureId, planId);
            return objectMapper.writeValueAsString(result);
        } catch (ResourceNotFoundException e) {
            return "{\"error\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize rule\"}";
        }
    }

    @Tool(description = "LINKS a feature to a plan with optional pricing rules. "
            + "SIDE EFFECT: Creates a plan-feature rule that defines how the feature behaves in this plan. "
            + "The valueJson parameter accepts a JSON string with NESTED pricing and cost config. "
            + "Omit valueJson entirely for a boolean (on/off) gate. "
            + "FORMAT: {\"pricing\": {<pricing model>}, \"cost\": {<cost model>}} "
            + "PRICING MODELS (inside \"pricing\" key) — "
            + "Flat-rate: {\"model\":\"usage\", \"usage_unit_type\":\"<string>\", \"price_per_unit\":<number>} "
            + "Optional flat-rate fields: \"max_usage\":<non-negative number>, \"reset_mode\":\"reset\"|\"accumulate\" (default \"reset\"). "
            + "Graduated: {\"model\":\"graduated\", \"usage_unit_type\":\"<string>\", \"tiers\":[{\"up_to\":<number|\"inf\">, \"price_per_unit\":<non-negative number>, \"flat_fee\":<optional non-negative number>}, ...]} "
            + "Tiers must have at least one entry. Each tier requires up_to and price_per_unit. "
            + "COST MODELS (inside \"cost\" key, optional) — "
            + "Simple: {\"model\":\"simple\", \"cost_per_unit\":<non-negative number>, \"cost_unit\":\"TOKENS\"|\"CREDITS\"|\"CURRENCY\"} "
            + "Model-aware: {\"model\":\"model_aware\", \"default_cost_per_unit\":<number>, \"model_costs\":{\"gpt-4\":<number>, ...}, \"cost_unit\":\"CURRENCY\"} "
            + "EXAMPLES — "
            + "Flat-rate: {\"pricing\":{\"model\":\"usage\",\"price_per_unit\":0.10,\"usage_unit_type\":\"api_calls\"}} "
            + "With simple cost: {\"pricing\":{\"model\":\"usage\",\"price_per_unit\":0.10,\"usage_unit_type\":\"api_calls\"},\"cost\":{\"model\":\"simple\",\"cost_per_unit\":0.03}} "
            + "With model-aware cost: {\"pricing\":{\"model\":\"usage\",\"price_per_unit\":0.01,\"usage_unit_type\":\"api_calls\"},\"cost\":{\"model\":\"model_aware\",\"default_cost_per_unit\":0.00003,\"model_costs\":{\"gpt-4\":0.00006,\"claude-3-opus\":0.000075}}} "
            + "Graduated: {\"pricing\":{\"model\":\"graduated\",\"usage_unit_type\":\"api_calls\",\"tiers\":[{\"up_to\":100,\"price_per_unit\":0.10,\"flat_fee\":5.00},{\"up_to\":\"inf\",\"price_per_unit\":0.05}]}} "
            + "Legacy flat format is also accepted and will be normalized to nested format on save.")
    public String adminLinkFeatureToPlan(
            @ToolParam(description = "The plan ID (UUID)") String planId,
            @ToolParam(description = "The feature ID (UUID)") String featureId,
            @ToolParam(description = "Rule type. Must be 'BASE'. Omit valueJson for a boolean (on/off) gate; provide valueJson with a pricing model for metered billing.", required = false) String type,
            @ToolParam(description = "JSON string with pricing model configuration (see tool description for schemas)", required = false) String valueJson,
            @ToolParam(description = "Whether the rule is enabled (default: true)", required = false) Boolean isEnabled) {
        try {
            PlanFeatureRuleRequest request = new PlanFeatureRuleRequest();
            request.setPlanId(planId);
            request.setFeatureId(featureId);
            if (type != null) request.setType(type);
            if (valueJson != null) {
                Map<String, Object> value = objectMapper.readValue(valueJson, new TypeReference<>() {});
                request.setValue(value);
            }
            if (isEnabled != null) request.setIsEnabled(isEnabled);

            var result = planFeatureRuleService.createPlanFeatureRule(getAccountId(), request);
            return objectMapper.writeValueAsString(result);
        } catch (ResourceNotFoundException e) {
            return "{\"error\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (IllegalArgumentException | IllegalStateException e) {
            return "{\"error\": \"invalid_request\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to process rule: " + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "UPDATES an existing plan-feature rule. "
            + "SIDE EFFECT: Modifies the pricing/cost configuration for a feature within a plan. "
            + "Only provided fields are updated. See adminLinkFeatureToPlan for valueJson schemas.")
    public String adminUpdateFeatureRule(
            @ToolParam(description = "The plan ID (UUID)") String planId,
            @ToolParam(description = "The feature ID (UUID)") String featureId,
            @ToolParam(description = "Rule type. Must be 'BASE' if provided.", required = false) String type,
            @ToolParam(description = "JSON string with updated pricing model configuration", required = false) String valueJson,
            @ToolParam(description = "Enable or disable the rule", required = false) Boolean isEnabled) {
        try {
            PlanFeatureRuleRequest request = new PlanFeatureRuleRequest();
            request.setPlanId(planId);
            request.setFeatureId(featureId);
            if (type != null) request.setType(type);
            if (valueJson != null) {
                Map<String, Object> value = objectMapper.readValue(valueJson, new TypeReference<>() {});
                request.setValue(value);
            }
            if (isEnabled != null) request.setIsEnabled(isEnabled);

            var result = planFeatureRuleService.updatePlanFeatureRule(getAccountId(), request);
            return objectMapper.writeValueAsString(result);
        } catch (ResourceNotFoundException e) {
            return "{\"error\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (IllegalArgumentException | IllegalStateException e) {
            return "{\"error\": \"invalid_request\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to process rule: " + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "UNLINKS a feature from a plan by deleting the plan-feature rule. "
            + "DESTRUCTIVE: This permanently removes the feature's pricing configuration from the plan. "
            + "You MUST set confirmAction to true to execute.")
    public String adminUnlinkFeatureFromPlan(
            @ToolParam(description = "The plan ID (UUID)") String planId,
            @ToolParam(description = "The feature ID (UUID)") String featureId,
            @ToolParam(description = "Must be true to execute this destructive action") boolean confirmAction) {
        if (!confirmAction) {
            return "{\"error\": \"confirmation_required\", \"message\": \"Set confirmAction to true to execute this destructive action\"}";
        }
        try {
            planFeatureRuleService.deletePlanFeatureRule(getAccountId(), featureId, planId);
            return "{\"success\": true, \"message\": \"Feature " + featureId + " unlinked from plan " + planId + "\"}";
        } catch (ResourceNotFoundException e) {
            return "{\"error\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (IllegalArgumentException | IllegalStateException e) {
            return "{\"error\": \"invalid_request\", \"message\": \"" + e.getMessage() + "\"}";
        }
    }

    private String getAccountId() {
        UserContext ctx = (UserContext) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return ctx.getAccountId();
    }
}
