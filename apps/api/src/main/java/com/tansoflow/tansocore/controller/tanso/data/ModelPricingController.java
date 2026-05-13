package com.tansoflow.tansocore.controller.tanso.data;

import com.tansoflow.tansocore.entity.ModelPricing;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.repository.ModelPricingRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tanso/model-pricing")
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
