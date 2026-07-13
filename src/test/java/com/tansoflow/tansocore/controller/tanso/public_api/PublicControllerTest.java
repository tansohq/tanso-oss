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
