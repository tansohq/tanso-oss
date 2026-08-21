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
package com.tansoflow.tansocore.mcp.tools.customer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tansoflow.tansocore.auth.CustomerAccessGuard;
import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.model.credit.CreditPurchaseResult;
import com.tansoflow.tansocore.model.credit.request.CreditPurchaseRequest;
import com.tansoflow.tansocore.model.entitlement.api.EntitlementEvaluationRequest;
import com.tansoflow.tansocore.model.entitlement.api.UsageContext;
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.model.subscription.request.ClientSubscriptionRequest;
import com.tansoflow.tansocore.service.client.ClientEntitlementService;
import com.tansoflow.tansocore.service.client.ClientPlanService;
import com.tansoflow.tansocore.service.client.CreditPurchaseService;
import com.tansoflow.tansocore.service.client.UsageForecastService;
import com.tansoflow.tansocore.service.internal.monetization.CreditPriceService;
import com.tansoflow.tansocore.service.internal.monetization.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * The curated MCP surface for an end customer's agent (ck_ key): discover
 * pricing, pre-flight cost, watch usage, and buy — pinned to its own
 * customer. Tenant sk_ keys can call the same tools by passing an explicit
 * customerReferenceId. Money-spending tools require confirmAction: true.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mcp.enabled", havingValue = "true")
public class AgentCustomerTools {

    private final CustomerAccessGuard customerAccessGuard;
    private final ClientPlanService clientPlanService;
    private final CreditPriceService creditPriceService;
    private final ClientEntitlementService clientEntitlementService;
    private final UsageForecastService usageForecastService;
    private final SubscriptionService subscriptionService;
    private final CreditPurchaseService creditPurchaseService;
    private final com.tansoflow.tansocore.service.internal.account.KeyBudgetService keyBudgetService;
    private final ObjectMapper objectMapper;

    private static final String CONFIRMATION_REQUIRED =
            "{\"success\": false, \"error\": {\"code\": \"confirmation_required\", "
                    + "\"message\": \"This tool spends money. Set confirmAction to true after your principal approved it.\"}}";

    @Tool(description = "List the plans this product offers: prices, billing interval, features, and included credits. "
            + "Use this before subscribing or comparing cost.")
    public String listPlans() {
        try {
            return objectMapper.writeValueAsString(
                    clientPlanService.retrieveActivePlansWithPricing(context().getAccountId()));
        } catch (JsonProcessingException e) {
            return serializationError("plans");
        }
    }

    @Tool(description = "Current price of one credit per denomination, from the operator's price book. "
            + "Use this to convert credit quotes into money.")
    public String getCreditPrices() {
        try {
            return objectMapper.writeValueAsString(
                    creditPriceService.getCurrentPrices(context().getAccountId()));
        } catch (JsonProcessingException e) {
            return serializationError("credit prices");
        }
    }

    @Tool(description = "Pre-flight a feature call: whether it is allowed, remaining quota, and a credit quote "
            + "(estimated credits and cost) for the usage you are about to run. Does not record usage.")
    public String checkEntitlement(
            @ToolParam(description = "Feature key, e.g. 'ai.chat'") String featureKey,
            @ToolParam(description = "Units you intend to consume (default 1)", required = false) Double usageUnits,
            @ToolParam(description = "Model name for model-specific credit weights", required = false) String model,
            @ToolParam(description = "Customer reference — omit with a customer-scoped key", required = false) String customerReferenceId) {
        try {
            UserContext ctx = context();
            String ref = requireRef(ctx, customerReferenceId);
            EntitlementEvaluationRequest request = new EntitlementEvaluationRequest();
            request.setCustomerReferenceId(ref);
            request.setFeatureKey(featureKey);
            if (usageUnits != null || model != null) {
                UsageContext usage = new UsageContext();
                usage.setUsageUnits(usageUnits != null ? BigDecimal.valueOf(usageUnits) : BigDecimal.ONE);
                usage.setModel(model);
                request.setUsage(usage);
            }
            return objectMapper.writeValueAsString(
                    clientEntitlementService.evaluateEntitlement(ctx.getAccountId(), request));
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return forbidden(e);
        } catch (ResourceNotFoundException e) {
            return notFound(e);
        } catch (JsonProcessingException e) {
            return serializationError("entitlement");
        }
    }

    @Tool(description = "Current-period usage per feature with an end-of-period projection, plus credit balances "
            + "with average burn, projected depletion date, and credit price. The burndown view.")
    public String getUsageForecast(
            @ToolParam(description = "Customer reference — omit with a customer-scoped key", required = false) String customerReferenceId) {
        try {
            UserContext ctx = context();
            String ref = requireRef(ctx, customerReferenceId);
            return objectMapper.writeValueAsString(usageForecastService.getUsage(ref, ctx.getAccountId()));
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return forbidden(e);
        } catch (ResourceNotFoundException e) {
            return notFound(e);
        } catch (JsonProcessingException e) {
            return serializationError("usage forecast");
        }
    }

