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
package com.tansoflow.tansocore.integration.stripe.implementation;

import com.stripe.StripeClient;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.tansoflow.tansocore.entity.AccountSetting;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.StripeCustomer;
import com.tansoflow.tansocore.integration.stripe.StripeClientFactory;
import com.tansoflow.tansocore.integration.stripe.StripeSyncService;
import com.tansoflow.tansocore.model.apikey.type.SpendKind;
import com.tansoflow.tansocore.model.exception.BudgetExceededException;
import com.tansoflow.tansocore.model.exception.SpendMandateExceededException;
import com.tansoflow.tansocore.repository.CustomerRepository;
import com.tansoflow.tansocore.repository.StripeCustomerRepository;
import com.tansoflow.tansocore.service.internal.account.AccountService;
import com.tansoflow.tansocore.service.internal.account.CustomerService;
import com.tansoflow.tansocore.service.internal.account.KeyBudgetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The mandate and the key budget bound charges no human sees. A hosted checkout page is paid by a human in
 * person, so neither refuses it; the operator's per-charge cap still does.
 */
@ExtendWith(MockitoExtension.class)
class StripePaymentMethodServiceImplMandateTest {

    @Mock
    private StripeClientFactory stripeClientFactory;
    @Mock
    private StripeSyncService stripeSyncService;
    @Mock
    private StripeCustomerRepository stripeCustomerRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private CustomerService customerService;
    @Mock
    private AccountService accountService;
    @Mock
    private KeyBudgetService keyBudgetService;

    private StripePaymentMethodServiceImpl service;
    private final UUID accountId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new StripePaymentMethodServiceImpl(stripeClientFactory, stripeSyncService, stripeCustomerRepository,
                customerRepository, customerService, accountService, keyBudgetService);
        lenient().when(accountService.retrieveAccountSettings(accountId.toString())).thenReturn(new AccountSetting());
    }

    @Test
    void anOffSessionChargeOverTheMandateNeverReachesStripe() {
        doThrow(new SpendMandateExceededException("agent_abc", new BigDecimal("50"), BigDecimal.ZERO,
                new BigDecimal("60"), "month", Instant.now().plusSeconds(60)))
                .when(keyBudgetService).assertWithinMandate(customerId, new BigDecimal("60"));

        assertThatThrownBy(() -> service.chargeOffSession(accountId, customerId, "pm_1", new BigDecimal("60"),
                "usd", "top-up", Map.of(), null))
                .isInstanceOf(SpendMandateExceededException.class);
        verifyNoInteractions(stripeClientFactory);
    }

    @Test
    void anOffSessionChargeOverTheKeyBudgetNeverReachesStripe() {
        doThrow(new BudgetExceededException(SpendKind.MONEY, new BigDecimal("50"), new BigDecimal("50"),
                new BigDecimal("60"), Instant.now().plusSeconds(60)))
                .when(keyBudgetService).assertWithinBudget(any(), any(), any());

        assertThatThrownBy(() -> service.chargeOffSession(accountId, customerId, "pm_1", new BigDecimal("60"),
                "usd", "top-up", Map.of(), null))
                .isInstanceOf(BudgetExceededException.class);
        verifyNoInteractions(stripeClientFactory);
    }

    @Test
    void hostedCheckoutIsCheckedAgainstNeitherTheKeyBudgetNorTheMandate() {
        // A human pays this page in person. Stop right after the guards: reaching Stripe proves neither limit
        // refused the page.
        when(stripeClientFactory.forAccount(accountId)).thenThrow(new IllegalStateException("reached stripe"));

        assertThatThrownBy(() -> service.createTopupCheckoutSession(accountId, customerId, new BigDecimal("500"),
                "usd", "top-up", Map.of()))
                .hasMessage("reached stripe");
        verify(keyBudgetService, never()).assertWithinMandate(any(), any());
        verify(keyBudgetService, never()).assertWithinBudget(any(), any(), any());
    }

    @Test
    void hostedCheckoutIsStillHeldToTheOperatorsPerChargeCap() {
        AccountSetting capped = new AccountSetting();
        capped.setAgentMaxTopupAmount(new BigDecimal("100"));
        when(accountService.retrieveAccountSettings(accountId.toString())).thenReturn(capped);

        assertThatThrownBy(() -> service.createTopupCheckoutSession(accountId, customerId, new BigDecimal("500"),
                "usd", "top-up", Map.of()))
                .isInstanceOf(BudgetExceededException.class);
        verifyNoInteractions(stripeClientFactory);
    }

    @Test
    void theSetupPageNamesTheOperatorAmountAndPeriod() {
        assertThat(StripePaymentMethodServiceImpl.mandateNotice("Acme AI", new BigDecimal("50"), "usd", "month"))
                .isEqualTo("You're saving this card so your agent can pay Acme AI without asking you, up to 50.00 USD"
                        + " per month. Anything above that comes back to you for approval.");
    }

    @Test
    void theSetupPageDropsTheOperatorClauseWhenTheAccountHasNoName() {
        assertThat(StripePaymentMethodServiceImpl.mandateNotice("  ", new BigDecimal("50"), "usd", "week"))
                .isEqualTo("You're saving this card so your agent can use it without asking you, up to 50.00 USD"
                        + " per week. Anything above that comes back to you for approval.");
    }

    @Test
    void theOffSessionPaymentIntentCarriesTheCallersIdempotencyKey() throws Exception {
        StripeClient stripeClient = org.mockito.Mockito.mock(StripeClient.class, Answers.RETURNS_DEEP_STUBS);
        when(stripeClientFactory.forAccount(accountId)).thenReturn(stripeClient);
        Customer customer = new Customer();
        customer.setId(customerId);
        when(customerService.validateAndRetrieveCustomer(customerId.toString(), accountId.toString())).thenReturn(customer);
        StripeCustomer stripeCustomer = new StripeCustomer();
        stripeCustomer.setStripeCustomerExternalId("cus_1");
        when(stripeCustomerRepository.findByCustomer(customer)).thenReturn(stripeCustomer);
        PaymentIntent intent = new PaymentIntent();
        intent.setId("pi_1");
        intent.setStatus("succeeded");
        when(stripeClient.v1().paymentIntents().create(any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
                .thenReturn(intent);

        service.chargeOffSession(accountId, customerId, "pm_1", new BigDecimal("10"), "usd", "top-up", Map.of(),
                "tanso-credit-purchase-abc");

        ArgumentCaptor<RequestOptions> options = ArgumentCaptor.forClass(RequestOptions.class);
        verify(stripeClient.v1().paymentIntents()).create(any(PaymentIntentCreateParams.class), options.capture());
        assertThat(options.getValue().getIdempotencyKey()).isEqualTo("tanso-credit-purchase-abc");
    }
}
