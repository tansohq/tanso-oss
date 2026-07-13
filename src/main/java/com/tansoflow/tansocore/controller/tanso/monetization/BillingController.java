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
package com.tansoflow.tansocore.controller.tanso.monetization;

import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.model.billing.InvoiceDto;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.service.internal.monetization.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/monetization/billing")
@PreAuthorize("hasRole('TANSO_UI')")
@Tag(name = "Billing", description = "Billing and invoice management operations")
public class BillingController {
    private final InvoiceService invoiceService;

    @GetMapping("/invoices")
    @Operation(summary = "List invoices", description = "Retrieves all invoices for the authenticated account", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved invoices"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content)
    })
    public ResponseEntity<ApiResponse<List<InvoiceDto>>> getInvoices(@AuthenticationPrincipal UserContext userContext,
                                                                     @RequestParam(required = false, defaultValue = "false") String onlyDue) {
        ApiResponse<List<InvoiceDto>> apiResponse = ApiResponse.<List<InvoiceDto>>builder().success(true).build();
        if (onlyDue.equalsIgnoreCase("true")) {
            apiResponse.setData(invoiceService.retrieveOnlyDueInvoicesByAccount(userContext.getAccountId()));
        } else {
            apiResponse.setData(invoiceService.retrieveInvoicesByAccount(userContext.getAccountId()));
        }

        return ResponseEntity.ok(apiResponse);
    }

    @GetMapping("/invoices/{invoiceId}")
    @Operation(summary = "Get invoice details", description = "Retrieves a single invoice with line items for the authenticated account", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved invoice"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Invoice not found", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content)
    })
    public ResponseEntity<ApiResponse<InvoiceDto>> getInvoice(@AuthenticationPrincipal UserContext userContext, @PathVariable String invoiceId) {
        InvoiceDto invoiceDto = invoiceService.retrieveInvoiceById(invoiceId, userContext.getAccountId());
        ApiResponse<InvoiceDto> apiResponse = ApiResponse.<InvoiceDto>builder().success(true).data(invoiceDto).build();
        return ResponseEntity.ok(apiResponse);
    }

    @PatchMapping("/invoices/{invoiceId}")
    @Operation(summary = "Update an invoice", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "501", description = "Not yet implemented"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Invalid id supplied", content = @Content)})
    public ResponseEntity<ApiResponse<Void>> patchInvoice(@AuthenticationPrincipal UserContext userContext, @PathVariable String invoiceId) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_IMPLEMENTED)
                .body(ApiResponse.<Void>builder().success(false)
                        .error(new com.tansoflow.tansocore.model.response.Error("Invoice editing is not yet implemented")).build());
    }

}
