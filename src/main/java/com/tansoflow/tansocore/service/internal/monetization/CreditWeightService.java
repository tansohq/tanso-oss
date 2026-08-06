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

import com.tansoflow.tansocore.model.credit.CreditFeatureWeightDto;
import com.tansoflow.tansocore.model.credit.request.PublishCreditWeightsRequest;
import com.tansoflow.tansocore.model.credit.type.WeightMatch;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface CreditWeightService {

    /** Weight resolution result. weightId is null when no row matched (identity default). */
    record ResolvedWeight(BigDecimal weight, UUID weightId, WeightMatch match) {
        public static final ResolvedWeight IDENTITY = new ResolvedWeight(BigDecimal.ONE, null, WeightMatch.NONE);
    }

    /**
     * Resolves the credits-per-unit weight for a feature at a point in time.
     * Most specific tier wins, per-tier fallback: (feature, model) then
     * (feature, NULL) then identity 1.0. Never throws for a missing row.
     */
    ResolvedWeight resolveWeight(UUID accountId, UUID featureId, String model, Instant at);

    List<CreditFeatureWeightDto> getWeights(String accountId);

    List<CreditFeatureWeightDto> getHistory(String accountId, String featureId, String model);

    /** Batch tariff publish: one transaction, one shared effectiveFrom. Idempotent on exact replay. */
    List<CreditFeatureWeightDto> publishWeights(PublishCreditWeightsRequest request, String accountId, UUID publishedBy);

    /** Deletes a scheduled row. Only rows with effectiveFrom in the future may be deleted. */
    void deleteScheduledWeight(String weightId, String accountId);

    /** Observed average cost per usage unit from the event ledger, keyed "featureId|model" (model empty for null). */
    Map<String, BigDecimal> getObservedUnitCosts(String accountId, Instant since);

    /**
     * Which credit denomination each feature burns, from its plan rules.
     * Features burning multiple denominations are omitted (ambiguous — the
     * same reason a weight row is rejected for them).
     */
    Map<String, String> getFeatureDenominations(String accountId);
}
