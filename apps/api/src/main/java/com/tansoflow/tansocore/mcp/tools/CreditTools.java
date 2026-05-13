package com.tansoflow.tansocore.mcp.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.service.client.ClientCreditService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mcp.enabled", havingValue = "true")
public class CreditTools {

    private final ClientCreditService clientCreditService;
    private final ObjectMapper objectMapper;

    @Tool(description = "List all credit pools for a customer. "
            + "Returns each pool's name, total credits, used credits, and remaining balance.")
    public String getCreditPools(
            @ToolParam(description = "The customer's external reference ID (your system's user/customer ID)") String customerReferenceId) {
        try {
            var pools = clientCreditService.getCreditPools(customerReferenceId, getAccountId());
            return objectMapper.writeValueAsString(pools);
        } catch (ResourceNotFoundException e) {
            return "{\"error\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize credit pools\"}";
        }
    }

    @Tool(description = "Get the balance of a specific credit pool. "
            + "Returns the pool's total credits, used credits, and remaining balance.")
    public String getCreditBalance(
            @ToolParam(description = "The customer's external reference ID (your system's user/customer ID)") String customerReferenceId,
            @ToolParam(description = "The credit pool ID") String poolId) {
        try {
            var pool = clientCreditService.getCreditPool(customerReferenceId, poolId, getAccountId());
            return objectMapper.writeValueAsString(pool);
        } catch (ResourceNotFoundException e) {
            return "{\"error\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize credit pool\"}";
        }
    }

    @Tool(description = "List all transactions (grants, deductions, expirations, reversals) for a customer's credit pool. "
            + "Returns each transaction with amount, balance before/after, type, and timestamp.")
    public String getPoolTransactions(
            @ToolParam(description = "The customer's external reference ID (your system's user/customer ID)") String customerReferenceId,
            @ToolParam(description = "The credit pool ID") String poolId) {
        try {
            var transactions = clientCreditService.getPoolTransactions(customerReferenceId, poolId, getAccountId());
            return objectMapper.writeValueAsString(transactions);
        } catch (ResourceNotFoundException e) {
            return "{\"error\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize transactions\"}";
        }
    }

    @Tool(description = "List all credit grants for a customer's credit pool. "
            + "Returns each grant with amount, remaining balance, type, expiry date, and description.")
    public String getPoolGrants(
            @ToolParam(description = "The customer's external reference ID (your system's user/customer ID)") String customerReferenceId,
            @ToolParam(description = "The credit pool ID") String poolId) {
        try {
            var grants = clientCreditService.getPoolGrants(customerReferenceId, poolId, getAccountId());
            return objectMapper.writeValueAsString(grants);
        } catch (ResourceNotFoundException e) {
            return "{\"error\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize grants\"}";
        }
    }

    private String getAccountId() {
        UserContext ctx = (UserContext) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return ctx.getAccountId();
    }
}
