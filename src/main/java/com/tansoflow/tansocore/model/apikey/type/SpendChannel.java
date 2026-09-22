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
package com.tansoflow.tansocore.model.apikey.type;

/**
 * How money reached the operator. The spend mandate is the principal's consent for charges made with no human
 * present, so it counts only OFF_SESSION rows. The key budget counts both: the key caused the spend either way.
 */
public enum SpendChannel {
    /** Nobody was on a payment page: a saved card charged off-session, or credits drawn down by usage. */
    OFF_SESSION,
    /** A human paid in person on a Stripe-hosted page (Checkout or a hosted invoice), or settled the invoice. */
    HOSTED
}
