package com.tansoflow.tansocore.mcp.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.model.plan.request.PlanRequest;
import com.tansoflow.tansocore.service.internal.monetization.PlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mcp.enabled", havingValue = "true")
public class AdminPlanTools {

    private final PlanService planService;
    private final ObjectMapper objectMapper;

    @Tool(description = "List all plans in the account. "
            + "Returns every plan with its ID, key, name, price, billing interval, and status.")
    public String adminListPlans() {
        try {
            var plans = planService.getPlans(getAccountId());
            return objectMapper.writeValueAsString(plans);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize plans\"}";
        }
    }

    @Tool(description = "Get a plan's full details including all linked features and credit allocations. "
            + "Use this to inspect the complete configuration of a plan.")
    public String adminGetPlanWithFeatures(
            @ToolParam(description = "The plan ID (UUID)") String planId) {
        try {
            var result = planService.retrievePlanFeatureLinkByPlanUuid(planId, getAccountId());
            return objectMapper.writeValueAsString(result);
        } catch (ResourceNotFoundException e) {
            return "{\"error\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize plan\"}";
        }
    }

    @Tool(description = "CREATES a new plan in the product catalog. "
            + "SIDE EFFECT: Permanently creates a plan record. "
            + "Plans define pricing bundles that customers can subscribe to. "
            + "New plans start in DRAFT status. To activate, set status to ACTIVE via adminUpdatePlan "
            + "(requires name, description, priceAmount, intervalMonths, and at least one linked feature).")
    public String adminCreatePlan(
            @ToolParam(description = "Unique key for the plan, e.g. 'pro_tier'") String key,
            @ToolParam(description = "Display name, e.g. 'Pro Tier'") String name,
            @ToolParam(description = "Base price amount, e.g. '99.00'") String priceAmount,
            @ToolParam(description = "Optional description of the plan", required = false) String description,
            @ToolParam(description = "Billing interval in months, e.g. '1' for monthly, '12' for annual (default: '1')", required = false) String intervalMonths,
            @ToolParam(description = "Billing timing: 'IN_ADVANCE' or 'IN_ARREARS' (default: 'IN_ADVANCE')", required = false) String billingTiming) {
        try {
            PlanRequest request = new PlanRequest();
            request.setKey(key);
            request.setName(name);
            request.setPriceAmount(new BigDecimal(priceAmount));
            if (description != null) request.setDescription(description);
            request.setIntervalMonths(intervalMonths != null ? intervalMonths : "1");
            request.setBillingTiming(billingTiming != null ? billingTiming : "IN_ADVANCE");

            var result = planService.createPlans(getAccountId(), request);
            return objectMapper.writeValueAsString(result);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return "{\"error\": \"invalid_request\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize plan\"}";
        }
    }

    @Tool(description = "UPDATES an existing plan. "
            + "SIDE EFFECT: Modifies the plan record. Only provided fields are updated; "
            + "omitted fields remain unchanged. "
            + "CONSTRAINTS: Once a plan is ACTIVE, key, priceAmount, intervalMonths, and billingTiming "
            + "are locked and cannot be changed. ARCHIVED plans only allow status changes (back to ACTIVE). "
            + "All fields are mutable while the plan is in DRAFT.")
    public String adminUpdatePlan(
            @ToolParam(description = "The plan ID (UUID)") String planId,
            @ToolParam(description = "New unique key", required = false) String key,
            @ToolParam(description = "New display name", required = false) String name,
            @ToolParam(description = "New base price amount", required = false) String priceAmount,
            @ToolParam(description = "New description", required = false) String description,
            @ToolParam(description = "New billing interval in months", required = false) String intervalMonths,
            @ToolParam(description = "New billing timing: 'IN_ADVANCE' or 'IN_ARREARS'", required = false) String billingTiming,
            @ToolParam(description = "New status: 'DRAFT', 'ACTIVE', or 'ARCHIVED'. Allowed transitions: DRAFT→ACTIVE (requires name, description, priceAmount, intervalMonths, and at least one linked feature), ACTIVE→ARCHIVED, ARCHIVED→ACTIVE.", required = false) String status) {
        try {
            PlanRequest request = new PlanRequest();
            if (key != null) request.setKey(key);
            if (name != null) request.setName(name);
            if (priceAmount != null) request.setPriceAmount(new BigDecimal(priceAmount));
            if (description != null) request.setDescription(description);
            if (intervalMonths != null) request.setIntervalMonths(intervalMonths);
            if (billingTiming != null) request.setBillingTiming(billingTiming);
            if (status != null) request.setStatus(status);

            var result = planService.updatePlan(getAccountId(), planId, request);
            return objectMapper.writeValueAsString(result);
        } catch (ResourceNotFoundException e) {
            return "{\"error\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (IllegalArgumentException | IllegalStateException e) {
            return "{\"error\": \"invalid_request\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize plan\"}";
        }
    }

    private String getAccountId() {
        UserContext ctx = (UserContext) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return ctx.getAccountId();
    }
}
