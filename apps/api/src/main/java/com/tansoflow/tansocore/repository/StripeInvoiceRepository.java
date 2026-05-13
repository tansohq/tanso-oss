package com.tansoflow.tansocore.repository;

import com.tansoflow.tansocore.entity.Invoice;
import com.tansoflow.tansocore.entity.StripeInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface StripeInvoiceRepository extends JpaRepository <StripeInvoice, UUID>{
    boolean existsStripeInvoiceByInvoice(Invoice invoice);

    StripeInvoice findStripeInvoiceByInvoice(Invoice invoice);

    @Query("SELECT si FROM StripeInvoice si WHERE si.stripeInvoiceExternalId = :invoiceId")
    StripeInvoice findStripeInvoiceByStripeInvoiceId(String invoiceId);

    StripeInvoice findStripeInvoiceByStripeInvoiceExternalId(String invoiceId);

    boolean existsStripeInvoiceByStripeInvoiceExternalId(String stripeInvoiceExternalId);
}
