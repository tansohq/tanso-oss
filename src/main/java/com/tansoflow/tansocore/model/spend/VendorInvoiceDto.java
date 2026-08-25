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

import com.tansoflow.tansocore.model.spend.type.InvoiceLineKind;
import com.tansoflow.tansocore.model.spend.type.VendorProvider;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class VendorInvoiceDto {
    private String id;
    private VendorProvider provider;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String currency;
    private BigDecimal totalCents;
    private String importedFrom;
    private Instant createdAt;
    private List<Line> lines;

    @Getter
    @Builder
    public static class Line {
        private String description;
        private InvoiceLineKind kind;
        private String model;
        private BigDecimal quantity;
        private BigDecimal amountCents;
    }
}
