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

import com.tansoflow.tansocore.auth.SecurityErrorWriter;
import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.model.exception.IdempotencyConflictException;
import com.tansoflow.tansocore.model.response.ErrorCode;
import com.tansoflow.tansocore.service.internal.idempotency.IdempotencyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Opt-in transport-level idempotency for the client API: when a mutating
 * request carries an Idempotency-Key header, an identical retry within the
 * retention window replays the stored response instead of re-executing.
 * Runs after authentication and fails closed — a keyed request with no
 * authenticated account is rejected, never passed through unstored.
 */
@Slf4j
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "Idempotency-Key";
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PATCH", "PUT", "DELETE");

    private final IdempotencyService idempotencyService;

    public IdempotencyFilter(IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getHeader(HEADER) == null
                || !MUTATING_METHODS.contains(request.getMethod().toUpperCase());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String idempotencyKey = request.getHeader(HEADER);
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 255) {
            SecurityErrorWriter.write(response, HttpStatus.BAD_REQUEST.value(),
                    ErrorCode.VALIDATION_FAILED, "Idempotency-Key must be 1-255 characters");
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserContext ctx)
                || ctx.getAccountId() == null) {
            SecurityErrorWriter.write(response, HttpStatus.UNAUTHORIZED.value(),
                    ErrorCode.UNAUTHORIZED, "Authentication required");
            return;
        }
        UUID accountId = UUID.fromString(ctx.getAccountId());
        String endpoint = request.getMethod() + " " + request.getRequestURI();

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        // Force the body into the cache so the hash covers the full payload
        wrappedRequest.getInputStream().readAllBytes();
        String requestHash = idempotencyService.hash(wrappedRequest.getContentAsByteArray());

        Optional<IdempotencyService.StoredResponse> replay;
        try {
            replay = idempotencyService.findReplay(accountId, endpoint, idempotencyKey, requestHash);
        } catch (IdempotencyConflictException e) {
            SecurityErrorWriter.write(response, HttpStatus.CONFLICT.value(),
                    ErrorCode.IDEMPOTENCY_CONFLICT, e.getMessage());
            return;
        }
        if (replay.isPresent()) {
            response.setStatus(replay.get().status());
            response.setContentType("application/json");
            response.setHeader(HEADER, idempotencyKey);
            response.getWriter().write(replay.get().body() != null ? replay.get().body() : "");
            return;
        }

        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            // Store only completed 2xx/4xx outcomes; 5xx retries should re-execute
            int status = wrappedResponse.getStatus();
            if (status < 500) {
                String body = new String(wrappedResponse.getContentAsByteArray(), StandardCharsets.UTF_8);
                idempotencyService.store(accountId, endpoint, idempotencyKey, requestHash, status, body);
            }
            wrappedResponse.setHeader(HEADER, idempotencyKey);
            wrappedResponse.copyBodyToResponse();
        }
    }
}
