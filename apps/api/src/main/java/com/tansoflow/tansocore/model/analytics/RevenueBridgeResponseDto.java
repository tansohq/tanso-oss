package com.tansoflow.tansocore.model.analytics;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Historical revenue bridge showing period-over-period revenue changes")
public class RevenueBridgeResponseDto {
    @Schema(description = "Revenue breakdown by billing period, ordered chronologically")
    private List<RevenueBridgePeriodDto> periods;
}
