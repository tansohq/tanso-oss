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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/public/v1")
@Tag(name = "Public", description = "Public operations")
public class PublicController {
    private final UserService userService;

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticates a user and returns a JWT token")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content)
    })
    public ResponseEntity<ApiResponse<JwtResponse>> login(@Valid @RequestBody UsernameAndPasswordRequest request) {
        JwtResponse response = userService.generateJwtTokenForUser(request);

        ApiResponse<JwtResponse> apiResponse = ApiResponse.<JwtResponse>builder()
                .success(true)
                .data(response)
                .build();

        return ResponseEntity.status(200).body(apiResponse);
    }
}
