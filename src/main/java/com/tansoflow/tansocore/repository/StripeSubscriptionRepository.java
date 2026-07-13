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

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.StripeSubscription;
import com.tansoflow.tansocore.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StripeSubscriptionRepository extends JpaRepository<StripeSubscription, UUID> {
    boolean existsStripeSubscriptionBySubscriptionAndStripeSubscriptionExternalId(Subscription subscription, String stripeSubscriptionExternalId);

    boolean existsStripeSubscriptionBySubscription(Subscription subscription);

    boolean existsStripeSubscriptionByStripeSubscriptionExternalId(String stripeSubscriptionExternalId);

    StripeSubscription findStripeSubscriptionBySubscription(Subscription subscription);

    StripeSubscription findStripeSubscriptionByStripeSubscriptionExternalId(String stripeSubscriptionExternalId);

    StripeSubscription findByStripeSubscriptionExternalIdAndAccount(String stripeSubscriptionExternalId, Account account);
}
