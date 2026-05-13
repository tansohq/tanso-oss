package com.tansoflow.tansocore.service.internal.monetization;

import com.tansoflow.tansocore.entity.Invoice;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.model.billing.CreateInvoiceParams;
import com.tansoflow.tansocore.model.billing.InvoiceDto;
import com.tansoflow.tansocore.model.billing.type.InvoiceStatus;
import com.tansoflow.tansocore.model.billing.type.InvoiceType;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface InvoiceService {

    record SyncLineItem(BigDecimal chargeAmount, String description) {}
    @Transactional
    Invoice createNewInvoice(CreateInvoiceParams p);

    InvoiceDto createNewInvoice(Subscription subscription, LocalDate dueDate, InvoiceStatus status, InvoiceType type);

    @Transactional
    InvoiceDto createNewInvoice(Subscription subscription, LocalDate dueDate, BigDecimal amount, InvoiceStatus status, Instant periodStart, Instant periodEnd);

    InvoiceDto createNewInvoice(Subscription subscription, LocalDate dueDate, InvoiceStatus status);

    List<InvoiceDto> retrieveInvoicesByExternalClientCustomerId(String externalClientCustomerId, String accountId);

    List<InvoiceDto> retrieveInvoicesByAccount(String accountId);

    List<InvoiceDto> retrieveOnlyDueInvoicesByAccount(String accountId);

    void processPendingInvoices();

    void processDueInvoices();

    Invoice retrieveCurrentlyDueBySubscription(Subscription subscription);

    Invoice retrieveInvoiceByInvoiceIdAndAccount(String invoiceId, String accountId);

    InvoiceDto retrieveInvoiceById(String invoiceId, String accountId);

    void markInvoiceAsPaid(String invoiceId);

    void markInvoiceAsPaid(Invoice invoice);

    boolean existsInvoiceForPeriod(Subscription sub, Instant start, Instant end);

    void processCancelledInvoices();

    @Transactional
    Invoice createAdjustmentInvoice(Plan subscribedPlan, Plan newPlan, Subscription currentSubscription, BigDecimal ratio, Instant now);

    boolean hasPastDueInvoice(Subscription sub);

    @Transactional
    void voidOutstandingInvoicesForSubscription(Subscription subscription);

    @Transactional
    Invoice createCreditInvoice(Subscription subscription, BigDecimal creditAmount, Instant periodStart, Instant periodEnd);

    BigDecimal calculateUsageChargeForPeriod(Subscription subscription, Instant periodStart, Instant periodEnd);

    boolean planHasAccumulateModeFeatures(Plan plan);

    Invoice retrieveInitialInvoiceForSubscription(Subscription subscription);

    @Transactional
    void syncInvoiceFromStripe(Invoice tansoInvoice, BigDecimal amount,
                               Instant periodStart, Instant periodEnd,
                               List<SyncLineItem> lineItems);
}
