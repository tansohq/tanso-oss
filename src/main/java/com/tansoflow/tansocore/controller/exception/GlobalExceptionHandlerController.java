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

import com.tansoflow.tansocore.model.exception.AuthenticationException;
import com.tansoflow.tansocore.model.exception.BudgetExceededException;
import com.tansoflow.tansocore.model.exception.CreditLimitExceededException;
import com.tansoflow.tansocore.model.exception.IdempotencyConflictException;
import com.tansoflow.tansocore.model.exception.RateLimitExceededException;
import com.tansoflow.tansocore.model.exception.InvalidRuleValueException;
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.model.exception.TariffConflictException;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.model.response.Error;
import com.tansoflow.tansocore.model.response.ErrorCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;
import java.util.stream.Collectors;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandlerController {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {
        String errorId = assignErrorId();
        log.error("Exception caught [errorId={}]: {}", errorId, exception.getMessage(), exception);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(processErrorMessage(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), errorId, ErrorCode.INTERNAL_ERROR));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolationException(DataIntegrityViolationException exception) {
        String errorId = assignErrorId();
        log.error("Data integrity violation [errorId={}]: {}", errorId, exception.getMessage(), exception);

        String rootMessage = exception.getMostSpecificCause().getMessage();
        if (rootMessage != null && (rootMessage.contains("unique constraint") || rootMessage.contains("duplicate key") || rootMessage.contains("Unique index or primary key"))) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(processErrorMessage("A record with this key already exists. Please use a different key.", errorId, ErrorCode.CONFLICT));
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(processErrorMessage(HttpStatus.BAD_REQUEST.getReasonPhrase(), errorId, ErrorCode.VALIDATION_FAILED));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException exception) {
        String errorId = assignErrorId();
        log.warn("Authentication failed [errorId={}]: {}", errorId, exception.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(processErrorMessage("Invalid credentials", errorId, ErrorCode.UNAUTHORIZED));
    }

    @ExceptionHandler(CreditLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleCreditLimitExceededException(CreditLimitExceededException exception) {
        String errorId = assignErrorId();
        log.info("Credit limit exceeded [errorId={}]: {}", errorId, exception.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(processErrorMessage(exception.getMessage(), errorId, ErrorCode.INSUFFICIENT_CREDITS));
    }

    @ExceptionHandler(BudgetExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleBudgetExceededException(BudgetExceededException exception) {
        String errorId = assignErrorId();
        log.info("Key budget exceeded [errorId={}]: {}", errorId, exception.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(processErrorMessage(exception.getMessage(), errorId, ErrorCode.BUDGET_EXCEEDED));
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleIdempotencyConflictException(IdempotencyConflictException exception) {
        String errorId = assignErrorId();
        log.info("Idempotency conflict [errorId={}]: {}", errorId, exception.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(processErrorMessage(exception.getMessage(), errorId, ErrorCode.IDEMPOTENCY_CONFLICT));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimitExceededException(RateLimitExceededException exception) {
        String errorId = assignErrorId();
        log.info("Rate limit exceeded [errorId={}]: {}", errorId, exception.getMessage());

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(exception.getRetryAfterSeconds()))
                .body(processErrorMessage(exception.getMessage(), errorId, ErrorCode.RATE_LIMITED));
    }

    @ExceptionHandler(TariffConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleTariffConflictException(TariffConflictException exception) {
        String errorId = assignErrorId();
        log.info("Tariff publish conflict [errorId={}]: {}", errorId, exception.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(processErrorMessage(exception.getMessage(), errorId, ErrorCode.CONFLICT));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(ResourceNotFoundException exception) {
        String errorId = assignErrorId();
        log.warn("Resource not found [errorId={}]: {}", errorId, exception.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(processErrorMessage(exception.getMessage(), errorId, ErrorCode.NOT_FOUND));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(org.springframework.security.access.AccessDeniedException exception) {
        String errorId = assignErrorId();
        log.warn("Access denied [errorId={}]: {}", errorId, exception.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(processErrorMessage(exception.getMessage(), errorId, ErrorCode.FORBIDDEN));
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthorizationDeniedException(AuthorizationDeniedException exception) {
        String errorId = assignErrorId();
        log.warn("Authorization denied [errorId={}]: {}", errorId, exception.getMessage(), exception);

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(processErrorMessage(HttpStatus.FORBIDDEN.getReasonPhrase(), errorId, ErrorCode.FORBIDDEN));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException exception) {
        String errorId = assignErrorId();
        log.warn("IllegalArgumentException [errorId={}]: {}", errorId, exception.getMessage(), exception);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(processErrorMessage(exception.getMessage(), errorId, ErrorCode.VALIDATION_FAILED));
    }

    @ExceptionHandler(InvalidRuleValueException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRuleValueException(InvalidRuleValueException exception) {
        String errorId = assignErrorId();
        log.warn("InvalidRuleValueException [errorId={}]: {}", errorId, exception.getMessage(), exception);

        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder()
                .success(false)
                .error(new Error(ErrorCode.VALIDATION_FAILED, exception.getMessage() + " (errorId=" + errorId + ")", exception.getDetail()))
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiResponse);
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotSupportedException(UnsupportedOperationException exception) {
        String errorId = assignErrorId();
        log.warn("UnsupportedOperationException [errorId={}]: {}", errorId, exception.getMessage(), exception);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(processErrorMessage(exception.getMessage(), errorId, ErrorCode.VALIDATION_FAILED));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException exception) {
        String errorId = assignErrorId();
        String details = exception.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation failed [errorId={}]: {}", errorId, details);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(processErrorMessage("Validation failed: " + details, errorId, ErrorCode.VALIDATION_FAILED));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestParameter(MissingServletRequestParameterException exception) {
        String errorId = assignErrorId();
        log.warn("Missing request parameter [errorId={}]: {}", errorId, exception.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(processErrorMessage(
                "Missing required parameter: " + exception.getParameterName(), errorId, ErrorCode.VALIDATION_FAILED));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException exception) {
        String errorId = assignErrorId();
        log.warn("Parameter type mismatch [errorId={}]: {}", errorId, exception.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(processErrorMessage(
                "Invalid value for parameter: " + exception.getName(), errorId, ErrorCode.VALIDATION_FAILED));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException exception) {
        String errorId = assignErrorId();
        log.warn("Malformed request body [errorId={}]: {}", errorId, exception.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(processErrorMessage("Malformed request body", errorId, ErrorCode.VALIDATION_FAILED));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException exception) {
        String errorId = assignErrorId();
        String details = exception.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(", "));
        log.warn("Constraint violation [errorId={}]: {}", errorId, details);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(processErrorMessage("Constraint violation: " + details, errorId, ErrorCode.VALIDATION_FAILED));
    }

    // Entity-level validation failures surface at commit time wrapped in
    // TransactionSystemException, which otherwise falls through to the
    // catch-all and reports a 500 with no hint of the offending field.
    @ExceptionHandler(TransactionSystemException.class)
    public ResponseEntity<ApiResponse<Void>> handleTransactionSystem(TransactionSystemException exception) {
        Throwable cause = exception.getRootCause();
        if (cause instanceof ConstraintViolationException violation) {
            return handleConstraintViolation(violation);
        }
        return handleException(exception);
    }

    private static String assignErrorId() {
        // Generate a per-instance unique ID
        String errorId = UUID.randomUUID().toString();
        // Put it into MDC so all logs for this thread include it
        MDC.put("errorId", errorId);
        return errorId;
    }

    private static ApiResponse<Void> processErrorMessage(String message, String errorId, ErrorCode code) {
        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder()
                .success(false)
                .error(new Error(code, message + " (errorId=" + errorId + ")"))
                .build();

        log.error("Error processed [errorId={}]: {}", errorId, message);
        return apiResponse;
    }
}
