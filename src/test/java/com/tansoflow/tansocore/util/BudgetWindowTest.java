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
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BudgetWindowTest {

    private static final Instant ANCHOR = Instant.parse("2026-08-14T10:30:00Z");

    @Test
    void tilingDayWindowRepeatsFromAnchor() {
        BudgetWindow w = BudgetWindow.tiling(ANCHOR, BudgetPeriod.DAY, Instant.parse("2026-08-16T09:00:00Z"));
        assertEquals(Instant.parse("2026-08-15T10:30:00Z"), w.start());
        assertEquals(Instant.parse("2026-08-16T10:30:00Z"), w.resetsAt());
    }

    @Test
    void tilingMonthIsThirtyDays() {
        BudgetWindow w = BudgetWindow.tiling(ANCHOR, BudgetPeriod.MONTH, Instant.parse("2026-09-20T00:00:00Z"));
        assertEquals(Instant.parse("2026-09-13T10:30:00Z"), w.start());
        assertEquals(Instant.parse("2026-10-13T10:30:00Z"), w.resetsAt());
    }

    @Test
    void tilingBeforeAnchorIsFirstWindow() {
        BudgetWindow w = BudgetWindow.tiling(ANCHOR, BudgetPeriod.WEEK, ANCHOR.minusSeconds(60));
        assertEquals(ANCHOR, w.start());
        assertEquals(ANCHOR.plusSeconds(7 * 86400), w.resetsAt());
    }

    @Test
    void totalNeverResets() {
        BudgetWindow w = BudgetWindow.tiling(ANCHOR, BudgetPeriod.TOTAL, Instant.parse("2027-01-01T00:00:00Z"));
        assertEquals(ANCHOR, w.start());
        assertNull(w.resetsAt());
        assertEquals(Instant.EPOCH, BudgetWindow.tiling(null, BudgetPeriod.DAY, ANCHOR).start());
    }

    @Test
    void calendarDaySnapsToUtcMidnight() {
        BudgetWindow w = BudgetWindow.calendar(BudgetPeriod.DAY, Instant.parse("2026-08-25T17:45:00Z"));
        assertEquals(Instant.parse("2026-08-25T00:00:00Z"), w.start());
        assertEquals(Instant.parse("2026-08-26T00:00:00Z"), w.resetsAt());
    }

    @Test
    void calendarWeekStartsMonday() {
        // 2026-08-27 is a Thursday
        BudgetWindow w = BudgetWindow.calendar(BudgetPeriod.WEEK, Instant.parse("2026-08-27T12:00:00Z"));
        assertEquals(Instant.parse("2026-08-24T00:00:00Z"), w.start());
        assertEquals(Instant.parse("2026-08-31T00:00:00Z"), w.resetsAt());
    }

    @Test
    void calendarMonthStartsOnTheFirstAndHandlesShortMonths() {
        BudgetWindow w = BudgetWindow.calendar(BudgetPeriod.MONTH, Instant.parse("2026-02-10T00:00:00Z"));
        assertEquals(Instant.parse("2026-02-01T00:00:00Z"), w.start());
        assertEquals(Instant.parse("2026-03-01T00:00:00Z"), w.resetsAt());
    }

    @Test
    void calendarTotalNeverResets() {
        BudgetWindow w = BudgetWindow.calendar(BudgetPeriod.TOTAL, ANCHOR);
        assertEquals(Instant.EPOCH, w.start());
        assertNull(w.resetsAt());
    }
}
