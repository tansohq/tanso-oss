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
package com.tansoflow.tansocore.controller.tanso.account;

import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.entity.AccountApiKey;
import com.tansoflow.tansocore.model.account.response.AccountApiKeyResponse;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.service.internal.account.AccountService;
import com.tansoflow.tansocore.service.internal.audit.AuditHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountControllerTest {

    @Mock
    private AccountService accountService;

    @Mock
    private AuditHelper auditHelper;

    @InjectMocks
    private AccountController accountController;

    private UserContext userContext;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        String accountId = UUID.randomUUID().toString();
        userContext = new UserContext(UUID.randomUUID().toString(), accountId, "test@example.com", "test-token");
    }

    @Test
    void testGetAccountApiKey_Success() {
        AccountApiKey apiKey = new AccountApiKey();
        apiKey.setKeyValue("sk_test_1234567890abcdef");
        apiKey.setKeyType("secret");

        when(accountService.retrieveFirstApiKey(userContext.getAccountId())).thenReturn(apiKey);

        ResponseEntity<ApiResponse<AccountApiKeyResponse>> response = accountController.getAccountApiKey(userContext);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("sk_test_************cdef", response.getBody().getData().getApiKey());
        assertEquals("secret", response.getBody().getData().getKeyType());
    }

    @Test
    void testRotateAccountApiKey_ReturnsRawKeyOnce() {
        AccountApiKey apiKey = new AccountApiKey();
        apiKey.setKeyValue("sk_test_new-secret-value");
        apiKey.setKeyType("secret");

        when(accountService.rotateApiKey(userContext.getAccountId())).thenReturn(apiKey);

        ResponseEntity<ApiResponse<AccountApiKeyResponse>> response = accountController.rotateAccountApiKey(userContext);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("sk_test_new-secret-value", response.getBody().getData().getApiKey());
        verify(accountService).rotateApiKey(userContext.getAccountId());
    }
}
