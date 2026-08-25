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
package com.tansoflow.tansocore.model.apikey;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomerApiKeyDto {
    private String id;
    private String customerReferenceId;
    @Schema(description = "Plaintext key. Returned exactly once, at creation or rotation; never retrievable again.")
    private String apiKey;
    private String keyHint;
    private List<String> scopes;
    private Boolean active;
    private Instant expiresAt;
    private Instant createdAt;

    // Budget summary, so a list of keys shows which ones are capped without a
    // round trip per key. Null period means no budget; a null limit on either
    // axis means that axis is unlimited.
    @Schema(description = "Window this key's budget is measured over, or null when it has no budget")
    private com.tansoflow.tansocore.model.apikey.type.BudgetPeriod budgetPeriod;

    @Schema(description = "Credits this key may consume per window. Null means unlimited.")
    private java.math.BigDecimal budgetCredits;

    @Schema(description = "Money this key may spend per window. Null means unlimited.")
    private java.math.BigDecimal budgetAmount;
}
