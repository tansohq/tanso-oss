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
package com.tansoflow.tansocore.service.internal.spend;

import com.tansoflow.tansocore.model.spend.VendorInvoiceDto;
import com.tansoflow.tansocore.model.spend.type.VendorProvider;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

public interface VendorInvoiceService {
    /**
     * CSV with a header row. Required columns: description, amount (in the
     * currency's major unit, e.g. dollars). Optional: kind (TOKEN, SEAT, TOOL,
     * OTHER), model, quantity.
     */
    VendorInvoiceDto importCsv(String accountId, VendorProvider provider, LocalDate periodStart, LocalDate periodEnd,
                               String currency, String fileName, InputStream csv);

    List<VendorInvoiceDto> list(String accountId);

    void delete(String accountId, String invoiceId);
}
