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
import com.tansoflow.tansocore.model.subscription.request.ClientSubscriptionRequest;
import com.tansoflow.tansocore.service.internal.monetization.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mcp.enabled", havingValue = "true")
public class SubscriptionTools {

    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;

    @Tool(description = "CREATES a subscription for a customer on a specific plan. "
            + "The plan must be in ACTIVE status. "
            + "SIDE EFFECT: Subscribes the customer to a billing plan. Generates the first invoice. "
            + "For IN_ADVANCE plans, entitlements are granted after invoice payment. "
            + "For IN_ARREARS plans, entitlements are granted immediately.")
    public String createSubscription(
            @ToolParam(description = "The customer's external reference ID") String customerReferenceId,
            @ToolParam(description = "The plan ID to subscribe to (use list_plans to find available plans)") String planId) {
        try {
            ClientSubscriptionRequest request = new ClientSubscriptionRequest();
            request.setCustomerReferenceId(customerReferenceId);
            request.setPlanId(planId);

            var result = subscriptionService.clientSubscribeCustomer(request, getAccountId());
            return objectMapper.writeValueAsString(result);
        } catch (ResourceNotFoundException e) {
            return "{\"error\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (IllegalArgumentException | IllegalStateException e) {
            return "{\"error\": \"invalid_request\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize subscription response\"}";
        }
    }

    @Tool(description = "CANCELS a customer's subscription. "
            + "DESTRUCTIVE: This will end the customer's access to plan features. "
            + "Use cancelMode 'END_OF_PERIOD' (default) to let the subscription run until the current billing period ends, "
            + "or 'IMMEDIATE' to cancel right now. You MUST set confirmAction to true to execute.")
    public String cancelSubscription(
            @ToolParam(description = "The subscription ID to cancel") String subscriptionId,
            @ToolParam(description = "Cancel mode: 'END_OF_PERIOD' (graceful) or 'IMMEDIATE'", required = false) String cancelMode,
            @ToolParam(description = "Must be true to execute this destructive action") boolean confirmAction) {
        if (!confirmAction) {
            return "{\"error\": \"confirmation_required\", \"message\": \"Set confirmAction to true to execute this destructive action\"}";
        }
        try {
            String mode = (cancelMode == null || cancelMode.isBlank()) ? "END_OF_PERIOD" : cancelMode;
            subscriptionService.cancelSubscription(subscriptionId, mode, getAccountId());
            return "{\"success\": true, \"message\": \"Subscription " + subscriptionId + " cancelled with mode " + mode + "\"}";
        } catch (ResourceNotFoundException e) {
            return "{\"error\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (IllegalArgumentException | IllegalStateException e) {
            return "{\"error\": \"invalid_request\", \"message\": \"" + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "CHANGES a customer's subscription to a different plan. "
            + "SIDE EFFECT: Upgrades take effect immediately with prorated billing. "
            + "Downgrades are scheduled for the end of the current billing period. "
            + "You MUST set confirmAction to true to execute.")
    public String changeSubscriptionPlan(
            @ToolParam(description = "The subscription ID to change") String subscriptionId,
            @ToolParam(description = "The new plan ID to switch to") String newPlanId,
            @ToolParam(description = "Change type: 'UPGRADE' (immediate) or 'DOWNGRADE' (end of period)") String changeType,
            @ToolParam(description = "Must be true to execute this action") boolean confirmAction) {
        if (!confirmAction) {
            return "{\"error\": \"confirmation_required\", \"message\": \"Set confirmAction to true to execute this action\"}";
        }
        try {
            if ("UPGRADE".equalsIgnoreCase(changeType)) {
                subscriptionService.upgradeSubscription(subscriptionId, getAccountId(), newPlanId, true);
            } else if ("DOWNGRADE".equalsIgnoreCase(changeType)) {
                subscriptionService.scheduleDowngradeSubscription(subscriptionId, getAccountId(), newPlanId);
            } else {
                return "{\"error\": \"invalid_request\", \"message\": \"changeType must be 'UPGRADE' or 'DOWNGRADE'\"}";
            }
            return "{\"success\": true, \"message\": \"Subscription " + subscriptionId + " " + changeType.toLowerCase() + " to plan " + newPlanId + " initiated\"}";
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
