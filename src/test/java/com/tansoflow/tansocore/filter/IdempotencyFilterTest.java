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
import com.tansoflow.tansocore.model.exception.IdempotencyConflictException;
import com.tansoflow.tansocore.service.internal.idempotency.IdempotencyService;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyFilterTest {

    @Mock
    private IdempotencyService idempotencyService;

    private IdempotencyFilter filter;
    private final UUID accountId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        filter = new IdempotencyFilter(idempotencyService);
        lenient().when(idempotencyService.hash(any())).thenReturn("hash-1");
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate() {
        UserContext ctx = new UserContext(accountId.toString(), null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(ctx, null, List.of()));
    }

    private MockHttpServletRequest post(String key) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/client/subscriptions");
        request.setContent("{\"planId\":\"p1\"}".getBytes());
        if (key != null) {
            request.addHeader(IdempotencyFilter.HEADER, key);
        }
        return request;
    }

    @Test
    void passesThroughWithoutHeader() throws ServletException, IOException {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(post(null), response, chain);
        assertThat(chain.getRequest()).isNotNull();
        verify(idempotencyService, never()).findReplay(any(), anyString(), anyString(), anyString());
    }

    @Test
    void rejectsKeyedRequestWithoutAuthentication() throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(post("key-1"), response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"unauthorized\"");
    }

    @Test
    void replaysStoredResponse() throws ServletException, IOException {
        authenticate();
        when(idempotencyService.findReplay(eq(accountId), anyString(), eq("key-1"), eq("hash-1")))
                .thenReturn(Optional.of(new IdempotencyService.StoredResponse(201, "{\"data\":\"stored\"}")));

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(post("key-1"), response, chain);

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).isEqualTo("{\"data\":\"stored\"}");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void conflictingKeyReturns409() throws ServletException, IOException {
        authenticate();
        when(idempotencyService.findReplay(eq(accountId), anyString(), eq("key-1"), eq("hash-1")))
                .thenThrow(new IdempotencyConflictException("key reused with different body"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(post("key-1"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("\"code\":\"idempotency_conflict\"");
    }

    @Test
    void storesFirstExecutionResult() throws ServletException, IOException {
        authenticate();
        when(idempotencyService.findReplay(eq(accountId), anyString(), eq("key-1"), eq("hash-1")))
                .thenReturn(Optional.empty());

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(post("key-1"), response, new MockFilterChain());

        verify(idempotencyService).store(eq(accountId), eq("POST /api/v1/client/subscriptions"),
                eq("key-1"), eq("hash-1"), eq(200), anyString());
    }

    @Test
    void rejectsOverlongKey() throws ServletException, IOException {
        authenticate();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(post("x".repeat(256)), response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("\"code\":\"validation_failed\"");
    }
}
