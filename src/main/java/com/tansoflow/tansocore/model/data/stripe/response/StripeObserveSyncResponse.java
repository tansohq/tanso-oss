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

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response from Observe-mode Stripe sync")
public class StripeObserveSyncResponse {

    @Schema(description = "Number of customers synced from Stripe")
    @Builder.Default
    private int customersSynced = 0;

    @Schema(description = "Number of plans synced from Stripe products/prices")
    @Builder.Default
    private int plansSynced = 0;

    @Schema(description = "Number of subscriptions synced from Stripe")
    @Builder.Default
    private int subscriptionsSynced = 0;

    @Schema(description = "Number of items that failed to sync")
    @Builder.Default
    private int errors = 0;

    @Schema(description = "Warnings about skipped items")
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
}
