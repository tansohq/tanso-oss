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

import com.stripe.exception.StripeException;
import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.entity.AccountSetting;
import com.tansoflow.tansocore.entity.Invoice;
import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.integration.stripe.StripeSyncService;
import com.tansoflow.tansocore.model.billing.response.StripeCheckoutSessionsResponse;
import com.tansoflow.tansocore.model.billing.type.InvoiceStatus;
import com.tansoflow.tansocore.model.data.stripe.StripePaymentLinkDto;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.service.internal.account.AccountService;
import com.tansoflow.tansocore.service.internal.monetization.InvoiceService;
import com.tansoflow.tansocore.service.internal.monetization.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BillingClientControllerTest {

    @Mock
    private InvoiceService invoiceService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private StripeSyncService stripeSyncService;
    @Mock
    private AccountService accountService;

    @InjectMocks
    private BillingClientController billingClientController;

    private UserContext userContext;
    private final String accountId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userContext = new UserContext(accountId, "test-api-key");
    }

    @Test
    void testCreateStripeSubscriptionCheckoutSession_Deprecated() {
        ResponseEntity<ApiResponse<StripeCheckoutSessionsResponse>> response =
            billingClientController.createStripeSubscriptionCheckoutSession(userContext, UUID.randomUUID().toString());

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
    }

    @Test
    void testCreateStripeCheckoutSession_StripeDisabled() {
        AccountSetting accountSetting = new AccountSetting();
        accountSetting.setStripeMode(com.tansoflow.tansocore.model.api.external.StripeMode.NONE);
        when(accountService.retrieveAccountSettings(accountId)).thenReturn(accountSetting);

        ResponseEntity<ApiResponse<StripeCheckoutSessionsResponse>> response =
            billingClientController.createStripeCheckoutSession(userContext, UUID.randomUUID().toString());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Stripe is not enabled at the account", response.getBody().getError().getMessage());
    }

    @Test
    void testCreateStripeCheckoutSession_Success() throws StripeException {
        AccountSetting accountSetting = new AccountSetting();
        accountSetting.setStripeMode(com.tansoflow.tansocore.model.api.external.StripeMode.PAYMENT_PASS_THROUGH);
        when(accountService.retrieveAccountSettings(accountId)).thenReturn(accountSetting);

        String subscriptionId = UUID.randomUUID().toString();
        Subscription subscription = new Subscription();
        subscription.setId(UUID.fromString(subscriptionId));
        when(subscriptionService.getSubscriptionById(subscriptionId, accountId)).thenReturn(subscription);

        String invoiceId = UUID.randomUUID().toString();
        Invoice invoice = new Invoice();
        invoice.setId(UUID.fromString(invoiceId));
        invoice.setStatus(InvoiceStatus.DUE.name());
        when(invoiceService.retrieveCurrentlyDueBySubscription(subscription)).thenReturn(invoice);

        StripePaymentLinkDto linkDto = new StripePaymentLinkDto();
        linkDto.setPaymentLink("https://stripe.com/checkout/test");
        when(stripeSyncService.syncNewInvoice(any(UUID.class), any(UUID.class))).thenReturn(linkDto);
        when(stripeSyncService.retrieveStripeSession(invoiceId, accountId)).thenReturn(linkDto);

        ResponseEntity<ApiResponse<StripeCheckoutSessionsResponse>> response =
            billingClientController.createStripeCheckoutSession(userContext, subscriptionId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals("https://stripe.com/checkout/test", response.getBody().getData().getUrl());
        verify(stripeSyncService).syncNewInvoice(any(UUID.class), any(UUID.class));
    }

    @Test
    void testCreateStripeCheckoutSession_NoDueInvoice() {
        AccountSetting accountSetting = new AccountSetting();
        accountSetting.setStripeMode(com.tansoflow.tansocore.model.api.external.StripeMode.PAYMENT_PASS_THROUGH);
        when(accountService.retrieveAccountSettings(accountId)).thenReturn(accountSetting);

        String subscriptionId = UUID.randomUUID().toString();
        Subscription subscription = new Subscription();
        subscription.setId(UUID.fromString(subscriptionId));
        when(subscriptionService.getSubscriptionById(subscriptionId, accountId)).thenReturn(subscription);
        when(invoiceService.retrieveCurrentlyDueBySubscription(subscription)).thenReturn(null);

        ResponseEntity<ApiResponse<StripeCheckoutSessionsResponse>> response =
            billingClientController.createStripeCheckoutSession(userContext, subscriptionId);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("No DUE invoice found for this subscription", response.getBody().getError().getMessage());
    }
}
