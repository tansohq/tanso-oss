package com.tansoflow.tansocore.repository;

import com.tansoflow.tansocore.entity.Invoice;
import com.tansoflow.tansocore.entity.InvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, UUID> {

    List<InvoiceItem> findAllByInvoice(Invoice invoice);

    @Modifying
    @Transactional
    @Query("DELETE FROM InvoiceItem ii WHERE ii.invoice = :invoice AND ii.description LIKE 'Usage for %'")
    void deleteUsageItemsByInvoice(Invoice invoice);

    @Modifying
    @Transactional
    @Query("DELETE FROM InvoiceItem ii WHERE ii.invoice = :invoice")
    void deleteAllByInvoice(Invoice invoice);
}