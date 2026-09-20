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

import com.tansoflow.tansocore.model.usage.CustomerUsageResponse;

public interface UsageForecastService {

    /**
     * Current-period usage per feature with a linear end-of-period projection,
     * plus per-pool credit balances with an average-burn depletion estimate
     * and the current book price. The agent-facing burndown API.
     */
    CustomerUsageResponse getUsage(String customerReferenceId, String accountId);

    /**
     * What the customer recorded over a window, grouped by subscription, feature and event name. Usage outlives
     * the subscription that carried it, so a period stays auditable after a plan change ends that subscription.
     *
     * @param featureKey      optional filter
     * @param subscriptionId  optional filter
     */
    /**
     * The individual events behind a usage total, newest first. Recorded usage is append-only, so this is what
     * was written at the time; a correction is another event.
     *
     * @param featureKey optional filter
     * @param page       zero-based page number
     * @param limit      page size, capped by the service
     */
    com.tansoflow.tansocore.model.usage.CustomerEventsResponse getRecordedEvents(
            String customerReferenceId, String accountId, java.time.Instant from, java.time.Instant to,
            String featureKey, int page, int limit);

    com.tansoflow.tansocore.model.usage.CustomerUsageHistoryResponse getUsageHistory(
            String customerReferenceId, String accountId, java.time.Instant from, java.time.Instant to,
            String featureKey, String subscriptionId);
}
