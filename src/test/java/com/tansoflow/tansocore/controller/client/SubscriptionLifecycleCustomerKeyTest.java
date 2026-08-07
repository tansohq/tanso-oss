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
}
