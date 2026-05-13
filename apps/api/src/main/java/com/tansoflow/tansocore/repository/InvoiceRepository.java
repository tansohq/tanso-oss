package com.tansoflow.tansocore.repository;

import com.tansoflow.tansocore.entity.Invoice;
import com.tansoflow.tansocore.entity.Subscription;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    @Query(value = "SELECT * FROM invoices WHERE account_id = :accountId AND deleted_at IS NULL", nativeQuery = true)
    List<Invoice> getInvoicesByAccount_Id(UUID accountId);

    List<Invoice> getInvoicesBySubscriptionIdIn(Collection<UUID> subscription_id);

    @Query(value = "SELECT * FROM invoices WHERE account_id = :accountId AND status = :status AND deleted_at IS NULL", nativeQuery = true)
    List<Invoice> getInvoicesByAccount_IdAndStatus(UUID accountId, String status);

    List<Invoice> getInvoicesByStatus(String status);

    @Query("""
        SELECT i FROM Invoice i
        WHERE i.status = :status
          AND i.deletedAt IS NULL
          AND NOT EXISTS (
            SELECT 1 FROM AccountSetting acs
            WHERE acs.accounts.id = i.account.id
              AND acs.stripeMode IN ('FULL_SYNC', 'STRIPE_INTEGRATION', 'STRIPE_DRIVEN')
          )
    """)
    List<Invoice> getInvoicesByStatusExcludingFullSync(String status);

    @Query("""
        SELECT i FROM Invoice i
        WHERE i.status = :status
          AND i.deletedAt IS NULL
          AND NOT EXISTS (
            SELECT 1 FROM AccountSetting acs
            WHERE acs.accounts.id = i.account.id
              AND acs.stripeMode IN ('FULL_SYNC', 'STRIPE_INTEGRATION', 'STRIPE_DRIVEN')
          )
    """)
    Page<Invoice> getInvoicesByStatusExcludingFullSyncPaged(String status, Pageable pageable);

    Page<Invoice> getInvoicesByStatus(String status, Pageable pageable);

    @Query(value = "SELECT * FROM invoices WHERE invoice_id = :invoiceId AND account_id = :accountId AND deleted_at IS NULL", nativeQuery = true)
    Invoice findByIdAndAccount(UUID invoiceId, UUID accountId);

    @Query("SELECT invoice FROM Invoice invoice WHERE invoice.subscription.id = :subscriptionId " +
            "AND invoice.status = :status ORDER BY invoice.dueDate")
    Invoice getUpcomingDueInvoiceBySubscriptionIdAndStatus(UUID subscriptionId, String status);

    boolean existsBySubscriptionAndInvoicePeriodStartAndInvoicePeriodEndAndType(
            @NotNull Subscription subscription, Instant invoicePeriodStart, Instant invoicePeriodEnd, @Size(max = 32) String type
    );

    boolean existsBySubscriptionAndStatus(@NotNull Subscription subscription, @Size(max = 50) @NotNull String status);

    @Query("SELECT invoice FROM Invoice invoice WHERE invoice.subscription = :subscription AND invoice.type = 'IN_ADVANCE_INITIAL'")
    Invoice getInitialInvoiceForSubscription(Subscription subscription);

    @Query("SELECT invoice FROM Invoice invoice WHERE invoice.subscription = :subscription AND invoice.type IN ('REGULAR', 'IN_ADVANCE_INITIAL') AND invoice.status = 'DUE'")
    Invoice getCurrentlyDueInvoiceBySubscription(Subscription subscription);

    @Query("SELECT i FROM Invoice i WHERE i.subscription = :subscription AND i.status IN ('DUE', 'PENDING')")
    List<Invoice> findOutstandingInvoicesBySubscription(Subscription subscription);
}
