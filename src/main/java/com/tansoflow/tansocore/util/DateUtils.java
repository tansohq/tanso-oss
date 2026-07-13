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

import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class DateUtils {

    private static final int MIN_DAY_OF_MONTH = 1;

    /**
     * Returns true if the given day exceeds the last valid day for the month of the provided date.
     * Throws IllegalArgumentException for day < 1.
     */
    public static boolean isDayExceedingMonthLength(int dayOfMonth, LocalDate date) {
        if (dayOfMonth < MIN_DAY_OF_MONTH) {
            throw new IllegalArgumentException("dayOfMonth must be >= " + MIN_DAY_OF_MONTH);
        }
        int maxDaysInMonth = date.lengthOfMonth();
        return dayOfMonth > maxDaysInMonth;
    }

    /**
     * Calculates the next billing date based on a base due date and an anchor day.
     * If the specified anchor day does not exist in the month of the base due date,
     * the billing date is adjusted to the last valid day of the month.
     *
     * @param baseDueDate the initial date to calculate from
     * @param anchorDay the target day of the month for the billing date
     * @return the calculated next billing date
     * @throws IllegalArgumentException if the anchorDay is less than 1
     */
    public static LocalDate calculateNextBillingDate(LocalDate baseDueDate, short anchorDay) {
        LocalDate nextDueDate;

        if (baseDueDate.getDayOfMonth() != anchorDay) {
            // If the desired anchor day doesn't exist in this month, clamp to the month's end.
            nextDueDate = DateUtils.isDayExceedingMonthLength(anchorDay, baseDueDate)
                    ? baseDueDate.withDayOfMonth(baseDueDate.lengthOfMonth())
                    : baseDueDate.withDayOfMonth(anchorDay);
        } else {
            nextDueDate = baseDueDate;
        }

        return nextDueDate;
    }

}
