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
import com.tansoflow.tansocore.auth.UserContextAuthentication;
import com.tansoflow.tansocore.model.entitlement.response.EntitlementResponse;
import com.tansoflow.tansocore.property.AppProperty;
import com.tansoflow.tansocore.service.client.ClientEntitlementService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntitlementAuthFilterTest {

    @Mock
    private ClientEntitlementService clientEntitlementService;

    @Mock
    private FilterChain filterChain;

    private AppProperty appProperty;
    private EntitlementAuthFilter filter;

    private static final String MASTER_ACCOUNT_ID = "00000000-0000-0000-0000-000000000000";

    @BeforeEach
    void setUp() {
        appProperty = new AppProperty();
        appProperty.setDogfoodingEnabled(true);
        appProperty.setMasterAccountId(MASTER_ACCOUNT_ID);
        filter = new EntitlementAuthFilter(clientEntitlementService, appProperty);
        SecurityContextHolder.clearContext();
    }

    // --- dogfooding disabled: filter is skipped ---

    @Test
    void dogfoodingDisabled_passesThrough() throws ServletException, IOException {
        appProperty.setDogfoodingEnabled(false);
        setAuthentication(UUID.randomUUID().toString());

        MockHttpServletRequest request = clientRequest("/api/v1/client/events");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(clientEntitlementService, never()).checkEntitlement(anyString(), anyString(), anyString(), anyBoolean());
        assertEquals(200, response.getStatus());
    }

    // --- master account: bypass entitlement check ---

    @Test
    void masterAccount_bypassesEntitlementCheck() throws ServletException, IOException {
        setAuthentication(MASTER_ACCOUNT_ID);

        MockHttpServletRequest request = clientRequest("/api/v1/client/events");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(clientEntitlementService, never()).checkEntitlement(anyString(), anyString(), anyString(), anyBoolean());
        assertEquals(200, response.getStatus());
    }

    // --- non-master account, entitled: passes through ---

    @Test
    void entitledAccount_passesThrough() throws ServletException, IOException {
        String accountId = UUID.randomUUID().toString();
        setAuthentication(accountId);
        mockEntitlementAllowed(accountId);

        MockHttpServletRequest request = clientRequest("/api/v1/client/events");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(clientEntitlementService).checkEntitlement(accountId, MASTER_ACCOUNT_ID, "feature_api_access", false);
        assertEquals(200, response.getStatus());
    }

    // --- non-master account, NOT entitled: returns 403 ---

    @Test
    void unentitledAccount_returns403WithEntitlementDeniedBody() throws ServletException, IOException {
        String accountId = UUID.randomUUID().toString();
        setAuthentication(accountId);
        mockEntitlementDenied(accountId);

        MockHttpServletRequest request = clientRequest("/api/v1/client/events");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertEquals(403, response.getStatus());
        assertEquals("application/json", response.getContentType());
        String body = response.getContentAsString();
        assertTrue(body.contains("\"error\":\"entitlement_denied\""));
        assertTrue(body.contains("\"feature\":\"feature_api_access\""));
        assertTrue(body.contains("\"upgradeUrl\":\"/select-plan\""));
    }

    // --- unentitled account on different client endpoints ---

    @Test
    void unentitledAccount_blockedOnEntitlementsEndpoint() throws ServletException, IOException {
        String accountId = UUID.randomUUID().toString();
        setAuthentication(accountId);
        mockEntitlementDenied(accountId);

        MockHttpServletRequest request = clientRequest("/api/v1/client/entitlements/check");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertEquals(403, response.getStatus());
    }

    @Test
    void unentitledAccount_blockedOnBillingEndpoint() throws ServletException, IOException {
        String accountId = UUID.randomUUID().toString();
        setAuthentication(accountId);
        mockEntitlementDenied(accountId);

        MockHttpServletRequest request = clientRequest("/api/v1/client/billing/invoices");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertEquals(403, response.getStatus());
    }

    // --- skipped paths ---

    @Test
    void optionsRequest_skipsFilter() throws ServletException, IOException {
        String accountId = UUID.randomUUID().toString();
        setAuthentication(accountId);

        MockHttpServletRequest request = clientRequest("/api/v1/client/events");
        request.setMethod("OPTIONS");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(clientEntitlementService, never()).checkEntitlement(anyString(), anyString(), anyString(), anyBoolean());
    }

    // --- no authentication set (unauthenticated request): filter passes through ---

    @Test
    void noAuthentication_passesThrough() throws ServletException, IOException {
        // No SecurityContext authentication set — filter should not block
        MockHttpServletRequest request = clientRequest("/api/v1/client/events");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(clientEntitlementService, never()).checkEntitlement(anyString(), anyString(), anyString(), anyBoolean());
        assertEquals(200, response.getStatus());
    }

    // --- helpers ---

    private MockHttpServletRequest clientRequest(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        request.setMethod("GET");
        return request;
    }

    private void setAuthentication(String accountId) {
        UserContext principal = new UserContext(accountId, null);
        UserContextAuthentication auth = new UserContextAuthentication(
                principal, List.of(new SimpleGrantedAuthority("ROLE_CLIENT")));
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void mockEntitlementAllowed(String accountId) {
        EntitlementResponse resp = new EntitlementResponse();
        resp.setAllowed(true);
        when(clientEntitlementService.checkEntitlement(accountId, MASTER_ACCOUNT_ID, "feature_api_access", false))
                .thenReturn(resp);
    }

    private void mockEntitlementDenied(String accountId) {
        EntitlementResponse resp = new EntitlementResponse();
        resp.setAllowed(false);
        when(clientEntitlementService.checkEntitlement(accountId, MASTER_ACCOUNT_ID, "feature_api_access", false))
                .thenReturn(resp);
    }
}
