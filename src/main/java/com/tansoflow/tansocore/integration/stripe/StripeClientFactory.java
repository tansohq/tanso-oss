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
package com.tansoflow.tansocore.integration.stripe;

import com.stripe.StripeClient;
import com.stripe.net.RequestOptions;
import com.tansoflow.tansocore.model.api.external.ExternalApiKeyType;
import com.tansoflow.tansocore.repository.ExternalApiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class StripeClientFactory {

    private final ExternalApiKeyRepository externalApiKeyRepository;

    private String apiKeyFor(UUID accountId) {
        return externalApiKeyRepository
                .findExternalApiKeyByKeyTypeAndAccount(ExternalApiKeyType.STRIPE_API_KEY.name(), accountId)
                .getKeyValue();
    }

    public StripeClient forAccount(UUID accountId) {
        return new StripeClient(apiKeyFor(accountId));
    }

    public RequestOptions requestOptionsForAccount(UUID accountId) {
        return RequestOptions.builder()
                .setApiKey(apiKeyFor(accountId))
                .build();
    }
}
