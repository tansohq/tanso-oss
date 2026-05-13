package com.tansoflow.tansocore.controller.tanso.account;

import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.entity.AccountApiKey;
import com.tansoflow.tansocore.model.account.response.AccountApiKeyResponse;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.service.internal.account.AccountService;
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
import static org.mockito.Mockito.when;

class AccountControllerTest {

    @Mock
    private AccountService accountService;

    @InjectMocks
    private AccountController accountController;

    private UserContext userContext;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        String accountId = UUID.randomUUID().toString();
        userContext = new UserContext(accountId, "test-token");
    }

    @Test
    void testGetAccountApiKey_Success() {
        AccountApiKey apiKey = new AccountApiKey();
        apiKey.setKeyValue("sk_test_12345");
        
        when(accountService.retrieveFirstApiKey(userContext.getAccountId())).thenReturn(apiKey);

        ResponseEntity<ApiResponse<AccountApiKeyResponse>> response = accountController.getAccountApiKey(userContext);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("sk_test_12345", response.getBody().getData().getApiKey());
        assertEquals("secret", response.getBody().getData().getKeyType());
    }
}
