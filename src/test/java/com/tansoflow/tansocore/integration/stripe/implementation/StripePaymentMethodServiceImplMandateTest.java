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

import com.tansoflow.tansocore.entity.AccountSetting;
import com.tansoflow.tansocore.integration.stripe.StripeClientFactory;
import com.tansoflow.tansocore.integration.stripe.StripeSyncService;
import com.tansoflow.tansocore.model.exception.SpendMandateExceededException;
import com.tansoflow.tansocore.repository.CustomerRepository;
import com.tansoflow.tansocore.repository.StripeCustomerRepository;
import com.tansoflow.tansocore.service.internal.account.AccountService;
import com.tansoflow.tansocore.service.internal.account.CustomerService;
import com.tansoflow.tansocore.service.internal.account.KeyBudgetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * The mandate bounds charges no human sees. A hosted checkout page is paid by a human in person,
 * so it must not be refused by the mandate.
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
                "usd", "top-up", Map.of()))
                .isInstanceOf(SpendMandateExceededException.class);
        verifyNoInteractions(stripeClientFactory);
    }

    @Test
    void hostedCheckoutIsNotCheckedAgainstTheMandate() {
        // Stop right after the guards: reaching Stripe proves the mandate did not refuse the page.
        when(stripeClientFactory.forAccount(accountId)).thenThrow(new IllegalStateException("reached stripe"));

        assertThatThrownBy(() -> service.createTopupCheckoutSession(accountId, customerId, new BigDecimal("500"),
                "usd", "top-up", Map.of()))
                .hasMessage("reached stripe");
        verify(keyBudgetService, never()).assertWithinMandate(any(), any());
        verify(keyBudgetService).assertWithinBudget(any(), any(), any());
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
}
