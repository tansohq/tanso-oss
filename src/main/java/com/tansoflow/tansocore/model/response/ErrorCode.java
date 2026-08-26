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
package com.tansoflow.tansocore.model.response;

/**
 * Stable machine-readable error codes. Agents branch on these; messages are
 * for humans and may change. Add codes, never rename them.
 */
public enum ErrorCode {
    UNAUTHORIZED("unauthorized"),
    FORBIDDEN("forbidden"),
    NOT_FOUND("not_found"),
    VALIDATION_FAILED("validation_failed"),
    PAYMENT_REQUIRED("payment_required"),
    IDEMPOTENCY_CONFLICT("idempotency_conflict"),
    INSUFFICIENT_CREDITS("insufficient_credits"),
    CONFLICT("conflict"),
    RATE_LIMITED("rate_limited"),
    BUDGET_EXCEEDED("budget_exceeded"),
    VENDOR_ERROR("vendor_error"),
    INTERNAL_ERROR("internal_error");

    private final String code;

    ErrorCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
