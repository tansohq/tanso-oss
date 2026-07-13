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
package com.tansoflow.tansocore.model.data.stripe.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class StripeDiscoveryResponse {
    private List<DiscoveredProduct> products;
    private List<DiscoveredCustomer> customers;
    private List<DiscoveredSubscription> subscriptions;

    @Data
    @Builder
    public static class DiscoveredProduct {
        private String stripeProductId;
        private String name;
        private String description;
        private boolean alreadyMapped;
    }

    @Data
    @Builder
    public static class DiscoveredCustomer {
        private String stripeCustomerId;
        private String name;
        private String email;
        private boolean alreadyMapped;
    }

    @Data
    @Builder
    public static class DiscoveredSubscription {
        private String stripeSubscriptionId;
        private String stripeCustomerId;
        private String stripeProductId;
        private String status;
        private boolean alreadyMapped;
    }
}