    @Tool(description = "This key's own spend budget: credit and money limits, how much has been used in "
            + "the current window, and when the window resets. Check before a large call or purchase — "
            + "exceeding the budget is rejected with error code 'budget_exceeded'.")
    public String getMyBudget() {
        try {
            UserContext ctx = context();
            if (ctx.getApiKeyId() == null) {
                return "{\"error\":\"budget_unavailable\",\"message\":\"Budgets apply to customer-scoped (ck_) keys only\"}";
            }
            return objectMapper.writeValueAsString(keyBudgetService.getBudget(
                    ctx.getAccountId(), ctx.getCustomerReferenceId(), ctx.getApiKeyId().toString()));
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return forbidden(e);
        } catch (ResourceNotFoundException e) {
            return notFound(e);
        } catch (JsonProcessingException e) {
            return serializationError("key budget");
        }
    }

    @Tool(description = "SPENDS MONEY. Subscribes the customer to a plan. With a saved or supplied payment method "
            + "the subscription is created immediately; otherwise the result carries a checkout URL for your "
            + "principal plus a checkoutSessionId to poll with getCheckoutStatus.")
    public String subscribePlan(
            @ToolParam(description = "Plan ID (UUID) from listPlans") String planId,
            @ToolParam(description = "Stripe payment method (pm_...); defaults to the saved one", required = false) String paymentMethodId,
            @ToolParam(description = "Customer reference — omit with a customer-scoped key", required = false) String customerReferenceId,
            @ToolParam(description = "Must be true to execute — this creates a paid subscription") boolean confirmAction) {
        if (!confirmAction) {
            return CONFIRMATION_REQUIRED;
        }
        try {
            UserContext ctx = context();
            customerAccessGuard.requirePurchaseScope(ctx);
            ClientSubscriptionRequest request = new ClientSubscriptionRequest();
            request.setPlanId(planId);
            request.setCustomerReferenceId(requireRef(ctx, customerReferenceId));
            request.setPaymentMethodId(paymentMethodId);
            return objectMapper.writeValueAsString(
                    subscriptionService.clientSubscribeCustomer(request, ctx.getAccountId()));
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return forbidden(e);
        } catch (ResourceNotFoundException e) {
            return notFound(e);
        } catch (IllegalArgumentException e) {
            return invalidRequest(e);
        } catch (JsonProcessingException e) {
            return serializationError("subscription");
        }
    }

    @Tool(description = "SPENDS MONEY. Buys credits at the current price book rate. With a saved or supplied "
            + "payment method the charge happens now; otherwise the result carries a checkout URL and a "
            + "checkoutSessionId to poll. Subject to the operator's agent spend cap.")
    public String purchaseCredits(
            @ToolParam(description = "Credit pool ID (UUID) from getUsageForecast or the credits API") String creditPoolId,
            @ToolParam(description = "How many credits to buy") double credits,
            @ToolParam(description = "Stripe payment method (pm_...); defaults to the saved one", required = false) String paymentMethodId,
            @ToolParam(description = "Customer reference — omit with a customer-scoped key", required = false) String customerReferenceId,
            @ToolParam(description = "Must be true to execute — this charges money") boolean confirmAction) {
        if (!confirmAction) {
            return CONFIRMATION_REQUIRED;
        }
        try {
            UserContext ctx = context();
            customerAccessGuard.requirePurchaseScope(ctx);
            CreditPurchaseRequest request = new CreditPurchaseRequest();
            request.setCreditPoolId(creditPoolId);
            request.setCredits(BigDecimal.valueOf(credits));
            request.setPaymentMethodId(paymentMethodId);
            CreditPurchaseResult result = creditPurchaseService.purchase(
                    request, requireRef(ctx, customerReferenceId), ctx.getAccountId());
            return objectMapper.writeValueAsString(result);
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return forbidden(e);
        } catch (ResourceNotFoundException e) {
            return notFound(e);
        } catch (IllegalArgumentException e) {
            return invalidRequest(e);
        } catch (JsonProcessingException e) {
            return serializationError("credit purchase");
        }
    }

    private UserContext context() {
        return (UserContext) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    /** ck_ keys are pinned to their own customer; sk_ keys must name one explicitly. */
    private String requireRef(UserContext ctx, String requested) {
        String ref = customerAccessGuard.resolveCustomerRef(ctx, requested);
        if (ref == null) {
            throw new IllegalArgumentException("customerReferenceId is required for tenant API keys");
        }
        return ref;
    }

    private String serializationError(String what) {
        return "{\"success\": false, \"error\": {\"code\": \"serialization_error\", \"message\": \"Failed to serialize "
                + what + "\"}}";
    }

    private String forbidden(Exception e) {
        return "{\"success\": false, \"error\": {\"code\": \"forbidden\", \"message\": \"" + e.getMessage() + "\"}}";
    }

    private String notFound(Exception e) {
        return "{\"success\": false, \"error\": {\"code\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}}";
    }

    private String invalidRequest(Exception e) {
        return "{\"success\": false, \"error\": {\"code\": \"invalid_request\", \"message\": \"" + e.getMessage() + "\"}}";
    }
}
