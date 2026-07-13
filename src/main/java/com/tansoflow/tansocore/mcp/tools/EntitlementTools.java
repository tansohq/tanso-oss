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
import com.tansoflow.tansocore.model.entitlement.api.EntitlementEvaluationRequest;
import com.tansoflow.tansocore.model.entitlement.api.UsageContext;
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.service.client.ClientEntitlementService;

import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mcp.enabled", havingValue = "true")
public class EntitlementTools {

    private final ClientEntitlementService clientEntitlementService;
    private final ObjectMapper objectMapper;

    @Tool(description = "Check if a customer has access to a specific feature. "
            + "Returns whether access is allowed, the usage limit, current usage, and remaining quota.")
    public String checkEntitlement(
            @ToolParam(description = "The customer's external reference ID (your system's user/customer ID)") String customerReferenceId,
            @ToolParam(description = "The feature key to check, e.g. 'api_access' or 'ai_messages'") String featureKey) {
        try {
            var result = clientEntitlementService.checkEntitlement(
                    customerReferenceId, getAccountId(), featureKey);
            return objectMapper.writeValueAsString(result);
        } catch (ResourceNotFoundException e) {
            return "{\"error\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize entitlement response\"}";
        }
    }

    @Tool(description = "List all entitlements for a customer. "
            + "Returns every feature the customer has access to, along with usage limits and current consumption.")
    public String listCustomerEntitlements(
            @ToolParam(description = "The customer's external reference ID (your system's user/customer ID)") String customerReferenceId) {
        try {
            var result = clientEntitlementService.getCustomerEntitlements(
                    customerReferenceId, getAccountId());
            return objectMapper.writeValueAsString(result);
        } catch (ResourceNotFoundException e) {
            return "{\"error\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize entitlements\"}";
        }
    }

    @Tool(description = "Simulates an entitlement check with optional usage context. "
            + "SIDE EFFECT: Records a zero-usage audit event but does NOT consume actual usage. "
            + "Use this to check if proposed usage would be allowed before ingesting a real event.")
    public String evaluateEntitlement(
            @ToolParam(description = "The customer's external reference ID") String customerReferenceId,
            @ToolParam(description = "The feature key to evaluate, e.g. 'api_access'") String featureKey,
            @ToolParam(description = "Number of usage units to simulate, e.g. '100'", required = false) String requestedUsage) {
        try {
            EntitlementEvaluationRequest request = new EntitlementEvaluationRequest();
            request.setCustomerReferenceId(customerReferenceId);
            request.setFeatureKey(featureKey);

            if (requestedUsage != null && !requestedUsage.isBlank()) {
                UsageContext usageContext = new UsageContext();
                usageContext.setUsageUnits(new BigDecimal(requestedUsage));
                request.setUsage(usageContext);
            }

            var result = clientEntitlementService.evaluateEntitlement(getAccountId(), request);
            return objectMapper.writeValueAsString(result);
        } catch (ResourceNotFoundException e) {
            return "{\"error\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (NumberFormatException e) {
            return "{\"error\": \"invalid_request\", \"message\": \"requestedUsage must be a valid number\"}";
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize entitlement response\"}";
        }
    }

    private String getAccountId() {
        UserContext ctx = (UserContext) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return ctx.getAccountId();
    }
}
