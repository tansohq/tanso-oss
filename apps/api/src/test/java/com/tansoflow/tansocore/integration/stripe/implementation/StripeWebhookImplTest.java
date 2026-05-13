package com.tansoflow.tansocore.integration.stripe.implementation;

import com.stripe.model.Invoice;
import com.stripe.model.InvoiceLineItem;
import com.stripe.model.InvoiceLineItemCollection;
import com.stripe.model.Price;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.SubscriptionItemCollection;
import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.entity.StripeCustomer;
import com.tansoflow.tansocore.entity.StripeInvoice;
import com.tansoflow.tansocore.entity.StripeProduct;
import com.tansoflow.tansocore.entity.StripeSubscription;
import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.model.billing.InvoiceDto;
import com.tansoflow.tansocore.model.billing.type.InvoiceStatus;
import com.tansoflow.tansocore.model.customer.CustomerDto;
import com.tansoflow.tansocore.repository.AccountRepository;
import com.tansoflow.tansocore.repository.AccountSettingRepository;
import com.tansoflow.tansocore.repository.ExternalApiKeyRepository;
import com.tansoflow.tansocore.repository.StripeCustomerRepository;
import com.tansoflow.tansocore.repository.StripeProductPlansRepository;
import com.tansoflow.tansocore.repository.StripeSubscriptionRepository;
import com.tansoflow.tansocore.repository.StripeWebhookEventRepository;
import com.tansoflow.tansocore.repository.SubscriptionRepository;
import com.tansoflow.tansocore.service.internal.account.CustomerService;
import com.tansoflow.tansocore.service.internal.monetization.EntitlementService;
import com.tansoflow.tansocore.service.internal.monetization.InvoiceService;
import com.tansoflow.tansocore.service.internal.monetization.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeWebhookImplTest {

    @InjectMocks
    private StripeWebhookImpl stripeWebhook;

    @Mock
    private StripeSyncServiceImpl stripeSyncService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ExternalApiKeyRepository externalApiKeyRepository;

    @Mock
    private InvoiceService invoiceService;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private AccountSettingRepository accountSettingRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private EntitlementService entitlementService;

    @Mock
    private StripeWebhookEventRepository stripeWebhookEventRepository;

    @Mock
    private StripeSubscriptionRepository stripeSubscriptionRepository;

    @Mock
    private com.tansoflow.tansocore.service.internal.monetization.CreditService creditService;

    @Mock
    private com.tansoflow.tansocore.repository.CreditPoolSubscriptionRepository creditPoolSubscriptionRepository;

    @Mock
    private StripeCustomerRepository stripeCustomerRepository;

    @Mock
    private StripeProductPlansRepository stripeProductPlansRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CustomerService customerService;

    private Account account;
    private Customer customer;
    private Plan plan;
    private Subscription subscription;
    private String accountId;

    @BeforeEach
    void setUp() {
        account = new Account();
        account.setId(UUID.randomUUID());
        accountId = account.getId().toString();

        customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setAccount(account);

        plan = new Plan();
        plan.setId(UUID.randomUUID());

        subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setCustomer(customer);
        subscription.setPlan(plan);
        subscription.setIsActive(true);
        subscription.setAccount(account);
        subscription.setCurrentPeriodStart(Instant.parse("2025-01-01T00:00:00Z"));
        subscription.setCurrentPeriodEnd(Instant.parse("2025-02-01T00:00:00Z"));
    }

    // ── Period advancement tests ──────────────────────────────────────────────

    @Test
    void handleFullSyncInvoiceCreated_UpdatesSubscriptionPeriodFromStripeInvoice() {
        Invoice stripeInvoice = createStripeInvoiceWithPeriod(
                "inv_001", 5000L,
                Instant.parse("2025-02-01T00:00:00Z").getEpochSecond(),
                Instant.parse("2025-03-01T00:00:00Z").getEpochSecond());
        stripeInvoice.setMetadata(Map.of("tanso_subscription_id", subscription.getId().toString()));

        when(stripeSyncService.stripeInvoiceLinked("inv_001")).thenReturn(false);
        when(subscriptionService.getSubscriptionById(subscription.getId().toString(), accountId))
                .thenReturn(subscription);
        when(invoiceService.planHasAccumulateModeFeatures(plan)).thenReturn(false);
        when(invoiceService.createNewInvoice(any(Subscription.class), any(), any(BigDecimal.class), any(InvoiceStatus.class), any(Instant.class), any(Instant.class)))
                .thenReturn(createInvoiceDto(UUID.randomUUID().toString()));

        stripeWebhook.handleFullSyncInvoiceCreated(stripeInvoice, accountId);

        // Verify period was updated on subscription
        assertEquals(Instant.parse("2025-02-01T00:00:00Z"), subscription.getCurrentPeriodStart());
        assertEquals(Instant.parse("2025-03-01T00:00:00Z"), subscription.getCurrentPeriodEnd());
        verify(subscriptionRepository).save(subscription);
    }

    @Test
    void handleFullSyncInvoiceCreated_AccumulateMode_UsesCorrectPeriodForChargeCalculation() {
        Instant newPeriodStart = Instant.parse("2025-02-01T00:00:00Z");
        Instant newPeriodEnd = Instant.parse("2025-03-01T00:00:00Z");

        Invoice stripeInvoice = createStripeInvoiceWithPeriod(
                "inv_002", 0L,
                newPeriodStart.getEpochSecond(), newPeriodEnd.getEpochSecond());
        stripeInvoice.setMetadata(Map.of("tanso_subscription_id", subscription.getId().toString()));

        when(stripeSyncService.stripeInvoiceLinked("inv_002")).thenReturn(false);
        when(subscriptionService.getSubscriptionById(subscription.getId().toString(), accountId))
                .thenReturn(subscription);
        when(invoiceService.planHasAccumulateModeFeatures(plan)).thenReturn(true);
        when(invoiceService.calculateUsageChargeForPeriod(eq(subscription), eq(newPeriodStart), eq(newPeriodEnd)))
                .thenReturn(new BigDecimal("35.00"));
        when(invoiceService.createNewInvoice(any(Subscription.class), any(), any(BigDecimal.class), any(InvoiceStatus.class), any(Instant.class), any(Instant.class)))
                .thenReturn(createInvoiceDto(UUID.randomUUID().toString()));

        stripeWebhook.handleFullSyncInvoiceCreated(stripeInvoice, accountId);

        // Verify charge was calculated with the NEW period dates, not the stale ones
        verify(invoiceService).calculateUsageChargeForPeriod(subscription, newPeriodStart, newPeriodEnd);
    }

    @Test
    void handleFullSyncSubscriptionUpdated_SyncsPeriodDatesFromItems() {
        // In Stripe SDK v31+, period is per-SubscriptionItem, not on the Subscription itself
        SubscriptionItem subItem = new SubscriptionItem();
        subItem.setCurrentPeriodStart(Instant.parse("2025-03-01T00:00:00Z").getEpochSecond());
        subItem.setCurrentPeriodEnd(Instant.parse("2025-04-01T00:00:00Z").getEpochSecond());

        SubscriptionItemCollection items = new SubscriptionItemCollection();
        items.setData(List.of(subItem));

        com.stripe.model.Subscription stripeSub = new com.stripe.model.Subscription();
        stripeSub.setMetadata(Map.of("tanso_subscription_id", subscription.getId().toString()));
        stripeSub.setItems(items);
        stripeSub.setStatus("active");

        when(subscriptionService.getSubscriptionById(subscription.getId().toString(), accountId))
                .thenReturn(subscription);

        stripeWebhook.handleFullSyncSubscriptionUpdated(stripeSub, accountId);

        assertEquals(Instant.parse("2025-03-01T00:00:00Z"), subscription.getCurrentPeriodStart());
        assertEquals(Instant.parse("2025-04-01T00:00:00Z"), subscription.getCurrentPeriodEnd());
        verify(subscriptionRepository).save(subscription);
    }

    // ── invoice.paid race condition tests ─────────────────────────────────────

    @Test
    void handleFullSyncInvoicePaid_AccumulateMode_AlreadyPaid_CreatesMirrorDirectly() {
        Invoice stripeInvoice = createStripeInvoiceWithPeriod(
                "inv_003", 3500L,
                Instant.parse("2025-02-01T00:00:00Z").getEpochSecond(),
                Instant.parse("2025-03-01T00:00:00Z").getEpochSecond());
        stripeInvoice.setMetadata(Map.of("tanso_subscription_id", subscription.getId().toString()));
        stripeInvoice.setStatus("paid");
        stripeInvoice.setAmountPaid(3500L);

        UUID tansoInvoiceId = UUID.randomUUID();
        com.tansoflow.tansocore.entity.Invoice tansoInvoice = new com.tansoflow.tansocore.entity.Invoice();
        tansoInvoice.setId(tansoInvoiceId);

        StripeInvoice stripeInvoiceEntity = new StripeInvoice();
        stripeInvoiceEntity.setInvoice(tansoInvoice);

        // Invoice not yet linked (race condition scenario)
        when(stripeSyncService.stripeInvoiceLinked("inv_003"))
                .thenReturn(false)   // First call in handleFullSyncInvoicePaid: not linked
                .thenReturn(true);   // After createMirrorInvoiceFromPaidStripeInvoice: now linked

        when(subscriptionService.getSubscriptionById(subscription.getId().toString(), accountId))
                .thenReturn(subscription);
        when(invoiceService.planHasAccumulateModeFeatures(plan)).thenReturn(true);
        when(invoiceService.createNewInvoice(any(Subscription.class), any(), any(BigDecimal.class), any(InvoiceStatus.class), any(Instant.class), any(Instant.class)))
                .thenReturn(createInvoiceDto(tansoInvoiceId.toString()));
        when(stripeSyncService.retrieveStripeInvoiceLinkedData("inv_003")).thenReturn(stripeInvoiceEntity);

        stripeWebhook.handleFullSyncInvoicePaid(stripeInvoice, accountId);

        // Verify: should create mirror directly using amountPaid ($35.00), NOT try to manipulate draft
        verify(invoiceService).createNewInvoice(
                eq(subscription), any(), eq(new BigDecimal("35.00")), eq(InvoiceStatus.DUE),
                any(Instant.class), any(Instant.class));
        verify(invoiceService).markInvoiceAsPaid(tansoInvoiceId.toString());
    }

    @Test
    void handleFullSyncInvoicePaid_NonAccumulateMode_FallsBackToInvoiceCreated() {
        Invoice stripeInvoice = createStripeInvoiceWithPeriod(
                "inv_004", 5000L,
                Instant.parse("2025-02-01T00:00:00Z").getEpochSecond(),
                Instant.parse("2025-03-01T00:00:00Z").getEpochSecond());
        stripeInvoice.setMetadata(Map.of("tanso_subscription_id", subscription.getId().toString()));
        stripeInvoice.setStatus("paid");
        stripeInvoice.setAmountPaid(5000L);
        stripeInvoice.setAmountDue(5000L);

        UUID tansoInvoiceId = UUID.randomUUID();
        com.tansoflow.tansocore.entity.Invoice tansoInvoice = new com.tansoflow.tansocore.entity.Invoice();
        tansoInvoice.setId(tansoInvoiceId);

        StripeInvoice stripeInvoiceEntity = new StripeInvoice();
        stripeInvoiceEntity.setInvoice(tansoInvoice);

        // 3 calls to stripeInvoiceLinked:
        // 1. handleFullSyncInvoicePaid: not linked → enters backfill branch
        // 2. handleFullSyncInvoiceCreated: not linked → proceeds to create mirror
        // 3. (implicit) retrieveStripeInvoiceLinkedData used after creation
        when(stripeSyncService.stripeInvoiceLinked("inv_004"))
                .thenReturn(false)   // handleFullSyncInvoicePaid check
                .thenReturn(false);  // handleFullSyncInvoiceCreated idempotency check

        when(subscriptionService.getSubscriptionById(subscription.getId().toString(), accountId))
                .thenReturn(subscription);
        when(invoiceService.planHasAccumulateModeFeatures(plan)).thenReturn(false);
        when(invoiceService.createNewInvoice(any(Subscription.class), any(), any(BigDecimal.class), any(InvoiceStatus.class), any(Instant.class), any(Instant.class)))
                .thenReturn(createInvoiceDto(tansoInvoiceId.toString()));
        when(stripeSyncService.retrieveStripeInvoiceLinkedData("inv_004")).thenReturn(stripeInvoiceEntity);

        stripeWebhook.handleFullSyncInvoicePaid(stripeInvoice, accountId);

        // For non-accumulate mode, should fall through to handleFullSyncInvoiceCreated as before
        verify(invoiceService).markInvoiceAsPaid(tansoInvoiceId.toString());
    }

    // ── Accumulate mode base price tests ────────────────────────────────────────

    @Test
    void handleFullSyncInvoiceCreated_AccumulateMode_WithBasePrice_AddsBasePriceLineItem() throws Exception {
        Instant newPeriodStart = Instant.parse("2025-02-01T00:00:00Z");
        Instant newPeriodEnd = Instant.parse("2025-03-01T00:00:00Z");

        plan.setPriceAmount(new BigDecimal("50.00"));
        plan.setName("Pro Plan");

        Invoice stripeInvoice = createStripeInvoiceWithPeriod(
                "inv_base_01", 0L,
                newPeriodStart.getEpochSecond(), newPeriodEnd.getEpochSecond());
        stripeInvoice.setMetadata(Map.of("tanso_subscription_id", subscription.getId().toString()));

        when(stripeSyncService.stripeInvoiceLinked("inv_base_01")).thenReturn(false);
        when(subscriptionService.getSubscriptionById(subscription.getId().toString(), accountId))
                .thenReturn(subscription);
        when(invoiceService.planHasAccumulateModeFeatures(plan)).thenReturn(true);
        when(invoiceService.calculateUsageChargeForPeriod(eq(subscription), eq(newPeriodStart), eq(newPeriodEnd)))
                .thenReturn(new BigDecimal("35.00"));
        when(invoiceService.createNewInvoice(any(Subscription.class), any(), any(BigDecimal.class), any(InvoiceStatus.class), any(Instant.class), any(Instant.class)))
                .thenReturn(createInvoiceDto(UUID.randomUUID().toString()));

        stripeWebhook.handleFullSyncInvoiceCreated(stripeInvoice, accountId);

        // Verify base price line item added to Stripe draft
        verify(stripeSyncService).addLineItemToDraftInvoice(
                eq("inv_base_01"), any(UUID.class), eq(new BigDecimal("50.00")), any(String.class),
                contains("Plan base price"));

        // Verify usage charge line item also added
        verify(stripeSyncService).addLineItemToDraftInvoice(
                eq("inv_base_01"), any(UUID.class), eq(new BigDecimal("35.00")), any(String.class),
                contains("Accumulated usage charge"));

        // Verify tanso mirror invoice total = base (50) + usage (35) = 85
        verify(invoiceService).createNewInvoice(
                eq(subscription), any(), eq(new BigDecimal("85.00")), eq(InvoiceStatus.DUE),
                eq(newPeriodStart), eq(newPeriodEnd));
    }

    @Test
    void handleFullSyncInvoiceCreated_AccumulateMode_WithBasePriceAndCreditOffset_AllThreeLineItems() throws Exception {
        Instant newPeriodStart = Instant.parse("2025-02-01T00:00:00Z");
        Instant newPeriodEnd = Instant.parse("2025-03-01T00:00:00Z");

        plan.setPriceAmount(new BigDecimal("50.00"));
        plan.setName("Pro Plan");

        Invoice stripeInvoice = createStripeInvoiceWithPeriod(
                "inv_base_02", 0L,
                newPeriodStart.getEpochSecond(), newPeriodEnd.getEpochSecond());
        stripeInvoice.setMetadata(Map.of("tanso_subscription_id", subscription.getId().toString()));

        when(stripeSyncService.stripeInvoiceLinked("inv_base_02")).thenReturn(false);
        when(subscriptionService.getSubscriptionById(subscription.getId().toString(), accountId))
                .thenReturn(subscription);
        when(invoiceService.planHasAccumulateModeFeatures(plan)).thenReturn(true);
        when(invoiceService.calculateUsageChargeForPeriod(eq(subscription), eq(newPeriodStart), eq(newPeriodEnd)))
                .thenReturn(new BigDecimal("35.00"));

        // Mock credit pool returning a credit offset of 10.00
        var creditPoolSub = new com.tansoflow.tansocore.entity.CreditPoolSubscription();
        var creditPool = new com.tansoflow.tansocore.entity.CreditPool();
        creditPool.setId(UUID.randomUUID());
        creditPoolSub.setCreditPool(creditPool);
        when(creditPoolSubscriptionRepository.findBySubscriptionIdOrderByDrawPriority(subscription.getId()))
                .thenReturn(List.of(creditPoolSub));
        when(creditService.applyCreditOffset(eq(creditPool.getId()), any(BigDecimal.class), eq(subscription.getId()), any(UUID.class), any(String.class)))
                .thenReturn(new BigDecimal("10.00"));

        when(invoiceService.createNewInvoice(any(Subscription.class), any(), any(BigDecimal.class), any(InvoiceStatus.class), any(Instant.class), any(Instant.class)))
                .thenReturn(createInvoiceDto(UUID.randomUUID().toString()));

        stripeWebhook.handleFullSyncInvoiceCreated(stripeInvoice, accountId);

        // Verify all three line items: base price, usage charge (net of credit), credit offset
        verify(stripeSyncService).addLineItemToDraftInvoice(
                eq("inv_base_02"), any(UUID.class), eq(new BigDecimal("50.00")), any(String.class),
                contains("Plan base price"));
        verify(stripeSyncService).addLineItemToDraftInvoice(
                eq("inv_base_02"), any(UUID.class), eq(new BigDecimal("25.00")), any(String.class),
                contains("Accumulated usage charge"));
        verify(stripeSyncService).addLineItemToDraftInvoice(
                eq("inv_base_02"), any(UUID.class), eq(new BigDecimal("-10.00")), any(String.class),
                contains("Credit applied"));

        // Verify tanso mirror invoice total = base (50) + net usage (25) = 75
        verify(invoiceService).createNewInvoice(
                eq(subscription), any(), eq(new BigDecimal("75.00")), eq(InvoiceStatus.DUE),
                eq(newPeriodStart), eq(newPeriodEnd));
    }

    @Test
    void handleFullSyncInvoiceCreated_AccumulateMode_ZeroBasePrice_NoBasePriceLineItem() throws Exception {
        Instant newPeriodStart = Instant.parse("2025-02-01T00:00:00Z");
        Instant newPeriodEnd = Instant.parse("2025-03-01T00:00:00Z");

        plan.setPriceAmount(BigDecimal.ZERO);

        Invoice stripeInvoice = createStripeInvoiceWithPeriod(
                "inv_base_03", 0L,
                newPeriodStart.getEpochSecond(), newPeriodEnd.getEpochSecond());
        stripeInvoice.setMetadata(Map.of("tanso_subscription_id", subscription.getId().toString()));

        when(stripeSyncService.stripeInvoiceLinked("inv_base_03")).thenReturn(false);
        when(subscriptionService.getSubscriptionById(subscription.getId().toString(), accountId))
                .thenReturn(subscription);
        when(invoiceService.planHasAccumulateModeFeatures(plan)).thenReturn(true);
        when(invoiceService.calculateUsageChargeForPeriod(eq(subscription), eq(newPeriodStart), eq(newPeriodEnd)))
                .thenReturn(new BigDecimal("35.00"));
        when(invoiceService.createNewInvoice(any(Subscription.class), any(), any(BigDecimal.class), any(InvoiceStatus.class), any(Instant.class), any(Instant.class)))
                .thenReturn(createInvoiceDto(UUID.randomUUID().toString()));

        stripeWebhook.handleFullSyncInvoiceCreated(stripeInvoice, accountId);

        // Verify NO base price line item was added
        verify(stripeSyncService, never()).addLineItemToDraftInvoice(
                any(), any(UUID.class), any(), any(), contains("Plan base price"));

        // Verify usage charge still added and mirror uses just net charge
        verify(stripeSyncService).addLineItemToDraftInvoice(
                eq("inv_base_03"), any(UUID.class), eq(new BigDecimal("35.00")), any(String.class),
                contains("Accumulated usage charge"));
        verify(invoiceService).createNewInvoice(
                eq(subscription), any(), eq(new BigDecimal("35.00")), eq(InvoiceStatus.DUE),
                eq(newPeriodStart), eq(newPeriodEnd));
    }

    // ── STRIPE_DRIVEN webhook handler tests ─────────────────────────────────

    @Test
    void handleStripeDrivenSubscriptionCreated_CreatesSubscriptionAndEntitlements() {
        com.stripe.model.Subscription stripeSub = createStripeSubscription("sub_sd_001", "cus_sd_001", "prod_sd_001", "active");

        StripeCustomer stripeCustomer = new StripeCustomer();
        stripeCustomer.setCustomer(customer);

        StripeProduct stripeProduct = new StripeProduct();
        stripeProduct.setPlan(plan);

        when(stripeSubscriptionRepository.existsStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_001"))
                .thenReturn(false);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(stripeCustomerRepository.findByStripeCustomerExternalIdAndAccount("cus_sd_001", account))
                .thenReturn(stripeCustomer);
        when(stripeProductPlansRepository.findByStripeProductExternalIdAndAccount("prod_sd_001", account))
                .thenReturn(stripeProduct);

        stripeWebhook.handleStripeDrivenSubscriptionCreated(stripeSub, accountId);

        verify(subscriptionRepository).save(any(Subscription.class));
        verify(stripeSubscriptionRepository).save(any(StripeSubscription.class));
        verify(entitlementService).processEntitlementsForSubscription(any(Subscription.class));
    }

    @Test
    void handleStripeDrivenSubscriptionCreated_AlreadyMapped_Skips() {
        com.stripe.model.Subscription stripeSub = createStripeSubscription("sub_sd_002", "cus_sd_001", "prod_sd_001", "active");

        when(stripeSubscriptionRepository.existsStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_002"))
                .thenReturn(true);

        stripeWebhook.handleStripeDrivenSubscriptionCreated(stripeSub, accountId);

        verify(subscriptionRepository, never()).save(any());
        verify(entitlementService, never()).processEntitlementsForSubscription(any());
    }

    @Test
    void handleStripeDrivenSubscriptionCreated_NoMappedCustomer_Skips() {
        com.stripe.model.Subscription stripeSub = createStripeSubscription("sub_sd_003", "cus_unknown", "prod_sd_001", "active");

        when(stripeSubscriptionRepository.existsStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_003"))
                .thenReturn(false);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(stripeCustomerRepository.findByStripeCustomerExternalIdAndAccount("cus_unknown", account))
                .thenReturn(null);

        stripeWebhook.handleStripeDrivenSubscriptionCreated(stripeSub, accountId);

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void handleStripeDrivenSubscriptionCreated_NoMappedPlan_Skips() {
        com.stripe.model.Subscription stripeSub = createStripeSubscription("sub_sd_004", "cus_sd_001", "prod_unmapped", "active");

        StripeCustomer stripeCustomer = new StripeCustomer();
        stripeCustomer.setCustomer(customer);

        when(stripeSubscriptionRepository.existsStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_004"))
                .thenReturn(false);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(stripeCustomerRepository.findByStripeCustomerExternalIdAndAccount("cus_sd_001", account))
                .thenReturn(stripeCustomer);
        when(stripeProductPlansRepository.findByStripeProductExternalIdAndAccount("prod_unmapped", account))
                .thenReturn(null);

        stripeWebhook.handleStripeDrivenSubscriptionCreated(stripeSub, accountId);

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void handleStripeDrivenSubscriptionUpdated_SyncsPeriodAndStatus() {
        com.stripe.model.Subscription stripeSub = createStripeSubscription("sub_sd_010", "cus_sd_001", "prod_sd_001", "canceled");

        StripeSubscription bridge = new StripeSubscription();
        bridge.setSubscription(subscription);

        when(stripeSubscriptionRepository.findStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_010"))
                .thenReturn(bridge);

        stripeWebhook.handleStripeDrivenSubscriptionUpdated(stripeSub);

        assertFalse(subscription.getIsActive());
        assertNotNull(subscription.getCancelledAt());
        verify(subscriptionRepository).save(subscription);
        verify(entitlementService).processEntitlementRevokeForSubscription(subscription);
    }

    @Test
    void handleStripeDrivenSubscriptionUpdated_ReactivatesWhenActive() {
        subscription.setIsActive(false);
        com.stripe.model.Subscription stripeSub = createStripeSubscription("sub_sd_011", "cus_sd_001", "prod_sd_001", "active");

        StripeSubscription bridge = new StripeSubscription();
        bridge.setSubscription(subscription);

        when(stripeSubscriptionRepository.findStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_011"))
                .thenReturn(bridge);

        stripeWebhook.handleStripeDrivenSubscriptionUpdated(stripeSub);

        assertEquals(true, subscription.getIsActive());
        verify(entitlementService).processEntitlementsForSubscription(subscription);
        verify(subscriptionRepository).save(subscription);
    }

    @Test
    void handleStripeDrivenSubscriptionUpdated_NoMapping_Skips() {
        com.stripe.model.Subscription stripeSub = createStripeSubscription("sub_sd_012", "cus_sd_001", "prod_sd_001", "active");

        when(stripeSubscriptionRepository.findStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_012"))
                .thenReturn(null);

        stripeWebhook.handleStripeDrivenSubscriptionUpdated(stripeSub);

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void handleStripeDrivenSubscriptionDeleted_DeactivatesAndRevokes() {
        com.stripe.model.Subscription stripeSub = createStripeSubscription("sub_sd_020", "cus_sd_001", "prod_sd_001", "canceled");

        StripeSubscription bridge = new StripeSubscription();
        bridge.setSubscription(subscription);

        when(stripeSubscriptionRepository.findStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_020"))
                .thenReturn(bridge);

        stripeWebhook.handleStripeDrivenSubscriptionDeleted(stripeSub);

        assertFalse(subscription.getIsActive());
        assertNotNull(subscription.getCancelledAt());
        assertNotNull(subscription.getCancelEffectiveAt());
        verify(subscriptionRepository).save(subscription);
        verify(entitlementService).processEntitlementRevokeForSubscription(subscription);
    }

    @Test
    void handleStripeDrivenSubscriptionDeleted_NoMapping_Skips() {
        com.stripe.model.Subscription stripeSub = createStripeSubscription("sub_sd_021", "cus_sd_001", "prod_sd_001", "canceled");

        when(stripeSubscriptionRepository.findStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_021"))
                .thenReturn(null);

        stripeWebhook.handleStripeDrivenSubscriptionDeleted(stripeSub);

        verify(subscriptionRepository, never()).save(any());
        verify(entitlementService, never()).processEntitlementRevokeForSubscription(any());
    }

    // ── STRIPE_DRIVEN invoice mirroring tests ────────────────────────────────

    @Test
    void handleStripeDrivenInvoiceCreated_MirrorsInvoice() {
        Invoice stripeInvoice = createStripeInvoiceWithSubscription("inv_sd_mirror_001", "sub_sd_030");
        stripeInvoice.setAmountDue(5000L);
        stripeInvoice.setStatus("open");

        StripeSubscription bridge = new StripeSubscription();
        bridge.setSubscription(subscription);

        when(stripeSubscriptionRepository.findStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_030"))
                .thenReturn(bridge);
        when(stripeSyncService.stripeInvoiceLinked("inv_sd_mirror_001")).thenReturn(false);
        when(invoiceService.createNewInvoice(any(Subscription.class), any(), any(BigDecimal.class), any(InvoiceStatus.class), any(Instant.class), any(Instant.class)))
                .thenReturn(createInvoiceDto(UUID.randomUUID().toString()));

        stripeWebhook.handleStripeDrivenInvoiceCreated(stripeInvoice, accountId);

        verify(invoiceService).createNewInvoice(eq(subscription), any(), eq(new BigDecimal("50.00")), eq(InvoiceStatus.DUE),
                any(Instant.class), any(Instant.class));
        verify(stripeSyncService).saveStripeInvoice(eq("inv_sd_mirror_001"), any(String.class), eq(accountId));
    }

    @Test
    void handleStripeDrivenInvoiceCreated_AlreadyLinked_Skips() {
        Invoice stripeInvoice = createStripeInvoiceWithSubscription("inv_sd_mirror_002", "sub_sd_030");

        StripeSubscription bridge = new StripeSubscription();
        bridge.setSubscription(subscription);

        when(stripeSubscriptionRepository.findStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_030"))
                .thenReturn(bridge);
        when(stripeSyncService.stripeInvoiceLinked("inv_sd_mirror_002")).thenReturn(true);

        stripeWebhook.handleStripeDrivenInvoiceCreated(stripeInvoice, accountId);

        verify(invoiceService, never()).createNewInvoice(any(Subscription.class), any(), any(BigDecimal.class), any(InvoiceStatus.class), any(Instant.class), any(Instant.class));
    }

    @Test
    void handleStripeDrivenInvoicePaid_MarksAsPaidAndGrantsCredits() {
        UUID tansoInvoiceId = UUID.randomUUID();
        com.tansoflow.tansocore.entity.Invoice tansoInvoice = new com.tansoflow.tansocore.entity.Invoice();
        tansoInvoice.setId(tansoInvoiceId);

        StripeInvoice stripeInvoiceEntity = new StripeInvoice();
        stripeInvoiceEntity.setInvoice(tansoInvoice);

        Invoice stripeInvoice = createStripeInvoiceWithSubscription("inv_sd_paid_001", "sub_sd_030");
        stripeInvoice.setAmountDue(5000L);
        stripeInvoice.setStatus("paid");

        StripeSubscription bridge = new StripeSubscription();
        bridge.setSubscription(subscription);

        when(stripeSubscriptionRepository.findStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_030"))
                .thenReturn(bridge);
        when(stripeSyncService.stripeInvoiceLinked("inv_sd_paid_001")).thenReturn(true);
        when(stripeSyncService.retrieveStripeInvoiceLinkedData("inv_sd_paid_001")).thenReturn(stripeInvoiceEntity);

        stripeWebhook.handleStripeDrivenInvoicePaid(stripeInvoice, accountId);

        verify(invoiceService).markInvoiceAsPaid(tansoInvoiceId.toString());
        verify(creditService).processCreditGrantsForSubscription(subscription);
    }

    @Test
    void handleStripeDrivenInvoicePaid_NotYetLinked_CreatesMirrorThenMarksPaid() {
        UUID tansoInvoiceId = UUID.randomUUID();
        com.tansoflow.tansocore.entity.Invoice tansoInvoice = new com.tansoflow.tansocore.entity.Invoice();
        tansoInvoice.setId(tansoInvoiceId);

        StripeInvoice stripeInvoiceEntity = new StripeInvoice();
        stripeInvoiceEntity.setInvoice(tansoInvoice);

        Invoice stripeInvoice = createStripeInvoiceWithSubscription("inv_sd_race_001", "sub_sd_030");
        stripeInvoice.setAmountDue(3000L);
        stripeInvoice.setStatus("paid");

        StripeSubscription bridge = new StripeSubscription();
        bridge.setSubscription(subscription);

        when(stripeSubscriptionRepository.findStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_030"))
                .thenReturn(bridge);
        // Not linked on first check (invoice.paid before invoice.created), linked after mirror creation
        when(stripeSyncService.stripeInvoiceLinked("inv_sd_race_001"))
                .thenReturn(false)
                .thenReturn(false);
        when(invoiceService.createNewInvoice(any(Subscription.class), any(), any(BigDecimal.class), any(InvoiceStatus.class), any(Instant.class), any(Instant.class)))
                .thenReturn(createInvoiceDto(tansoInvoiceId.toString()));
        when(stripeSyncService.retrieveStripeInvoiceLinkedData("inv_sd_race_001")).thenReturn(stripeInvoiceEntity);

        stripeWebhook.handleStripeDrivenInvoicePaid(stripeInvoice, accountId);

        // Verify mirror was created first, then marked as paid
        verify(invoiceService).createNewInvoice(eq(subscription), any(), eq(new BigDecimal("30.00")), any(InvoiceStatus.class),
                any(Instant.class), any(Instant.class));
        verify(invoiceService).markInvoiceAsPaid(tansoInvoiceId.toString());
        verify(creditService).processCreditGrantsForSubscription(subscription);
    }

    @Test
    void handleStripeDrivenInvoicePaid_NoSubscription_Skips() {
        Invoice stripeInvoice = new Invoice();
        stripeInvoice.setId("inv_sd_002");
        // No parent/subscription

        stripeWebhook.handleStripeDrivenInvoicePaid(stripeInvoice, accountId);

        verifyNoInteractions(creditService);
    }

    @Test
    void handleStripeDrivenInvoicePaid_NoMapping_Skips() {
        Invoice stripeInvoice = createStripeInvoiceWithSubscription("inv_sd_003", "sub_sd_unmapped");

        when(stripeSubscriptionRepository.findStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_unmapped"))
                .thenReturn(null);

        stripeWebhook.handleStripeDrivenInvoicePaid(stripeInvoice, accountId);

        verifyNoInteractions(creditService);
    }

    @Test
    void handleStripeDrivenInvoicePaymentFailed_MarksPastDue() {
        UUID tansoInvoiceId = UUID.randomUUID();
        com.tansoflow.tansocore.entity.Invoice tansoInvoice = new com.tansoflow.tansocore.entity.Invoice();
        tansoInvoice.setId(tansoInvoiceId);
        tansoInvoice.setStatus(InvoiceStatus.DUE.name());

        StripeInvoice stripeInvoiceEntity = new StripeInvoice();
        stripeInvoiceEntity.setInvoice(tansoInvoice);

        Invoice stripeInvoice = createStripeInvoiceWithSubscription("inv_sd_fail_001", "sub_sd_030");

        when(stripeSyncService.stripeInvoiceLinked("inv_sd_fail_001")).thenReturn(true);
        when(stripeSyncService.retrieveStripeInvoiceLinkedData("inv_sd_fail_001")).thenReturn(stripeInvoiceEntity);

        stripeWebhook.handleStripeDrivenInvoicePaymentFailed(stripeInvoice);

        assertEquals(InvoiceStatus.PAST_DUE.name(), tansoInvoice.getStatus());
    }

    @Test
    void handleStripeDrivenSubscriptionCreated_IdempotentWhenBridgeExists() {
        com.stripe.model.Subscription stripeSub = createStripeSubscription("sub_sd_idempotent", "cus_sd_001", "prod_sd_001", "active");

        when(stripeSubscriptionRepository.existsStripeSubscriptionByStripeSubscriptionExternalId("sub_sd_idempotent"))
                .thenReturn(true);

        stripeWebhook.handleStripeDrivenSubscriptionCreated(stripeSub, accountId);

        verify(subscriptionRepository, never()).save(any());
        verify(entitlementService, never()).processEntitlementsForSubscription(any());
    }

    // ── STRIPE_DRIVEN customer auto-creation tests ─────────────────────────

    @Test
    void handleStripeDrivenCustomerCreated_CreatesCustomerAndBridge() {
        com.stripe.model.Customer stripeCustomer = new com.stripe.model.Customer();
        stripeCustomer.setId("cus_new_001");
        stripeCustomer.setEmail("jane@example.com");
        stripeCustomer.setName("Jane Doe");
        stripeCustomer.setPhone("+1555123456");

        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(stripeCustomerRepository.existsByStripeCustomerExternalIdAndAccount("cus_new_001", account))
                .thenReturn(false);
        when(customerService.createCustomer(eq(account), any(CustomerDto.class))).thenReturn(customer);

        stripeWebhook.handleStripeDrivenCustomerCreated(stripeCustomer, accountId);

        verify(customerService).createCustomer(eq(account), argThat(dto ->
                "cus_new_001".equals(dto.getCustomerReferenceId())
                        && "jane@example.com".equals(dto.getEmail())
                        && "Jane".equals(dto.getFirstName())
                        && "Doe".equals(dto.getLastName())
                        && "+1555123456".equals(dto.getPhoneNumber())
        ));
        verify(stripeCustomerRepository).save(any(StripeCustomer.class));
    }

    @Test
    void handleStripeDrivenCustomerCreated_AlreadyMapped_Skips() {
        com.stripe.model.Customer stripeCustomer = new com.stripe.model.Customer();
        stripeCustomer.setId("cus_existing");

        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(stripeCustomerRepository.existsByStripeCustomerExternalIdAndAccount("cus_existing", account))
                .thenReturn(true);

        stripeWebhook.handleStripeDrivenCustomerCreated(stripeCustomer, accountId);

        verify(customerService, never()).createCustomer(any(Account.class), any(CustomerDto.class));
        verify(stripeCustomerRepository, never()).save(any(StripeCustomer.class));
    }

    @Test
    void handleStripeDrivenCustomerCreated_NoEmail_UsesFallback() {
        com.stripe.model.Customer stripeCustomer = new com.stripe.model.Customer();
        stripeCustomer.setId("cus_no_email");
        stripeCustomer.setName("Bob Smith");

        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(stripeCustomerRepository.existsByStripeCustomerExternalIdAndAccount("cus_no_email", account))
                .thenReturn(false);
        when(customerService.createCustomer(eq(account), any(CustomerDto.class))).thenReturn(customer);

        stripeWebhook.handleStripeDrivenCustomerCreated(stripeCustomer, accountId);

        verify(customerService).createCustomer(eq(account), argThat(dto ->
                "cus_no_email@stripe.placeholder".equals(dto.getEmail())
        ));
    }

    @Test
    void handleStripeDrivenCustomerCreated_NoName_UsesUnknown() {
        com.stripe.model.Customer stripeCustomer = new com.stripe.model.Customer();
        stripeCustomer.setId("cus_no_name");
        stripeCustomer.setEmail("anon@example.com");

        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(stripeCustomerRepository.existsByStripeCustomerExternalIdAndAccount("cus_no_name", account))
                .thenReturn(false);
        when(customerService.createCustomer(eq(account), any(CustomerDto.class))).thenReturn(customer);

        stripeWebhook.handleStripeDrivenCustomerCreated(stripeCustomer, accountId);

        verify(customerService).createCustomer(eq(account), argThat(dto ->
                "Unknown".equals(dto.getFirstName()) && "".equals(dto.getLastName())
        ));
    }

    @Test
    void handleStripeDrivenSubscriptionCreated_UnmappedCustomer_AutoCreates() {
        com.stripe.model.Subscription stripeSub = createStripeSubscription("sub_auto_001", "cus_auto_001", "prod_sd_001", "active");

        StripeProduct stripeProduct = new StripeProduct();
        stripeProduct.setPlan(plan);

        // First call: no mapping; after auto-creation: mapping exists
        StripeCustomer createdBridge = new StripeCustomer();
        createdBridge.setCustomer(customer);

        when(stripeSubscriptionRepository.existsStripeSubscriptionByStripeSubscriptionExternalId("sub_auto_001"))
                .thenReturn(false);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(stripeCustomerRepository.findByStripeCustomerExternalIdAndAccount("cus_auto_001", account))
                .thenReturn(null)            // first lookup in subscription handler
                .thenReturn(createdBridge);  // after auto-creation re-query
        // Idempotency check inside handleStripeDrivenCustomerCreated
        when(stripeCustomerRepository.existsByStripeCustomerExternalIdAndAccount("cus_auto_001", account))
                .thenReturn(false);
        when(customerService.createCustomer(eq(account), any(CustomerDto.class))).thenReturn(customer);
        when(stripeProductPlansRepository.findByStripeProductExternalIdAndAccount("prod_sd_001", account))
                .thenReturn(stripeProduct);

        // Use mockStatic for Customer.retrieve
        try (var mockedStatic = org.mockito.Mockito.mockStatic(com.stripe.model.Customer.class)) {
            com.stripe.model.Customer stripeCustomerObj = new com.stripe.model.Customer();
            stripeCustomerObj.setId("cus_auto_001");
            stripeCustomerObj.setEmail("auto@example.com");
            stripeCustomerObj.setName("Auto User");
            mockedStatic.when(() -> com.stripe.model.Customer.retrieve("cus_auto_001"))
                    .thenReturn(stripeCustomerObj);

            stripeWebhook.handleStripeDrivenSubscriptionCreated(stripeSub, accountId);
        }

        // Verify customer was auto-created
        verify(customerService).createCustomer(eq(account), any(CustomerDto.class));
        // Verify subscription was still created
        verify(subscriptionRepository).save(any(Subscription.class));
        verify(stripeSubscriptionRepository).save(any(StripeSubscription.class));
        verify(entitlementService).processEntitlementsForSubscription(any(Subscription.class));
    }

    // ── Helper methods ────────────────────────────────────────────────────────

    private com.stripe.model.Subscription createStripeSubscription(String subId, String customerId, String productId, String status) {
        Price price = new Price();
        price.setProduct(productId);

        SubscriptionItem subItem = new SubscriptionItem();
        subItem.setPrice(price);
        subItem.setCurrentPeriodStart(Instant.parse("2025-03-01T00:00:00Z").getEpochSecond());
        subItem.setCurrentPeriodEnd(Instant.parse("2025-04-01T00:00:00Z").getEpochSecond());

        SubscriptionItemCollection items = new SubscriptionItemCollection();
        items.setData(List.of(subItem));

        com.stripe.model.Subscription stripeSub = new com.stripe.model.Subscription();
        stripeSub.setId(subId);
        stripeSub.setCustomer(customerId);
        stripeSub.setItems(items);
        stripeSub.setStatus(status);
        return stripeSub;
    }

    private Invoice createStripeInvoiceWithSubscription(String invoiceId, String subscriptionId) {
        Invoice invoice = new Invoice();
        invoice.setId(invoiceId);

        Invoice.Parent parent = new Invoice.Parent();
        Invoice.Parent.SubscriptionDetails subDetails = new Invoice.Parent.SubscriptionDetails();
        subDetails.setSubscription(subscriptionId);
        parent.setSubscriptionDetails(subDetails);
        invoice.setParent(parent);

        return invoice;
    }

    private InvoiceDto createInvoiceDto(String id) {
        InvoiceDto dto = new InvoiceDto();
        dto.setId(id);
        return dto;
    }

    private Invoice createStripeInvoiceWithPeriod(String invoiceId, long amountDue, long periodStart, long periodEnd) {
        Invoice stripeInvoice = new Invoice();
        stripeInvoice.setId(invoiceId);
        stripeInvoice.setAmountDue(amountDue);

        InvoiceLineItem.Period period = new InvoiceLineItem.Period();
        period.setStart(periodStart);
        period.setEnd(periodEnd);

        InvoiceLineItem lineItem = new InvoiceLineItem();
        lineItem.setPeriod(period);

        InvoiceLineItemCollection lineItems = new InvoiceLineItemCollection();
        lineItems.setData(List.of(lineItem));

        stripeInvoice.setLines(lineItems);
        return stripeInvoice;
    }
}
