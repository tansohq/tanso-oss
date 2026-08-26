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

import com.tansoflow.tansocore.model.spend.type.OutcomeSource;

import java.time.Instant;
import java.util.List;

/** Reads shipped work out of an engineering system. Stateless; token and scope come in per call. */
public interface OutcomePuller {
    OutcomeSource source();

    /** Cheapest call that proves the token works for the scope. Throws {@link com.tansoflow.tansocore.model.exception.VendorApiException} otherwise. */
    void probe(String token, String scope);

    /** Outcomes that completed in [from, to). */
    List<OutcomeRecord> pull(String token, String scope, Instant from, Instant to);
}
