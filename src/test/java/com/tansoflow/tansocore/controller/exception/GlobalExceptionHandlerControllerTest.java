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

    @Test
    void budgetExceeded_Returns403BudgetGateWithRetryAfter() {
        java.time.Instant resetsAt = java.time.Instant.now().plusSeconds(3600);
        ResponseEntity<ApiResponse<Void>> response = handler.handleBudgetExceededException(
                new com.tansoflow.tansocore.model.exception.BudgetExceededException(
                        com.tansoflow.tansocore.model.apikey.type.SpendKind.MONEY,
                        new java.math.BigDecimal("50"), new java.math.BigDecimal("45"), new java.math.BigDecimal("10"), resetsAt));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        com.tansoflow.tansocore.model.response.GateError error =
                (com.tansoflow.tansocore.model.response.GateError) response.getBody().getError();
        assertEquals("budget_exceeded", error.getCode());
        assertEquals("budget", error.getGate());
        assertEquals("wait", error.getAction());
        assertEquals(null, error.getUrl());
        assertEquals(null, error.getPoll());
        assertTrue(error.getRetryAfter() > 3500 && error.getRetryAfter() <= 3600);
        assertTrue(error.getDetail().startsWith("errorId="));
    }

    @Test
    void otherCustomer_Returns403ForbiddenWithUseOwnReference() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleAccessDeniedException(
                new com.tansoflow.tansocore.auth.CustomerAccessGuard.OtherCustomerException("This API key is scoped to another customer"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        com.tansoflow.tansocore.model.response.GateError error =
                (com.tansoflow.tansocore.model.response.GateError) response.getBody().getError();
        assertEquals("forbidden", error.getCode());
        assertEquals("scope", error.getGate());
        assertEquals("use_own_reference", error.getAction());
        assertTrue(error.getMessage().contains("customerReferenceId"));
    }

    @Test
    void missingPurchaseScope_Returns403ScopeDenied() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleAccessDeniedException(
                new com.tansoflow.tansocore.auth.CustomerAccessGuard.MissingScopeException("This API key lacks the 'purchase' scope"));

        com.tansoflow.tansocore.model.response.GateError error =
                (com.tansoflow.tansocore.model.response.GateError) response.getBody().getError();
        assertEquals("scope_denied", error.getCode());
        assertEquals("request_scope", error.getAction());
        assertTrue(error.getMessage().startsWith("This API key lacks the 'purchase' scope"));
    }

    @Test
    void otherAccessDenied_Returns403ForbiddenEndpointNotOpen() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleAccessDeniedException(
                new org.springframework.security.access.AccessDeniedException("Access Denied"));

        com.tansoflow.tansocore.model.response.GateError error =
                (com.tansoflow.tansocore.model.response.GateError) response.getBody().getError();
        assertEquals("forbidden", error.getCode());
        assertEquals("request_scope", error.getAction());
    }

    @Test
    void authorizationDenied_Returns403ForbiddenEndpointNotOpen() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleAuthorizationDeniedException(
                new org.springframework.security.authorization.AuthorizationDeniedException("Access Denied"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        com.tansoflow.tansocore.model.response.GateError error =
                (com.tansoflow.tansocore.model.response.GateError) response.getBody().getError();
        assertEquals("forbidden", error.getCode());
        assertEquals("scope", error.getGate());
        assertEquals("request_scope", error.getAction());
    }

    @Test
    void budgetWithoutWindow_ReturnsSpendCapExceeded() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleBudgetExceededException(
                com.tansoflow.tansocore.model.exception.BudgetExceededException.perTransaction(
                        new java.math.BigDecimal("50"), new java.math.BigDecimal("80")));

        com.tansoflow.tansocore.model.response.GateError error =
                (com.tansoflow.tansocore.model.response.GateError) response.getBody().getError();
        assertEquals("spend_cap_exceeded", error.getCode());
        assertEquals("raise_spend_cap", error.getAction());
        assertEquals(null, error.getRetryAfter());
        assertTrue(error.getMessage().endsWith("ask the operator to raise the cap."));
    }

    @Test
    void chargeLargerThanTheWholeKeyBudget_SaysRaiseTheCapNotWait() {
        // KeyBudgetServiceImpl throws with no reset time when the charge alone is over the budget.
        ResponseEntity<ApiResponse<Void>> response = handler.handleBudgetExceededException(
                new com.tansoflow.tansocore.model.exception.BudgetExceededException(
                        com.tansoflow.tansocore.model.apikey.type.SpendKind.MONEY, new java.math.BigDecimal("50"),
                        java.math.BigDecimal.ZERO, new java.math.BigDecimal("60"), null));

        com.tansoflow.tansocore.model.response.GateError error =
                (com.tansoflow.tansocore.model.response.GateError) response.getBody().getError();
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("spend_cap_exceeded", error.getCode());
        assertEquals("raise_spend_cap", error.getAction());
        assertEquals(null, error.getRetryAfter());
    }

    @Test
    void chargeLargerThanTheWholeMandate_RaiseMandateWithTheEndpointUrl() {
        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest(
                "POST", "/api/v1/client/credits/purchases");
        request.setServerName("tanso.example");
        request.setServerPort(443);
        request.setScheme("https");

        ResponseEntity<ApiResponse<Void>> response = handler.handleSpendMandateExceededException(
                new com.tansoflow.tansocore.model.exception.SpendMandateExceededException("agent_abc",
                        new java.math.BigDecimal("50"), java.math.BigDecimal.ZERO, new java.math.BigDecimal("60"),
                        "month", java.time.Instant.now().plusSeconds(3600)), request);

        com.tansoflow.tansocore.model.response.GateError error =
                (com.tansoflow.tansocore.model.response.GateError) response.getBody().getError();
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("budget", error.getGate());
        assertEquals("raise_mandate", error.getAction());
        assertEquals(null, error.getRetryAfter());
        assertEquals("https://tanso.example/api/v1/client/customers/agent_abc/spend-mandate", error.getUrl());
        assertEquals("This charge (60.00) is above the 50.00 per month your principal approved; give your principal"
                + " url to approve a higher limit, or buy less.", error.getMessage());
    }

    @Test
    void chargeThatFitsTheMandateButNotThisWindow_Waits() {
        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest(
                "POST", "/api/v1/client/credits/purchases");

        ResponseEntity<ApiResponse<Void>> response = handler.handleSpendMandateExceededException(
                new com.tansoflow.tansocore.model.exception.SpendMandateExceededException("agent_abc",
                        new java.math.BigDecimal("50"), new java.math.BigDecimal("40"), new java.math.BigDecimal("20"),
                        "month", java.time.Instant.now().plusSeconds(3600)), request);

        com.tansoflow.tansocore.model.response.GateError error =
                (com.tansoflow.tansocore.model.response.GateError) response.getBody().getError();
        assertEquals("budget_exceeded", error.getCode());
        assertEquals("wait", error.getAction());
        assertEquals(null, error.getUrl());
        assertNotNull(error.getRetryAfter());
        assertTrue(error.getRetryAfter() > 3500 && error.getRetryAfter() <= 3600);
    }
}
