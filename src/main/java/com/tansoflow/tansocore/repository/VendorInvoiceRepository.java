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

import com.tansoflow.tansocore.entity.VendorInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VendorInvoiceRepository extends JpaRepository<VendorInvoice, UUID> {
    List<VendorInvoice> findAllByAccountIdOrderByPeriodStartDesc(UUID accountId);

    Optional<VendorInvoice> findByIdAndAccountId(UUID id, UUID accountId);

    /** Invoices whose period touches [from, to]. */
    @Query("SELECT i FROM VendorInvoice i WHERE i.accountId = :accountId AND i.periodStart <= :to AND i.periodEnd >= :from")
    List<VendorInvoice> findOverlapping(UUID accountId, LocalDate from, LocalDate to);
}
