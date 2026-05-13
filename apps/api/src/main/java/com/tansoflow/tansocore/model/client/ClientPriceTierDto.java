package com.tansoflow.tansocore.model.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Client-facing graduated pricing tier")
public class ClientPriceTierDto {
    @Schema(description = "Upper limit for this tier (number or \"inf\")", example = "100")
    private Object upTo;

    @Schema(description = "Price per unit in this tier", example = "0.50")
    private BigDecimal pricePerUnit;

    @Schema(description = "Flat fee for this tier", example = "5.00")
    private BigDecimal flatFee;
}
