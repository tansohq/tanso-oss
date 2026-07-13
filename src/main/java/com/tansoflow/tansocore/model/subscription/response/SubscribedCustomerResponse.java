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
package com.tansoflow.tansocore.model.subscription.response;

import com.tansoflow.tansocore.model.billing.InvoiceDto;
import com.tansoflow.tansocore.model.subscription.SubscriptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "Response after a customer has successfully subscribed")
public class SubscribedCustomerResponse {
    @Schema(description = "Details of the created subscription")
    private SubscriptionDto subscription;

    @Schema(description = "Details of the first invoice generated")
    private InvoiceDto invoice;

    @Schema(description = "Additional metadata related to the subscription process")
    private Map<String, Object> metadata;

    @Schema(description = "Stripe Checkout URL for payment (IN_ADVANCE plans in STRIPE_INTEGRATION mode)")
    private String checkoutUrl;
}
