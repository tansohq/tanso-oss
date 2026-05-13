package com.tansoflow.tansocore.model.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Client-facing pricing details for a feature")
public class ClientFeaturePricingDto {
    @Schema(description = "Pricing model type", example = "usage")
    private String model;

    @Schema(description = "Price per unit of usage", example = "0.05")
    private BigDecimal pricePerUnit;

    @Schema(description = "Label for the usage unit", example = "messages")
    private String unitLabel;

    @Schema(description = "Maximum usage allowed", example = "10000")
    private BigDecimal maxUsage;

    @Schema(description = "Usage reset mode", example = "reset")
    private String resetMode;

    @Schema(description = "Graduated pricing tiers")
    private List<ClientPriceTierDto> tiers;
}
