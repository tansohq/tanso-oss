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
package com.tansoflow.tansocore.util;

import com.tansoflow.tansocore.model.apikey.type.BudgetPeriod;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/**
 * The window a budget is currently being spent in.
 *
 * Two shapes. <b>Tiling</b> windows start at the budget's anchor and repeat
 * every fixed length (MONTH = 30 days), so a budget set on the 14th resets on
 * the 14th-ish forever; that is what per-key budgets use. <b>Calendar</b>
 * windows snap to UTC midnight, Monday, or the 1st, which is what a finance
 * team expects a "monthly" team budget to mean. TOTAL never resets.
 *
 * @param start    first instant inside the window
 * @param resetsAt first instant after the window; null for TOTAL
 */
public record BudgetWindow(Instant start, Instant resetsAt) {

    public static BudgetWindow tiling(Instant anchor, BudgetPeriod period, Instant now) {
        if (anchor == null || period == null || period == BudgetPeriod.TOTAL) {
            return new BudgetWindow(anchor != null ? anchor : Instant.EPOCH, null);
        }
        Duration length = tilingLength(period);
        long elapsed = Duration.between(anchor, now).toSeconds();
        if (elapsed < 0) {
            return new BudgetWindow(anchor, anchor.plus(length));
        }
        long windowsPassed = elapsed / length.toSeconds();
        Instant start = anchor.plusSeconds(windowsPassed * length.toSeconds());
        return new BudgetWindow(start, start.plus(length));
    }

    public static BudgetWindow calendar(BudgetPeriod period, Instant now) {
        if (period == null || period == BudgetPeriod.TOTAL) {
            return new BudgetWindow(Instant.EPOCH, null);
        }
        ZonedDateTime utc = now.atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
        ZonedDateTime start = switch (period) {
            case DAY -> utc;
            case WEEK -> utc.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> utc.withDayOfMonth(1);
            case TOTAL -> throw new IllegalStateException("handled above");
        };
        ZonedDateTime next = switch (period) {
            case DAY -> start.plusDays(1);
            case WEEK -> start.plusWeeks(1);
            case MONTH -> start.plusMonths(1);
            case TOTAL -> throw new IllegalStateException("handled above");
        };
        return new BudgetWindow(start.toInstant(), next.toInstant());
    }

    private static Duration tilingLength(BudgetPeriod period) {
        return switch (period) {
            case DAY -> Duration.ofDays(1);
            case WEEK -> Duration.ofDays(7);
            case MONTH -> Duration.ofDays(30);
            case TOTAL -> Duration.ZERO;
        };
    }
}
