package com.tansoflow.tansocore.controller.tanso.public_api;

import com.tansoflow.tansocore.model.account.request.UsernameAndPasswordRequest;
import com.tansoflow.tansocore.model.auth.response.JwtResponse;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.service.internal.account.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class PublicControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private PublicController publicController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testLogin_Success() {
        UsernameAndPasswordRequest request = new UsernameAndPasswordRequest();
        request.setUsername("testuser");
        request.setPassword("password");

        JwtResponse jwtResponse = new JwtResponse();
        jwtResponse.setToken("test-jwt-token");

        when(userService.generateJwtTokenForUser(request)).thenReturn(jwtResponse);

        ResponseEntity<ApiResponse<JwtResponse>> response = publicController.login(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("test-jwt-token", response.getBody().getData().getToken());
    }
}
