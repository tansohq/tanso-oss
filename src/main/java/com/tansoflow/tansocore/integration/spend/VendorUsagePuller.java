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
package com.tansoflow.tansocore.integration.spend;

import com.tansoflow.tansocore.model.spend.type.VendorProvider;

import java.time.LocalDate;
import java.util.List;

/**
 * Reads an organisation's usage and cost out of a vendor's admin API. One
 * implementation per {@link VendorProvider}. Pullers are stateless; the
 * admin key comes in per call.
 */
public interface VendorUsagePuller {
    VendorProvider provider();

    /** Cheapest possible call that proves the key works. Throws {@link VendorApiException} otherwise. */
    void probe(String adminKey);

    /** Every row for days in [from, toExclusive). Callers keep windows to 31 days. */
    List<UsageBucketRecord> pull(String adminKey, LocalDate from, LocalDate toExclusive);
}
