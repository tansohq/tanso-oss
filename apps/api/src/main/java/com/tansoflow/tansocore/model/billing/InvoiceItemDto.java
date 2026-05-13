package com.tansoflow.tansocore.model.billing;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class InvoiceItemDto {
    public String id;
    public String invoiceId;
    private Instant createdAt;
    private Instant modifiedAt;
    private BigDecimal chargeAmount;
    @Size(max = 255)
    private String description;

}
