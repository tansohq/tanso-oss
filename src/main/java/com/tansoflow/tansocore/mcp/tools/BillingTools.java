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
import com.tansoflow.tansocore.service.internal.monetization.InvoiceService;
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
public class BillingTools {

    private final InvoiceService invoiceService;
    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;

    @Tool(description = "List all invoices for a customer. "
            + "Returns invoice dates, amounts, status (PENDING, DUE, PAID, VOID), and line items.")
    public String listCustomerInvoices(
            @ToolParam(description = "The customer's external reference ID (your system's user/customer ID)") String customerReferenceId) {
        try {
            var invoices = invoiceService.retrieveInvoicesByExternalClientCustomerId(
                    customerReferenceId, getAccountId());
            return objectMapper.writeValueAsString(invoices);
        } catch (ResourceNotFoundException e) {
            return "{\"error\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize invoices\"}";
        }
    }

    @Tool(description = "MARKS an invoice as paid. "
            + "SIDE EFFECT: Updates the invoice status and may trigger Stripe synchronization. "
            + "Use this for manual payment reconciliation. You MUST set confirmAction to true to execute.")
    public String markInvoicePaid(
            @ToolParam(description = "The invoice ID to mark as paid") String invoiceId,
            @ToolParam(description = "Must be true to execute this action") boolean confirmAction) {
        if (!confirmAction) {
            return "{\"error\": \"confirmation_required\", \"message\": \"Set confirmAction to true to execute this action\"}";
        }
        try {
            subscriptionService.subscriptionInvoicePaid(invoiceId, getAccountId());
            return "{\"success\": true, \"message\": \"Invoice " + invoiceId + " marked as paid\"}";
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
