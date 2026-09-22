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
package com.tansoflow.tansocore.controller.client;

import com.tansoflow.tansocore.auth.CustomerAccessGuard;
import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.service.internal.monetization.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionLifecycleCustomerKeyTest {

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private com.tansoflow.tansocore.service.internal.account.AccountService accountService;

    @Mock
    private com.tansoflow.tansocore.integration.stripe.StripeSyncService stripeSyncService;
    @Mock
    private com.tansoflow.tansocore.service.internal.account.CustomerService customerService;
    @Spy
    private CustomerAccessGuard customerAccessGuard = new CustomerAccessGuard();

    @InjectMocks
    private SubscriptionClientController controller;

    private final String accountId = UUID.randomUUID().toString();
    private final UUID ownCustomerId = UUID.randomUUID();
    private final String subscriptionId = UUID.randomUUID().toString();
    private Subscription subscription;

    @BeforeEach
    void setUp() {
        Customer owner = new Customer();
        owner.setId(ownCustomerId);
        owner.setEmail("owner@example.com");
        owner.setExternalClientCustomerId("cust-ref-" + ownCustomerId);
        org.mockito.Mockito.lenient()
                .when(customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(accountId)))
                .thenReturn(owner);
        subscription = new Subscription();
        subscription.setId(UUID.fromString(subscriptionId));
        subscription.setCustomer(owner);
        org.mockito.Mockito.lenient()
                .when(subscriptionService.getSubscriptionById(subscriptionId, accountId)).thenReturn(subscription);
    }

    private UserContext customerKey(UUID customerId) {
        return new UserContext(accountId, customerId.toString(), "cust-ref", List.of("read", "purchase"), null);
    }

    @Test
    void ownSubscriptionCanBeCancelled() {
        assertThatCode(() -> controller.cancelSubscription(customerKey(ownCustomerId), subscriptionId, "END_OF_PERIOD"))
                .doesNotThrowAnyException();
        verify(subscriptionService).cancelSubscription(subscriptionId, "END_OF_PERIOD", accountId);
    }

    @Test
    void anotherCustomersSubscriptionIsDenied() {
        assertThatThrownBy(() -> controller.cancelSubscription(customerKey(UUID.randomUUID()), subscriptionId, "IMMEDIATE"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void tenantKeyIsNotPinned() {
        UserContext tenant = new UserContext(accountId, null);
        assertThatCode(() -> controller.cancelSubscription(tenant, subscriptionId, "END_OF_PERIOD"))
                .doesNotThrowAnyException();
    }

    private com.tansoflow.tansocore.model.subscription.response.SubscribedCustomerResponse inactivePaidOutcome(String invoiceId) {
        com.tansoflow.tansocore.model.subscription.SubscriptionDto sub = new com.tansoflow.tansocore.model.subscription.SubscriptionDto();
        sub.setIsActive(false);
        com.tansoflow.tansocore.model.billing.InvoiceDto invoice = new com.tansoflow.tansocore.model.billing.InvoiceDto();
        invoice.setId(invoiceId);
        invoice.setStatus("DUE");
        com.tansoflow.tansocore.model.subscription.response.SubscribedCustomerResponse outcome =
                new com.tansoflow.tansocore.model.subscription.response.SubscribedCustomerResponse();
        outcome.setSubscription(sub);
        outcome.setInvoice(invoice);
        return outcome;
    }

    private com.tansoflow.tansocore.model.subscription.request.ClientSubscriptionRequest growth() {
        com.tansoflow.tansocore.model.subscription.request.ClientSubscriptionRequest request =
                new com.tansoflow.tansocore.model.subscription.request.ClientSubscriptionRequest();
        request.setPlanId("growth");
        return request;
    }

    private com.tansoflow.tansocore.entity.AccountSetting mode(com.tansoflow.tansocore.model.api.external.StripeMode mode) {
        com.tansoflow.tansocore.entity.AccountSetting setting = new com.tansoflow.tansocore.entity.AccountSetting();
        setting.setStripeMode(mode);
        return setting;
    }

    // Regression: with Tanso handling billing and Stripe collecting, a customer key subscribing to a
    // paid plan got 201, an inactive subscription and a DUE invoice with nothing to pay it with.
    // Found by the first agent-ready run against the quickstart stack, 2026-09-09.
    @Test
    void passThroughPaidSubscribeAnswers402WithTheInvoiceLink() throws Exception {
        String invoiceId = UUID.randomUUID().toString();
        when(subscriptionService.clientSubscribeCustomer(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(accountId)))
                .thenReturn(inactivePaidOutcome(invoiceId));
        when(accountService.retrieveAccountSettings(accountId))
                .thenReturn(mode(com.tansoflow.tansocore.model.api.external.StripeMode.PAYMENT_PASS_THROUGH));
        com.tansoflow.tansocore.model.data.stripe.StripePaymentLinkDto link = new com.tansoflow.tansocore.model.data.stripe.StripePaymentLinkDto();
        link.setPaymentLink("https://invoice.stripe.com/i/acct_test/inv_test");
        when(stripeSyncService.syncNewInvoice(UUID.fromString(invoiceId), UUID.fromString(accountId))).thenReturn(link);

        var response = controller.createSubscription(customerKey(ownCustomerId), growth(), new org.springframework.mock.web.MockHttpServletRequest());

        org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value()).isEqualTo(402);
        org.assertj.core.api.Assertions.assertThat(response.getBody().isSuccess()).isFalse();
        org.assertj.core.api.Assertions.assertThat(response.getBody().getData().getCheckoutUrl())
                .isEqualTo("https://invoice.stripe.com/i/acct_test/inv_test");
        org.assertj.core.api.Assertions.assertThat(response.getBody().getData().getSubscription().getIsActive()).isFalse();
        // No checkout session behind a hosted invoice, so the agent polls its own status instead of getting null.
        com.tansoflow.tansocore.model.response.GateError gate =
                (com.tansoflow.tansocore.model.response.GateError) response.getBody().getError();
        org.assertj.core.api.Assertions.assertThat(gate.getPoll()).endsWith("/api/v1/client/customers/cust-ref/status");
    }

    // Stripe refuses to send an invoice to a customer without an email. An agent that signed up with no
    // email used to get a 500 here; it now gets told where to put the email.
    @Test
    void passThroughWithoutAnEmailAnswers402NominateOwner() throws Exception {
        Customer noEmail = new Customer();
        noEmail.setId(ownCustomerId);
        noEmail.setExternalClientCustomerId("agent_noemail");
        when(customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(accountId))).thenReturn(noEmail);
        when(subscriptionService.clientSubscribeCustomer(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(accountId)))
                .thenReturn(inactivePaidOutcome(UUID.randomUUID().toString()));
        when(accountService.retrieveAccountSettings(accountId))
                .thenReturn(mode(com.tansoflow.tansocore.model.api.external.StripeMode.PAYMENT_PASS_THROUGH));

        var response = controller.createSubscription(customerKey(ownCustomerId), growth(), new org.springframework.mock.web.MockHttpServletRequest());

        org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value()).isEqualTo(402);
        com.tansoflow.tansocore.model.response.GateError error =
                (com.tansoflow.tansocore.model.response.GateError) response.getBody().getError();
        org.assertj.core.api.Assertions.assertThat(error.getAction()).isEqualTo("nominate_owner");
        org.assertj.core.api.Assertions.assertThat(error.getUrl()).endsWith("/api/v1/client/customers/agent_noemail/owner");
        org.mockito.Mockito.verify(stripeSyncService, org.mockito.Mockito.never())
                .syncNewInvoice(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void tenantKeyKeepsThe201ShapeAndMintsNoLink() throws Exception {
        when(subscriptionService.clientSubscribeCustomer(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(accountId)))
                .thenReturn(inactivePaidOutcome(UUID.randomUUID().toString()));
        com.tansoflow.tansocore.model.subscription.request.ClientSubscriptionRequest request = growth();
        request.setCustomerReferenceId("cust-ref");

        var response = controller.createSubscription(new UserContext(accountId, null), request, new org.springframework.mock.web.MockHttpServletRequest());

        org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value()).isEqualTo(201);
        org.mockito.Mockito.verifyNoInteractions(stripeSyncService);
    }

    @Test
    void withoutAPaymentProcessorTheSubscribeStays201AndInactive() throws Exception {
        when(subscriptionService.clientSubscribeCustomer(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(accountId)))
                .thenReturn(inactivePaidOutcome(UUID.randomUUID().toString()));
        when(accountService.retrieveAccountSettings(accountId))
                .thenReturn(mode(com.tansoflow.tansocore.model.api.external.StripeMode.NONE));

        var response = controller.createSubscription(customerKey(ownCustomerId), growth(), new org.springframework.mock.web.MockHttpServletRequest());

        org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value()).isEqualTo(201);
        org.assertj.core.api.Assertions.assertThat(response.getBody().getData().getCheckoutUrl()).isNull();
        org.mockito.Mockito.verifyNoInteractions(stripeSyncService);
    }

    private com.tansoflow.tansocore.model.subscription.request.ClientChangeSubscriptionRequest upgradeTo(String planId) {
        com.tansoflow.tansocore.model.subscription.request.ClientChangeSubscriptionRequest request =
                new com.tansoflow.tansocore.model.subscription.request.ClientChangeSubscriptionRequest();
        request.setChangeType(com.tansoflow.tansocore.model.subscription.type.SubscriptionChangeType.UPGRADE);
        request.setChangeToPlanId(planId);
        return request;
    }

    // An upgrade an agent has not paid for comes back as a gate, not a 200 that hides a plan it cannot use yet.
    @Test
    void anUnpaidPlanChangeAnswers402WithTheInvoiceLinkAndAStatusPoll() throws Exception {
        UUID adjustmentInvoiceId = UUID.randomUUID();
        when(subscriptionService.upgradeSubscription(org.mockito.ArgumentMatchers.eq(subscriptionId),
                org.mockito.ArgumentMatchers.eq(accountId), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(true))).thenReturn(com.tansoflow.tansocore.model.subscription.UpgradeResult.waitingOnInvoice(adjustmentInvoiceId));
        when(accountService.retrieveAccountSettings(accountId))
                .thenReturn(mode(com.tansoflow.tansocore.model.api.external.StripeMode.PAYMENT_PASS_THROUGH));
        com.tansoflow.tansocore.model.data.stripe.StripePaymentLinkDto link =
                new com.tansoflow.tansocore.model.data.stripe.StripePaymentLinkDto();
        link.setPaymentLink("https://invoice.stripe.com/i/acct_test/inv_adjust");
        when(stripeSyncService.syncNewInvoice(adjustmentInvoiceId, UUID.fromString(accountId))).thenReturn(link);

        var response = controller.changeSubscription(customerKey(ownCustomerId), upgradeTo("starter"), subscriptionId,
                new org.springframework.mock.web.MockHttpServletRequest());

        org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value()).isEqualTo(402);
        org.assertj.core.api.Assertions.assertThat(response.getBody().isSuccess()).isFalse();
        com.tansoflow.tansocore.model.response.GateError gate =
                (com.tansoflow.tansocore.model.response.GateError) response.getBody().getError();
        org.assertj.core.api.Assertions.assertThat(gate.getAction()).isEqualTo("complete_checkout");
        org.assertj.core.api.Assertions.assertThat(gate.getUrl()).isEqualTo("https://invoice.stripe.com/i/acct_test/inv_adjust");
        org.assertj.core.api.Assertions.assertThat(gate.getPoll()).endsWith("/status");
    }

    // STRIPE_DRIVEN: Stripe could not charge the saved card, so the agent gets Stripe's hosted invoice and a poll.
    @Test
    void aStripeDrivenUpgradeStripeCouldNotChargeAnswers402WithTheHostedInvoice() throws Exception {
        when(subscriptionService.upgradeSubscription(org.mockito.ArgumentMatchers.eq(subscriptionId),
                org.mockito.ArgumentMatchers.eq(accountId), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(true))).thenReturn(com.tansoflow.tansocore.model.subscription.UpgradeResult
                .waitingOnStripe("https://invoice.stripe.com/i/acct_test/inv_proration"));
        when(accountService.retrieveAccountSettings(accountId))
                .thenReturn(mode(com.tansoflow.tansocore.model.api.external.StripeMode.STRIPE_DRIVEN));

        var response = controller.changeSubscription(customerKey(ownCustomerId), upgradeTo("starter"), subscriptionId,
                new org.springframework.mock.web.MockHttpServletRequest());

        org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value()).isEqualTo(402);
        org.assertj.core.api.Assertions.assertThat(response.getBody().isSuccess()).isFalse();
        com.tansoflow.tansocore.model.response.GateError gate =
                (com.tansoflow.tansocore.model.response.GateError) response.getBody().getError();
        org.assertj.core.api.Assertions.assertThat(gate.getGate()).isEqualTo("payment");
        org.assertj.core.api.Assertions.assertThat(gate.getAction()).isEqualTo("complete_checkout");
        org.assertj.core.api.Assertions.assertThat(gate.getUrl()).isEqualTo("https://invoice.stripe.com/i/acct_test/inv_proration");
        org.assertj.core.api.Assertions.assertThat(gate.getPoll()).endsWith("/status");
        // Stripe already raised the invoice; Tanso must not mint a second payment link.
        org.mockito.Mockito.verifyNoInteractions(stripeSyncService);
    }

    @Test
    void aPlanChangeThatNeedsNoPaymentStays200() throws Exception {
        when(subscriptionService.upgradeSubscription(org.mockito.ArgumentMatchers.eq(subscriptionId),
                org.mockito.ArgumentMatchers.eq(accountId), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(true))).thenReturn(com.tansoflow.tansocore.model.subscription.UpgradeResult.notWaiting());

        var response = controller.changeSubscription(customerKey(ownCustomerId), upgradeTo("starter"), subscriptionId,
                new org.springframework.mock.web.MockHttpServletRequest());

        org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value()).isEqualTo(200);
        org.assertj.core.api.Assertions.assertThat(response.getBody().isSuccess()).isTrue();
        org.mockito.Mockito.verifyNoInteractions(stripeSyncService);
    }

    // Stripe will not send an invoice to a customer with no email, so the agent is told where to put one.
    @Test
    void anUnpaidPlanChangeWithoutAnEmailAnswers402NominateOwner() throws Exception {
        subscription.getCustomer().setEmail(null);
        subscription.getCustomer().setExternalClientCustomerId("agent_noemail");

        var response = controller.changeSubscription(customerKey(ownCustomerId), upgradeTo("starter"), subscriptionId,
                new org.springframework.mock.web.MockHttpServletRequest());

        org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value()).isEqualTo(402);
        com.tansoflow.tansocore.model.response.GateError gate =
                (com.tansoflow.tansocore.model.response.GateError) response.getBody().getError();
        org.assertj.core.api.Assertions.assertThat(gate.getAction()).isEqualTo("nominate_owner");
        org.assertj.core.api.Assertions.assertThat(gate.getUrl()).endsWith("/api/v1/client/customers/agent_noemail/owner");
        org.mockito.Mockito.verifyNoInteractions(stripeSyncService);
        // Asked before anything is raised: an invoice with no email behind it could never be paid.
        org.mockito.Mockito.verify(subscriptionService, org.mockito.Mockito.never())
                .upgradeSubscription(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean());
    }
}
