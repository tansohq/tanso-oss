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
package com.tansoflow.tansocore.service.client;

import com.tansoflow.tansocore.entity.Customer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * State changes on a customer an agent created for itself. Payment is the only
 * claim: no OTP, no link, no CAPTCHA. Everything here is idempotent because it
 * is called from webhooks that replay.
 */
public interface AgentLifecycleService {

    /** PROVISIONAL → CLAIMED. No-op for human-created or already claimed customers. */
    void claimOnPayment(Customer customer);

    void claimOnPayment(UUID customerId, UUID accountId);

    void setOwnerEmail(Customer customer, String email);

    /**
     * Saved card arrived for a spend mandate: cap the signup key's money budget at
     * {@code maxAmount} per {@code period}, store the card as default, claim the customer.
     */
    void activateSpendMandate(UUID accountId, UUID customerId, UUID apiKeyId, String paymentMethodId,
                              BigDecimal maxAmount, String period);

    /** PROVISIONAL and past expiry with nothing paid → EXPIRED, keys deactivated. Returns how many. */
    int expireProvisional(Instant now);
}
