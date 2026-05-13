package com.tansoflow.tansocore.model.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueBurnDownDto {
    private BigDecimal next30Days;
    private BigDecimal next60Days;
    private BigDecimal next90Days;
}
