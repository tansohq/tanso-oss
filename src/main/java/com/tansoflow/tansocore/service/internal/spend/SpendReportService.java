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
package com.tansoflow.tansocore.service.internal.spend;

import com.tansoflow.tansocore.model.spend.SpendReconcileReportDto;
import com.tansoflow.tansocore.model.spend.SpendUsageReportDto;

import java.time.LocalDate;

public interface SpendReportService {
    /** [from, to) — nulls default to the last 30 days. */
    SpendUsageReportDto usage(String accountId, LocalDate from, LocalDate to);

    /** [from, to] inclusive, matching invoice periods — nulls default to the last full calendar month. */
    SpendReconcileReportDto reconcile(String accountId, LocalDate from, LocalDate to);
}
