package com.tansoflow.tansocore.application.orchestrator;

import com.tansoflow.tansocore.entity.AccountSetting;
import com.tansoflow.tansocore.integration.stripe.StripeSyncService;
import com.tansoflow.tansocore.model.api.external.StripeMode;
import com.tansoflow.tansocore.model.event.service.CustomerCreatedEvent;
import com.tansoflow.tansocore.model.event.service.InvoiceCreatedEvent;
import com.tansoflow.tansocore.model.event.service.PlanCreatedEvent;
import com.tansoflow.tansocore.model.event.service.PlanUpdatedEvent;
import com.tansoflow.tansocore.model.event.service.SubscriptionActivatedEvent;
import com.tansoflow.tansocore.model.event.service.SubscriptionCancelledEvent;
import com.tansoflow.tansocore.repository.AccountSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeOrchestratorTest {

    @InjectMocks
    private StripeOrchestrator stripeOrchestrator;

    @Mock
    private AccountSettingRepository accountSettingRepository;

    @Mock
    private StripeSyncService stripeSyncService;

    private UUID accountId;
    private AccountSetting accountSetting;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        accountSetting = new AccountSetting();
    }

    // ── STRIPE_DRIVEN syncs outbound (except invoices) ──────────────────────

    @Test
    void onInvoiceCreated_StripeDriven_StillSkips() throws Exception {
        accountSetting.setStripeMode(StripeMode.STRIPE_DRIVEN);
        when(accountSettingRepository.findAccountSettingById(accountId)).thenReturn(accountSetting);

        stripeOrchestrator.onInvoiceCreated(new InvoiceCreatedEvent(accountId, UUID.randomUUID(), "REGULAR"));

        verifyNoInteractions(stripeSyncService);
    }

    @Test
    void onCustomerCreated_StripeDriven_SyncsCustomer() throws Exception {
        accountSetting.setStripeMode(StripeMode.STRIPE_DRIVEN);
        when(accountSettingRepository.findAccountSettingById(accountId)).thenReturn(accountSetting);

        UUID customerId = UUID.randomUUID();
        stripeOrchestrator.onCustomerCreated(new CustomerCreatedEvent(accountId, customerId));

        verify(stripeSyncService).createStripeCustomer(accountId, customerId);
    }

    @Test
    void onPlanCreated_StripeDriven_SyncsProductWithPrices() throws Exception {
        accountSetting.setStripeMode(StripeMode.STRIPE_DRIVEN);
        when(accountSettingRepository.findAccountSettingById(accountId)).thenReturn(accountSetting);

        UUID planId = UUID.randomUUID();
        stripeOrchestrator.onPlanCreated(new PlanCreatedEvent(accountId, planId));

        verify(stripeSyncService).createStripeProductWithPrices(planId, accountId);
    }

    @Test
    void onPlanUpdated_StripeDriven_SyncsProductWithPrices() throws Exception {
        accountSetting.setStripeMode(StripeMode.STRIPE_DRIVEN);
        when(accountSettingRepository.findAccountSettingById(accountId)).thenReturn(accountSetting);

        UUID planId = UUID.randomUUID();
        stripeOrchestrator.onPlanUpdated(new PlanUpdatedEvent(accountId, planId));

        verify(stripeSyncService).createStripeProductWithPrices(planId, accountId);
    }

    @Test
    void onSubscriptionActivated_StripeDriven_SyncsSubscription() throws Exception {
        accountSetting.setStripeMode(StripeMode.STRIPE_DRIVEN);
        when(accountSettingRepository.findAccountSettingById(accountId)).thenReturn(accountSetting);

        UUID subscriptionId = UUID.randomUUID();
        stripeOrchestrator.onSubscriptionActivated(new SubscriptionActivatedEvent(accountId, subscriptionId));

        verify(stripeSyncService).createStripeSubscription(subscriptionId, accountId);
    }

    @Test
    void onSubscriptionCancelled_StripeDriven_CancelsSubscription() throws Exception {
        accountSetting.setStripeMode(StripeMode.STRIPE_DRIVEN);
        when(accountSettingRepository.findAccountSettingById(accountId)).thenReturn(accountSetting);

        UUID subscriptionId = UUID.randomUUID();
        stripeOrchestrator.onSubscriptionCancelled(new SubscriptionCancelledEvent(accountId, subscriptionId, "IMMEDIATE"));

        verify(stripeSyncService).cancelStripeSubscription(subscriptionId, accountId, "IMMEDIATE");
    }

    // ── Verify other modes still work ──────────────────────────────────────

    @Test
    void onInvoiceCreated_PaymentPassThrough_SyncsInvoice() throws Exception {
        accountSetting.setStripeMode(StripeMode.PAYMENT_PASS_THROUGH);
        when(accountSettingRepository.findAccountSettingById(accountId)).thenReturn(accountSetting);

        UUID invoiceId = UUID.randomUUID();
        stripeOrchestrator.onInvoiceCreated(new InvoiceCreatedEvent(accountId, invoiceId, "REGULAR"));

        verify(stripeSyncService).syncNewInvoice(invoiceId, accountId);
    }

    @Test
    void onCustomerCreated_StripeIntegration_SyncsCustomer() throws Exception {
        accountSetting.setStripeMode(StripeMode.STRIPE_INTEGRATION);
        when(accountSettingRepository.findAccountSettingById(accountId)).thenReturn(accountSetting);

        UUID customerId = UUID.randomUUID();
        stripeOrchestrator.onCustomerCreated(new CustomerCreatedEvent(accountId, customerId));

        verify(stripeSyncService).createStripeCustomer(accountId, customerId);
    }

    @Test
    void onInvoiceCreated_None_SkipsSync() throws Exception {
        accountSetting.setStripeMode(StripeMode.NONE);
        when(accountSettingRepository.findAccountSettingById(accountId)).thenReturn(accountSetting);

        stripeOrchestrator.onInvoiceCreated(new InvoiceCreatedEvent(accountId, UUID.randomUUID(), "REGULAR"));

        verifyNoInteractions(stripeSyncService);
    }
}
