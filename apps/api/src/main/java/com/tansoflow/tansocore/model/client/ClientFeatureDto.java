package com.tansoflow.tansocore.model.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.UUID;

@Data
@Schema(description = "Client-facing feature information with pricing type")
public class ClientFeatureDto {
    @Schema(description = "Unique identifier of the feature")
    private UUID id;

    @Schema(description = "Display name of the feature", example = "API Access")
    private String name;

    @Schema(description = "Unique key for the feature", example = "api_access")
    private String key;

    @Schema(description = "Detailed description of the feature")
    private String description;

    @Schema(description = "Pricing type: included, usage_based, or graduated", example = "usage_based")
    private String pricingType;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Pricing details, null for included features")
    private ClientFeaturePricingDto pricing;
}
