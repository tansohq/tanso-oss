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
package com.tansoflow.tansocore.controller.client;

import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.model.client.ClientCreditGrantDto;
import com.tansoflow.tansocore.model.client.ClientCreditPoolDto;
import com.tansoflow.tansocore.model.credit.CreditTransactionDto;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.model.response.PaginatedResponse;
import com.tansoflow.tansocore.service.client.ClientCreditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/client/credits")
@PreAuthorize("hasRole('CLIENT')")
@Tag(name = "Client Credit", description = "Credit pool operations for client applications")
public class CreditClientController {
    private final ClientCreditService clientCreditService;

    @GetMapping("/{customerReferenceId}/pools")
    @Operation(summary = "List credit pools for a customer", description = "Retrieves all credit pools for a customer identified by their reference ID", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved credit pools"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Customer not found", content = @Content)
    })
    public ResponseEntity<ApiResponse<PaginatedResponse<ClientCreditPoolDto>>> getCreditPools(
            @Parameter(description = "Authenticated user context") @AuthenticationPrincipal UserContext userContext,
            @PathVariable("customerReferenceId") String customerReferenceId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        List<ClientCreditPoolDto> allPools = clientCreditService.getCreditPools(customerReferenceId, userContext.getAccountId());

        int total = allPools.size();
        int fromIndex = Math.min(offset, total);
        int toIndex = Math.min(offset + limit, total);
        List<ClientCreditPoolDto> page = allPools.subList(fromIndex, toIndex);

        PaginatedResponse<ClientCreditPoolDto> paginatedResponse = PaginatedResponse.<ClientCreditPoolDto>builder()
                .items(page)
                .pagination(PaginatedResponse.PaginationMeta.builder()
                        .total(total)
                        .limit(limit)
                        .offset(offset)
                        .hasMore(toIndex < total)
                        .build())
                .build();

        return ResponseEntity.ok(ApiResponse.<PaginatedResponse<ClientCreditPoolDto>>builder()
                .data(paginatedResponse).success(true).build());
    }

    @GetMapping("/{customerReferenceId}/pools/{poolId}")
    @Operation(summary = "Get a single credit pool", description = "Retrieves a specific credit pool with balance information", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved credit pool"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Customer or pool not found", content = @Content)
    })
    public ResponseEntity<ApiResponse<ClientCreditPoolDto>> getCreditPool(
            @AuthenticationPrincipal UserContext userContext,
            @PathVariable("customerReferenceId") String customerReferenceId,
            @PathVariable("poolId") String poolId) {
        ClientCreditPoolDto pool = clientCreditService.getCreditPool(customerReferenceId, poolId, userContext.getAccountId());

        return ResponseEntity.ok(ApiResponse.<ClientCreditPoolDto>builder()
                .data(pool).success(true).build());
    }

    @GetMapping("/{customerReferenceId}/pools/{poolId}/transactions")
    @Operation(summary = "List pool transactions", description = "Retrieves the transaction ledger for a specific credit pool", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved transactions"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Customer or pool not found", content = @Content)
    })
    public ResponseEntity<ApiResponse<PaginatedResponse<CreditTransactionDto>>> getPoolTransactions(
            @Parameter(description = "Authenticated user context") @AuthenticationPrincipal UserContext userContext,
            @PathVariable("customerReferenceId") String customerReferenceId,
            @PathVariable("poolId") String poolId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        List<CreditTransactionDto> allTransactions = clientCreditService.getPoolTransactions(customerReferenceId, poolId, userContext.getAccountId());

        int total = allTransactions.size();
        int fromIndex = Math.min(offset, total);
        int toIndex = Math.min(offset + limit, total);
        List<CreditTransactionDto> page = allTransactions.subList(fromIndex, toIndex);

        PaginatedResponse<CreditTransactionDto> paginatedResponse = PaginatedResponse.<CreditTransactionDto>builder()
                .items(page)
                .pagination(PaginatedResponse.PaginationMeta.builder()
                        .total(total)
                        .limit(limit)
                        .offset(offset)
                        .hasMore(toIndex < total)
                        .build())
                .build();

        return ResponseEntity.ok(ApiResponse.<PaginatedResponse<CreditTransactionDto>>builder()
                .data(paginatedResponse).success(true).build());
    }

    @GetMapping("/{customerReferenceId}/pools/{poolId}/grants")
    @Operation(summary = "List pool grants", description = "Retrieves all credit grants for a specific credit pool", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved grants"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Customer or pool not found", content = @Content)
    })
    public ResponseEntity<ApiResponse<PaginatedResponse<ClientCreditGrantDto>>> getPoolGrants(
            @Parameter(description = "Authenticated user context") @AuthenticationPrincipal UserContext userContext,
            @PathVariable("customerReferenceId") String customerReferenceId,
            @PathVariable("poolId") String poolId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        List<ClientCreditGrantDto> allGrants = clientCreditService.getPoolGrants(customerReferenceId, poolId, userContext.getAccountId());

        int total = allGrants.size();
        int fromIndex = Math.min(offset, total);
        int toIndex = Math.min(offset + limit, total);
        List<ClientCreditGrantDto> page = allGrants.subList(fromIndex, toIndex);

        PaginatedResponse<ClientCreditGrantDto> paginatedResponse = PaginatedResponse.<ClientCreditGrantDto>builder()
                .items(page)
                .pagination(PaginatedResponse.PaginationMeta.builder()
                        .total(total)
                        .limit(limit)
                        .offset(offset)
                        .hasMore(toIndex < total)
                        .build())
                .build();

        return ResponseEntity.ok(ApiResponse.<PaginatedResponse<ClientCreditGrantDto>>builder()
                .data(paginatedResponse).success(true).build());
    }
}
