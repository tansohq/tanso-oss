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

import com.tansoflow.tansocore.model.spend.VendorProbeResultDto;
import com.tansoflow.tansocore.model.spend.VendorSyncResultDto;

import java.time.LocalDate;

public interface VendorSyncService {
    /** Calls the vendor once with the stored key and records the outcome on the connection. */
    VendorProbeResultDto probe(String accountId, String connectionId);

    /** Pulls [from, to) and rewrites that window. Nulls default to the last 30 days. */
    VendorSyncResultDto sync(String accountId, String connectionId, LocalDate from, LocalDate to);

    /** Every connection on every account, last three days. One failure does not stop the others. */
    void syncAll();
}
