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
