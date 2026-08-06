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
package com.tansoflow.tansocore.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tansoflow.tansocore.auth.CustomerAccessGuard;
import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.mcp.tools.customer.AgentCustomerTools;
import com.tansoflow.tansocore.service.client.ClientEntitlementService;
import com.tansoflow.tansocore.service.client.ClientPlanService;
import com.tansoflow.tansocore.service.client.CreditPurchaseService;
import com.tansoflow.tansocore.service.client.UsageForecastService;
import com.tansoflow.tansocore.service.internal.monetization.CreditPriceService;
import com.tansoflow.tansocore.service.internal.monetization.SubscriptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AgentCustomerToolsTest {

    @Mock
    private ClientPlanService clientPlanService;
    @Mock
    private CreditPriceService creditPriceService;
    @Mock
    private ClientEntitlementService clientEntitlementService;
    @Mock
    private UsageForecastService usageForecastService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private CreditPurchaseService creditPurchaseService;

    private AgentCustomerTools tools;

    @BeforeEach
    void setUp() {
        tools = new AgentCustomerTools(new CustomerAccessGuard(), clientPlanService, creditPriceService,
                clientEntitlementService, usageForecastService, subscriptionService, creditPurchaseService,
                new ObjectMapper());
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateCustomer(List<String> scopes) {
        UserContext ctx = new UserContext(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                "cust-1", scopes, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(ctx, null, List.of()));
    }

    @Test
    void spendToolsRefuseWithoutConfirmAction() {
        authenticateCustomer(List.of("read", "purchase"));

        assertThat(tools.subscribePlan("plan-1", null, null, false))
                .contains("confirmation_required");
        assertThat(tools.purchaseCredits("pool-1", 100, null, null, false))
                .contains("confirmation_required");
        verifyNoInteractions(subscriptionService, creditPurchaseService);
    }

    @Test
    void spendToolsRequirePurchaseScope() {
        authenticateCustomer(List.of("read"));

        String result = tools.purchaseCredits("pool-1", 100, null, null, true);
        assertThat(result).contains("purchase");
        verifyNoInteractions(creditPurchaseService);
    }

    @Test
    void customerKeyCannotActOnAnotherCustomer() {
        authenticateCustomer(List.of("read", "purchase"));

        String result = tools.getUsageForecast("someone-else");
        assertThat(result).contains("scoped to another customer");
        verifyNoInteractions(usageForecastService);
    }
}
