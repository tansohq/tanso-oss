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

import com.tansoflow.tansocore.entity.CheckoutSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CheckoutSessionRepository extends JpaRepository<CheckoutSession, UUID> {

    Optional<CheckoutSession> findByIdAndAccountId(UUID id, UUID accountId);

    Optional<CheckoutSession> findByStripeSessionId(String stripeSessionId);

    Optional<CheckoutSession> findFirstByCustomerIdAndPurposeOrderByCreatedAtDesc(UUID customerId, String purpose);

    // A direct subscription charge still waiting to be recorded, so a retry reuses its Stripe idempotency key.
    Optional<CheckoutSession> findFirstByCustomerIdAndPlanIdAndPurposeAndStatusOrderByCreatedAtDesc(
            UUID customerId, UUID planId, String purpose, String status);

    // Locked so the subscribe call recording a direct charge and customer.subscription.created recovering it
    // cannot both create the Tanso subscription or both record the spend.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cs FROM CheckoutSession cs WHERE cs.id = :id")
    Optional<CheckoutSession> findByIdForUpdate(UUID id);
}
