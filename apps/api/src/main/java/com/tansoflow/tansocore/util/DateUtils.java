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
