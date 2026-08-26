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
package com.tansoflow.tansocore.controller.tanso.spend;

import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.model.spend.VendorInvoiceDto;
import com.tansoflow.tansocore.model.spend.type.VendorProvider;
import com.tansoflow.tansocore.service.internal.spend.VendorInvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/spend/invoices")
@PreAuthorize("hasRole('TANSO_UI')")
@ConditionalOnProperty(name = "app.modules.build.enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "Spend — Vendor Invoices", description = "The vendor's bill as finance sees it, imported from CSV, for reconciliation")
public class SpendInvoiceController {
    private final VendorInvoiceService vendorInvoiceService;

    @GetMapping
    @Operation(summary = "List imported vendor invoices", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<List<VendorInvoiceDto>>> list(@AuthenticationPrincipal UserContext userContext) {
        List<VendorInvoiceDto> invoices = vendorInvoiceService.list(userContext.getAccountId());
        return ResponseEntity.ok(ApiResponse.<List<VendorInvoiceDto>>builder().data(invoices).success(true).build());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import a vendor invoice from CSV",
            description = "CSV with a header row. Required columns: description, amount (major units, e.g. dollars). "
                    + "Optional: kind (TOKEN, SEAT, TOOL, OTHER), model, quantity. periodEnd is inclusive.",
            security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<VendorInvoiceDto>> importCsv(
            @AuthenticationPrincipal UserContext userContext,
            @RequestParam VendorProvider provider,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd,
            @RequestParam(defaultValue = "USD") String currency,
            @RequestParam("file") MultipartFile file) {
        try {
            VendorInvoiceDto created = vendorInvoiceService.importCsv(userContext.getAccountId(), provider,
                    periodStart, periodEnd, currency, file.getOriginalFilename(), file.getInputStream());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.<VendorInvoiceDto>builder().data(created).success(true).build());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DeleteMapping("/{invoiceId}")
    @Operation(summary = "Remove an imported invoice", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal UserContext userContext, @PathVariable String invoiceId) {
        vendorInvoiceService.delete(userContext.getAccountId(), invoiceId);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).build());
    }
}
