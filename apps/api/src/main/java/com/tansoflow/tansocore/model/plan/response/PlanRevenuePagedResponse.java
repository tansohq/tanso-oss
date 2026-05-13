package com.tansoflow.tansocore.model.plan.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Paginated subscription revenue response")
public class PlanRevenuePagedResponse {
    @Schema(description = "List of subscription revenue items in the current page")
    private List<SubscriptionRevenueDto> items;

    @Schema(description = "Total number of elements across all pages")
    private long totalElements;

    @Schema(description = "Total number of pages")
    private int totalPages;

    @Schema(description = "Current page number (0-indexed)")
    private int page;

    @Schema(description = "Number of elements per page")
    private int size;

    @Schema(description = "Whether there are more pages after the current one")
    private boolean hasNext;

    public static PlanRevenuePagedResponse fromPage(Page<?> page, List<SubscriptionRevenueDto> items) {
        return PlanRevenuePagedResponse.builder()
                .items(items)
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .page(page.getNumber())
                .size(page.getSize())
                .hasNext(page.hasNext())
                .build();
    }
}
