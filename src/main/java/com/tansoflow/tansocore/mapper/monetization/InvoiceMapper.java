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
package com.tansoflow.tansocore.mapper.monetization;

import com.tansoflow.tansocore.entity.Invoice;
import com.tansoflow.tansocore.entity.InvoiceItem;
import com.tansoflow.tansocore.model.billing.InvoiceDto;
import com.tansoflow.tansocore.model.billing.InvoiceItemDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface InvoiceMapper {
    @Mapping(target = "subscription.plan.intervalMonths", source = "subscription.plan.intervalMonths")
    @Mapping(target = "subscription.intervalMonths", source = "subscription.intervalMonths")
    @Mapping(target = "subscription.customer.customerReferenceId", source = "subscription.customer.externalClientCustomerId")
    @Mapping(target = "subscription.scheduledChange", ignore = true)
    @Mapping(target = "metadata", ignore = true)
    @Mapping(target = "subscription.metadata", ignore = true)
    @Mapping(target = "items", ignore = true)
    InvoiceDto invoiceEntityToInvoiceDto(Invoice invoice);

    List<InvoiceDto> invoiceEntityListToInvoiceDtoList(List<Invoice> invoiceEntities);

    @Mapping(target = "invoiceId", source = "invoice.id")
    InvoiceItemDto invoiceItemEntityToInvoiceItemDto(InvoiceItem invoiceItem);

    List<InvoiceItemDto> invoiceItemEntityListToInvoiceItemDtoList(List<InvoiceItem> invoiceItems);
}
