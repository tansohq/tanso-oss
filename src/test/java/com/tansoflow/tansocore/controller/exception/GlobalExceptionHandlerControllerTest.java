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
package com.tansoflow.tansocore.controller.exception;

import com.tansoflow.tansocore.model.exception.CreditLimitExceededException;
import com.tansoflow.tansocore.model.response.ApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerControllerTest {

    private final GlobalExceptionHandlerController handler = new GlobalExceptionHandlerController();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void errorCodesArePresentAndStable() {
        assertEquals("insufficient_credits", handler.handleCreditLimitExceededException(
                new CreditLimitExceededException("depleted")).getBody().getError().getCode());
        assertEquals("not_found", handler.handleResourceNotFoundException(
                new com.tansoflow.tansocore.model.exception.ResourceNotFoundException("missing")).getBody().getError().getCode());
        assertEquals("validation_failed", handler.handleIllegalArgumentException(
                new IllegalArgumentException("bad")).getBody().getError().getCode());
        assertEquals("internal_error", handler.handleException(
                new RuntimeException("boom")).getBody().getError().getCode());
        assertEquals("idempotency_conflict", handler.handleIdempotencyConflictException(
                new com.tansoflow.tansocore.model.exception.IdempotencyConflictException("reused")).getBody().getError().getCode());
    }

    @Test
    void creditLimitExceeded_ReturnsConflict() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleCreditLimitExceededException(
                new CreditLimitExceededException("Credit pool depleted - hard limit active"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertNotNull(response.getBody().getError());
        assertTrue(response.getBody().getError().getMessage().contains("Credit pool depleted"));
    }

    @Test
    void missingRequestParameter_ReturnsBadRequest() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleMissingServletRequestParameter(
                new MissingServletRequestParameterException("periodStart", "Instant"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertTrue(response.getBody().getError().getMessage().contains("periodStart"));
    }

    @Test
    void parameterTypeMismatch_ReturnsBadRequest() {
        MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
                "not-a-date", java.time.Instant.class, "periodStart", null, new IllegalArgumentException("bad"));

        ResponseEntity<ApiResponse<Void>> response = handler.handleMethodArgumentTypeMismatch(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertTrue(response.getBody().getError().getMessage().contains("periodStart"));
    }
}
