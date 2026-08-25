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
import com.tansoflow.tansocore.model.spend.VendorConnectionDto;
import com.tansoflow.tansocore.model.spend.request.CreateVendorConnectionRequest;
import com.tansoflow.tansocore.service.internal.spend.VendorConnectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Build side, console-only. Every route under /api/v1/spend is the operator's
 * own AI spend — nothing here is reachable with a client or customer key.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/spend/connections")
@PreAuthorize("hasRole('TANSO_UI')")
@ConditionalOnProperty(name = "app.modules.build.enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "Spend — Vendor Connections",
        description = "Vendor admin credentials Tanso pulls the operator's internal AI usage and cost from")
public class VendorConnectionController {
    private final VendorConnectionService vendorConnectionService;

    @GetMapping
    @Operation(summary = "List connected vendor accounts (hints only)", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<List<VendorConnectionDto>>> list(@AuthenticationPrincipal UserContext userContext) {
        List<VendorConnectionDto> connections = vendorConnectionService.list(userContext.getAccountId());
        return ResponseEntity.ok(ApiResponse.<List<VendorConnectionDto>>builder().data(connections).success(true).build());
    }

    @PostMapping
    @Operation(summary = "Connect a vendor account",
            description = "The admin key is stored encrypted and never returned; only its last four characters are.",
            security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<VendorConnectionDto>> create(
            @AuthenticationPrincipal UserContext userContext,
            @Valid @RequestBody CreateVendorConnectionRequest request) {
        VendorConnectionDto created = vendorConnectionService.create(userContext.getAccountId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<VendorConnectionDto>builder().data(created).success(true).build());
    }

    @DeleteMapping("/{connectionId}")
    @Operation(summary = "Disconnect a vendor account", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal UserContext userContext, @PathVariable String connectionId) {
        vendorConnectionService.delete(userContext.getAccountId(), connectionId);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).build());
    }
}
