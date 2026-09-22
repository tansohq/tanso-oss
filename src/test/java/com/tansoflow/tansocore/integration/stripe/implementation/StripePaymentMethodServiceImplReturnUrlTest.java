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
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.AccountSetting;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.StripeCustomer;
import com.tansoflow.tansocore.integration.stripe.StripeClientFactory;
import com.tansoflow.tansocore.integration.stripe.StripeSyncService;
import com.tansoflow.tansocore.repository.CustomerRepository;
import com.tansoflow.tansocore.repository.StripeCustomerRepository;
import com.tansoflow.tansocore.service.internal.account.AccountService;
import com.tansoflow.tansocore.service.internal.account.CustomerService;
import com.tansoflow.tansocore.service.internal.account.KeyBudgetService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Without operator URLs, the human who finishes or leaves the setup page lands on this instance, not example.com. */
@ExtendWith(MockitoExtension.class)
class StripePaymentMethodServiceImplReturnUrlTest {

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

    private final StripeClient stripeClient = mock(StripeClient.class, RETURNS_DEEP_STUBS);
    private final AccountSetting settings = new AccountSetting();
    private StripePaymentMethodServiceImpl service;
    private final UUID accountId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        service = new StripePaymentMethodServiceImpl(stripeClientFactory, stripeSyncService, stripeCustomerRepository,
                customerRepository, customerService, accountService, keyBudgetService);

        Account account = new Account();
        account.setName("Acme AI");
        Customer customer = new Customer();
        customer.setAccount(account);
        StripeCustomer stripeCustomer = new StripeCustomer();
        stripeCustomer.setStripeCustomerExternalId("cus_1");

        when(stripeClientFactory.forAccount(accountId)).thenReturn(stripeClient);
        when(customerService.validateAndRetrieveCustomer(customerId.toString(), accountId.toString())).thenReturn(customer);
        when(stripeCustomerRepository.findByCustomer(customer)).thenReturn(stripeCustomer);
        when(accountService.retrieveAccountSettings(accountId.toString())).thenReturn(settings);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/public/v1/catalog/acme/signup");
        request.setScheme("https");
        request.setServerName("billing.acme.test");
        request.setServerPort(443);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private SessionCreateParams createSetupSession() throws Exception {
        ArgumentCaptor<SessionCreateParams> params = ArgumentCaptor.forClass(SessionCreateParams.class);
        Session session = new Session();
        when(stripeClient.v1().checkout().sessions().create(params.capture())).thenReturn(session);

        service.createSetupCheckoutSession(accountId, customerId, new BigDecimal("50"), "month", Map.of());
        return params.getValue();
    }

    @Test
    void theSetupPageReturnsToThisInstanceWhenTheOperatorSetNoUrls() throws Exception {
        SessionCreateParams params = createSetupSession();

        assertThat(params.getSuccessUrl()).isEqualTo("https://billing.acme.test/public/checkout/complete?kind=setup");
        assertThat(params.getCancelUrl()).isEqualTo("https://billing.acme.test/public/checkout/cancelled");
    }

    @Test
    void theOperatorsUrlsWin() throws Exception {
        settings.setStripeCheckoutSuccessUrl("https://acme.test/thanks");
        settings.setStripeCheckoutCancelUrl("https://acme.test/cancelled");

        SessionCreateParams params = createSetupSession();

        assertThat(params.getSuccessUrl()).isEqualTo("https://acme.test/thanks");
        assertThat(params.getCancelUrl()).isEqualTo("https://acme.test/cancelled");
    }
}
