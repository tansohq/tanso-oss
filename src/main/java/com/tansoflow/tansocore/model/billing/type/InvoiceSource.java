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
package com.tansoflow.tansocore.model.billing.type;

/**
 * Who computed an invoice's amount. Tanso prices TANSO invoices, and its jobs may recalculate them. STRIPE invoices
 * are copies of invoices Stripe raised: their amount and lines are Stripe's, and Tanso never recalculates them,
 * whatever the account's Stripe mode is now.
 */
public enum InvoiceSource {
    TANSO,
    STRIPE
}
