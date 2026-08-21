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
package com.tansoflow.tansocore.model.exception;

import com.tansoflow.tansocore.model.apikey.type.SpendKind;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The calling API key would exceed its own budget. Carries the numbers so an
 * agent can decide whether to wait for the window to reset or ask a human to
 * raise the ceiling, rather than retrying blindly.
 */
@Getter
public class BudgetExceededException extends RuntimeException {

    private final SpendKind kind;
    private final BigDecimal limit;
    private final BigDecimal spent;
    private final BigDecimal requested;
    private final Instant resetsAt;

    public BudgetExceededException(SpendKind kind, BigDecimal limit, BigDecimal spent,
                                   BigDecimal requested, Instant resetsAt) {
        super("This API key's " + (kind == SpendKind.CREDITS ? "credit" : "spend")
                + " budget of " + limit + " would be exceeded: " + spent
                + " already used, " + requested + " requested"
                + (resetsAt != null ? ", window resets at " + resetsAt : ""));
        this.kind = kind;
        this.limit = limit;
        this.spent = spent;
        this.requested = requested;
        this.resetsAt = resetsAt;
    }

    public BigDecimal getRemaining() {
        BigDecimal remaining = limit.subtract(spent);
        return remaining.signum() < 0 ? BigDecimal.ZERO : remaining;
    }
}
