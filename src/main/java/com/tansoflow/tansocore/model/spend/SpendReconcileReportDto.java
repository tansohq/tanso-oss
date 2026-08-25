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
package com.tansoflow.tansocore.model.spend;

import com.tansoflow.tansocore.model.spend.type.VendorProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Three views of one period per vendor: what the price book says, what the vendor's report says, what the invoice says. */
@Getter
@Builder
public class SpendReconcileReportDto {
    private LocalDate from;
    /** Inclusive — invoices are dated, not timestamped. */
    private LocalDate to;
    private List<Row> rows;

    @Getter
    @Builder
    public static class Row {
        private VendorProvider provider;
        private BigDecimal meteredCents;
        @Schema(description = "True when some model was unpriced or a cache rate was missing, so metered is a floor or a ceiling, not a figure.")
        private boolean meteredIsEstimate;
        private BigDecimal vendorReportedCents;
        @Schema(description = "Sum of imported invoices whose period lies inside the window. Null when none.")
        private BigDecimal invoicedCents;
        private int invoiceCount;
        private BigDecimal meteredVsVendorCents;
        private BigDecimal vendorVsInvoiceCents;
    }
}
