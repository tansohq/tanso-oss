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
package com.tansoflow.tansocore.service.client.implementation;

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.CheckoutSession;
import com.tansoflow.tansocore.entity.CreditPool;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.integration.stripe.StripePaymentMethodService;
import com.tansoflow.tansocore.model.credit.CreditGrantDto;
import com.tansoflow.tansocore.model.credit.CreditPurchaseResult;
import com.tansoflow.tansocore.model.credit.request.CreditGrantRequest;
import com.tansoflow.tansocore.model.credit.request.CreditPurchaseRequest;
import com.tansoflow.tansocore.repository.CheckoutSessionRepository;
import com.tansoflow.tansocore.repository.CreditPoolRepository;
import com.tansoflow.tansocore.service.internal.account.CustomerService;
import com.tansoflow.tansocore.service.internal.monetization.CreditPriceService;
import com.tansoflow.tansocore.service.internal.monetization.CreditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditPurchaseServiceImplTest {

    @Mock
    private CreditPoolRepository creditPoolRepository;
    @Mock
    private CreditPriceService creditPriceService;
    @Mock
    private CreditService creditService;
    @Mock
    private CustomerService customerService;
    @Mock
    private StripePaymentMethodService stripePaymentMethodService;
    @Mock
    private CheckoutSessionRepository checkoutSessionRepository;

    @InjectMocks
    private CreditPurchaseServiceImpl service;

    private final UUID accountId = UUID.randomUUID();
    private Customer customer;
    private CreditPool pool;

    @BeforeEach
    void setUp() {
        Account account = new Account();
        account.setId(accountId);
        customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setAccount(account);
        customer.setExternalClientCustomerId("cust-1");

        pool = new CreditPool();
        pool.setId(UUID.randomUUID());
        pool.setAccount(account);
        pool.setCustomer(customer);
        pool.setDenomination("credits");

        lenient().when(customerService.retrieveCustomerByExternalClientCustomerIdAndAccount("cust-1", accountId.toString()))
                .thenReturn(customer);
        lenient().when(creditPoolRepository.findByIdAndAccountId(pool.getId(), accountId))
                .thenReturn(Optional.of(pool));
        lenient().when(creditPriceService.resolvePrice(eq(accountId), eq("credits"), any()))
                .thenReturn(Optional.of(new CreditPriceService.ResolvedPrice(
                        new BigDecimal("0.01"), "USD", UUID.randomUUID())));
        lenient().when(checkoutSessionRepository.save(any(CheckoutSession.class)))
                .thenAnswer(inv -> {
                    CheckoutSession session = inv.getArgument(0);
                    if (session.getId() == null) session.setId(UUID.randomUUID());
                    return session;
                });
    }

    private CreditPurchaseRequest request(BigDecimal credits, String pm) {
        CreditPurchaseRequest request = new CreditPurchaseRequest();
        request.setCreditPoolId(pool.getId().toString());
        request.setCredits(credits);
        request.setPaymentMethodId(pm);
        return request;
    }

    @Test
    void successfulOffSessionChargeGrantsAtBookPrice() throws Exception {
        when(stripePaymentMethodService.chargeOffSession(eq(accountId), eq(customer.getId()), eq("pm_1"),
                eq(new BigDecimal("10.00")), eq("USD"), anyString(), any()))
                .thenReturn(new StripePaymentMethodService.PaymentResult(true, "pi_123", null));
        CreditGrantDto grantDto = new CreditGrantDto();
        grantDto.setId(UUID.randomUUID().toString());
        when(creditService.grantCredits(any(CreditGrantRequest.class), eq(accountId.toString())))
                .thenReturn(grantDto);

        CreditPurchaseResult result = service.purchase(request(new BigDecimal("1000"), "pm_1"),
                "cust-1", accountId.toString());

        assertThat(result.isCompleted()).isTrue();
        assertThat(result.getAmountCharged()).isEqualByComparingTo("10.00");
        assertThat(result.getPaymentIntentId()).isEqualTo("pi_123");

        ArgumentCaptor<CreditGrantRequest> captor = ArgumentCaptor.forClass(CreditGrantRequest.class);
        verify(creditService).grantCredits(captor.capture(), eq(accountId.toString()));
        assertThat(captor.getValue().getGrantType()).isEqualTo("PURCHASED");
        assertThat(captor.getValue().getUnitPrice()).isEqualByComparingTo("0.01");
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("pi_pi_123");
    }

    @Test
    void noPaymentMethodFallsBackToHostedCheckout() throws Exception {
        when(stripePaymentMethodService.createTopupCheckoutSession(eq(accountId), eq(customer.getId()),
                eq(new BigDecimal("10.00")), eq("USD"), anyString(), any()))
                .thenReturn(new StripePaymentMethodService.HostedCheckout("https://checkout", "cs_123"));

        CreditPurchaseResult result = service.purchase(request(new BigDecimal("1000"), null),
                "cust-1", accountId.toString());

        assertThat(result.isCompleted()).isFalse();
        assertThat(result.getCheckoutUrl()).isEqualTo("https://checkout");
        assertThat(result.getCheckoutSessionId()).isNotNull();
        verify(creditService, never()).grantCredits(any(), anyString());
        verify(stripePaymentMethodService, never()).chargeOffSession(any(), any(), anyString(), any(), anyString(), anyString(), any());

        ArgumentCaptor<CheckoutSession> captor = ArgumentCaptor.forClass(CheckoutSession.class);
        verify(checkoutSessionRepository).save(captor.capture());
        assertThat(captor.getValue().getPurpose()).isEqualTo(CheckoutSession.PURPOSE_CREDIT_TOPUP);
        assertThat(captor.getValue().getCredits()).isEqualByComparingTo("1000");
    }

    @Test
    void declinedChargeFallsBackToHostedCheckout() throws Exception {
        when(stripePaymentMethodService.chargeOffSession(any(), any(), anyString(), any(), anyString(), anyString(), any()))
                .thenReturn(new StripePaymentMethodService.PaymentResult(false, "pi_dead", "card_declined"));
        when(stripePaymentMethodService.createTopupCheckoutSession(any(), any(), any(), anyString(), anyString(), any()))
                .thenReturn(new StripePaymentMethodService.HostedCheckout("https://checkout", "cs_456"));

        CreditPurchaseResult result = service.purchase(request(new BigDecimal("1000"), "pm_bad"),
                "cust-1", accountId.toString());

        assertThat(result.isCompleted()).isFalse();
        assertThat(result.getDeclineReason()).isNotNull();
        verify(creditService, never()).grantCredits(any(), anyString());
    }

    @Test
    void unpricedDenominationIsRejected() {
        when(creditPriceService.resolvePrice(eq(accountId), eq("credits"), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.purchase(request(new BigDecimal("1000"), "pm_1"), "cust-1", accountId.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No published price");
    }

    @Test
    void anotherCustomersPoolIsNotFound() {
        Customer other = new Customer();
        other.setId(UUID.randomUUID());
        pool.setCustomer(other);
        assertThatThrownBy(() -> service.purchase(request(new BigDecimal("1000"), "pm_1"), "cust-1", accountId.toString()))
                .isInstanceOf(com.tansoflow.tansocore.model.exception.ResourceNotFoundException.class);
    }

    @Test
    void tinyPurchasesBelowPaymentMinimumAreRejected() {
        assertThatThrownBy(() -> service.purchase(request(new BigDecimal("10"), "pm_1"), "cust-1", accountId.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0.50");
    }
}
