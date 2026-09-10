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

        var response = controller.createSubscription(customerKey(ownCustomerId), growth());

        org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value()).isEqualTo(402);
        org.assertj.core.api.Assertions.assertThat(response.getBody().isSuccess()).isFalse();
        org.assertj.core.api.Assertions.assertThat(response.getBody().getData().getCheckoutUrl())
                .isEqualTo("https://invoice.stripe.com/i/acct_test/inv_test");
        org.assertj.core.api.Assertions.assertThat(response.getBody().getData().getSubscription().getIsActive()).isFalse();
    }

    @Test
    void tenantKeyKeepsThe201ShapeAndMintsNoLink() throws Exception {
        when(subscriptionService.clientSubscribeCustomer(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(accountId)))
                .thenReturn(inactivePaidOutcome(UUID.randomUUID().toString()));
        com.tansoflow.tansocore.model.subscription.request.ClientSubscriptionRequest request = growth();
        request.setCustomerReferenceId("cust-ref");

        var response = controller.createSubscription(new UserContext(accountId, null), request);

        org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value()).isEqualTo(201);
        org.mockito.Mockito.verifyNoInteractions(stripeSyncService);
    }

    @Test
    void withoutAPaymentProcessorTheSubscribeStays201AndInactive() throws Exception {
        when(subscriptionService.clientSubscribeCustomer(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(accountId)))
                .thenReturn(inactivePaidOutcome(UUID.randomUUID().toString()));
        when(accountService.retrieveAccountSettings(accountId))
                .thenReturn(mode(com.tansoflow.tansocore.model.api.external.StripeMode.NONE));

        var response = controller.createSubscription(customerKey(ownCustomerId), growth());

        org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value()).isEqualTo(201);
        org.assertj.core.api.Assertions.assertThat(response.getBody().getData().getCheckoutUrl()).isNull();
        org.mockito.Mockito.verifyNoInteractions(stripeSyncService);
    }
}
