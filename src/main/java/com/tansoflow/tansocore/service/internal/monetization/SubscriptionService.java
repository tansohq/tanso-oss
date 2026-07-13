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
package com.tansoflow.tansocore.service.internal.monetization;

import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.model.subscription.SubscriptionDto;
import com.tansoflow.tansocore.model.subscription.SubscriptionScheduledChangeDto;
import com.tansoflow.tansocore.model.subscription.request.ClientSubscriptionRequest;
import com.tansoflow.tansocore.model.subscription.request.SubscriptionRequest;
import com.tansoflow.tansocore.model.subscription.response.SubscribedCustomerResponse;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface SubscriptionService {
    @Transactional
    SubscribedCustomerResponse clientSubscribeCustomer(ClientSubscriptionRequest request, String accountId);

    @Transactional
    SubscribedCustomerResponse subscribeCustomer(SubscriptionRequest request, String accountId);

    @Transactional
    SubscribedCustomerResponse subscribe(Customer customer, Plan plan, String accountId);

    Subscription createSubscription(String customerUuid, String planUuid, String accountId);

    // TODO implement as find subscriptions by customer
    List<SubscriptionDto> getSubscriptionsByCustomer(String customerUuid, String accountId);

    List<SubscriptionDto> getSubscriptionsByAccount(String accountId);

    void deleteSubscription(Subscription subscription);

    SubscriptionDto editSubscriptionById(SubscriptionRequest request, String subscriptionId, String accountId);

    Subscription getSubscriptionById(String subscriptionId, String accountId);

    SubscriptionDto getSubscriptionDtoById(String subscriptionId, String accountId);

    @Transactional
    void cancelSubscription(String subscriptionId, String cancelMode, String accountId);

    @Transactional
    void subscriptionInvoicePaid(String invoiceId, String accountId);

    @Transactional
    void upgradeSubscription(String currentSubscriptionId, String accountId, String newPlanId, boolean grantNow);

    void scheduleDowngradeSubscription(String currentSubscriptionId, String accountId, String newPlanId);

    void processScheduledDowngrades();

    void processSubscriptionCycles();

    void processCancellations();

    @Transactional
    void cancelScheduledChangesForSubscription(UUID subscriptionId, UUID accountId);

    void cancelScheduledSubscriptionCancellation(String subscriptionId, String accountId);

    List<SubscriptionScheduledChangeDto> getScheduledChangesByAccount(String accountId);

    List<SubscriptionDto> getScheduledCancellationsByAccount(String accountId);

    @Transactional
    void activateSubscription(String subscriptionId, String accountId);
}
