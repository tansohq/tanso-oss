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
package com.tansoflow.tansocore.controller.tanso.data;

import com.tansoflow.tansocore.entity.ModelPricing;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.repository.ModelPricingRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tanso/model-pricing")
@PreAuthorize("hasRole('TANSO_UI')")
@Tag(name = "Model Pricing", description = "Global AI model pricing reference")
public class ModelPricingController {

    private final ModelPricingRepository modelPricingRepository;

    @GetMapping
    @Operation(summary = "List all model prices", description = "Returns the global model pricing table used for auto-cost calculation")
    public ResponseEntity<ApiResponse<List<ModelPricing>>> listPricing() {
        List<ModelPricing> pricing = modelPricingRepository.findAll();
        return ResponseEntity.ok(ApiResponse.<List<ModelPricing>>builder()
                .success(true).data(pricing).build());
    }
}
