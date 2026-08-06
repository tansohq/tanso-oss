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
package com.tansoflow.tansocore.filter;

import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.AccountApiKey;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.service.internal.account.AccountService;
import com.tansoflow.tansocore.service.internal.account.CustomerApiKeyService;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthFilterTest {

    @Mock
    private AccountService accountService;
    @Mock
    private CustomerApiKeyService customerApiKeyService;

    @org.junit.jupiter.api.BeforeEach
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestWithKey(String key) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/client/plans");
        request.addHeader("X-API-Key", key);
        return request;
    }

    @Test
    void tenantKeyStillAuthenticatesAsClient() throws ServletException, IOException {
        Account account = new Account();
        account.setId(UUID.randomUUID());
        when(accountService.findByApiKey("sk_test_abc")).thenReturn(account);

        new ApiKeyAuthFilter(accountService, customerApiKeyService)
                .doFilter(requestWithKey("sk_test_abc"), new MockHttpServletResponse(), new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_CLIENT");
        UserContext ctx = (UserContext) auth.getPrincipal();
        assertThat(ctx.getAccountId()).isEqualTo(account.getId().toString());
        assertThat(ctx.isCustomerScoped()).isFalse();
        verifyNoInteractions(customerApiKeyService);
    }

    @Test
    void customerKeyAuthenticatesAsCustomerWithScopes() throws ServletException, IOException {
        Account account = new Account();
        account.setId(UUID.randomUUID());
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setAccount(account);
        customer.setExternalClientCustomerId("cust-ref-1");
        AccountApiKey key = new AccountApiKey();
        key.setAccount(account);
        key.setCustomer(customer);
        key.setScopes("read,purchase");
        key.setIsActive(true);
        key.setExpiresAt(Instant.now().plusSeconds(60));
        when(customerApiKeyService.findByKey("ck_test_abc")).thenReturn(key);

        new ApiKeyAuthFilter(accountService, customerApiKeyService)
                .doFilter(requestWithKey("ck_test_abc"), new MockHttpServletResponse(), new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_CUSTOMER", "SCOPE_read", "SCOPE_purchase");
        UserContext ctx = (UserContext) auth.getPrincipal();
        assertThat(ctx.isCustomerScoped()).isTrue();
        assertThat(ctx.getCustomerReferenceId()).isEqualTo("cust-ref-1");
        verifyNoInteractions(accountService);
    }

    @Test
    void unknownCustomerKeyIs401() throws ServletException, IOException {
        when(customerApiKeyService.findByKey("ck_test_bad")).thenReturn(null);

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        new ApiKeyAuthFilter(accountService, customerApiKeyService)
                .doFilter(requestWithKey("ck_test_bad"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"unauthorized\"");
        assertThat(chain.getRequest()).isNull();
    }
}
