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
package com.tansoflow.tansocore.repository;

import com.tansoflow.tansocore.entity.Invoice;
import com.tansoflow.tansocore.entity.StripeInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface StripeInvoiceRepository extends JpaRepository <StripeInvoice, UUID>{
    boolean existsStripeInvoiceByInvoice(Invoice invoice);

    StripeInvoice findStripeInvoiceByInvoice(Invoice invoice);

    @Query("SELECT si FROM StripeInvoice si WHERE si.stripeInvoiceExternalId = :invoiceId")
    StripeInvoice findStripeInvoiceByStripeInvoiceId(String invoiceId);

    StripeInvoice findStripeInvoiceByStripeInvoiceExternalId(String invoiceId);

    boolean existsStripeInvoiceByStripeInvoiceExternalId(String stripeInvoiceExternalId);
}
