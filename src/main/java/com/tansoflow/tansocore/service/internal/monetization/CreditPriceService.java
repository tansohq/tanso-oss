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

import com.tansoflow.tansocore.model.credit.CreditPriceDto;
import com.tansoflow.tansocore.model.credit.request.PublishCreditPricesRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreditPriceService {

    /** Price resolution result. */
    record ResolvedPrice(BigDecimal pricePerCredit, String currency, UUID priceId) {
    }

    /**
     * Resolves the price of one credit of a denomination at a point in time:
     * the row with the greatest effectiveFrom &lt;= at. Empty when the
     * denomination has never been priced — there is no default price.
     */
    Optional<ResolvedPrice> resolvePrice(UUID accountId, String denomination, Instant at);

    List<CreditPriceDto> getPrices(String accountId);

    List<CreditPriceDto> getHistory(String accountId, String denomination);

    /** Batch price publish: one transaction, one shared effectiveFrom. Idempotent on exact replay. */
    List<CreditPriceDto> publishPrices(PublishCreditPricesRequest request, String accountId, UUID publishedBy);

    /** Deletes a scheduled row. Only rows with effectiveFrom in the future may be deleted. */
    void deleteScheduledPrice(String priceId, String accountId);
}
