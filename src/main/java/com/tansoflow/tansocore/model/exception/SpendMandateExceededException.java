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

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * An off-session charge would go past the spend mandate the customer's principal approved.
 * Two cases an agent must tell apart: the charge alone is larger than the whole mandate
 * (only a higher mandate helps), or it fits the mandate but not what is left of this window
 * (waiting helps).
 */
@Getter
public class SpendMandateExceededException extends RuntimeException {

    private final String customerReferenceId;
    private final BigDecimal limit;
    private final BigDecimal spent;
    private final BigDecimal requested;
    private final String period;
    private final Instant resetsAt;

    public SpendMandateExceededException(String customerReferenceId, BigDecimal limit, BigDecimal spent,
                                         BigDecimal requested, String period, Instant resetsAt) {
        super(message(limit, spent, requested, period, resetsAt));
        this.customerReferenceId = customerReferenceId;
        this.limit = limit;
        this.spent = spent;
        this.requested = requested;
        this.period = period;
        this.resetsAt = resetsAt;
    }

    /** True when no amount of waiting lets this charge through. */
    public boolean isAboveWholeMandate() {
        return requested.compareTo(limit) > 0;
    }

    private static String message(BigDecimal limit, BigDecimal spent, BigDecimal requested, String period,
                                  Instant resetsAt) {
        if (requested.compareTo(limit) > 0) {
            return "This charge (" + money(requested) + ") is above the " + money(limit) + " per " + period
                    + " your principal approved; give your principal url to approve a higher limit, or buy less.";
        }
        return "This charge (" + money(requested) + ") would pass the " + money(limit) + " per " + period
                + " your principal approved: " + money(spent) + " already spent in this window, which resets at "
                + resetsAt + "; wait for the window to reset or buy less.";
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
