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

import com.stripe.exception.ApiConnectionException;
import com.tansoflow.tansocore.entity.ExternalApiKey;
import com.tansoflow.tansocore.model.api.external.ExternalApiKeyType;
import com.tansoflow.tansocore.repository.ExternalApiKeyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeEventDestinationSyncTest {

    @InjectMocks
    private StripeEventDestinationSync sync;

    @Mock
    private ExternalApiKeyRepository externalApiKeyRepository;
    @Mock
    private StripeService stripeService;

    private ExternalApiKey stripeKey(UUID accountId) {
        ExternalApiKey key = new ExternalApiKey();
        key.setAccount(accountId);
        key.setKeyType(ExternalApiKeyType.STRIPE_API_KEY.name());
        return key;
    }

    @Test
    void oneAccountFailingDoesNotStopTheOthers() throws Exception {
        UUID failing = UUID.randomUUID();
        UUID revoked = UUID.randomUUID();
        UUID healthy = UUID.randomUUID();
        when(externalApiKeyRepository.findAllByKeyType(ExternalApiKeyType.STRIPE_API_KEY.name()))
                .thenReturn(List.of(stripeKey(failing), stripeKey(revoked), stripeKey(healthy)));
        doThrow(new ApiConnectionException("Stripe unreachable")).when(stripeService).syncWebhookEventDestination(failing);
        doThrow(new IllegalStateException("No Stripe API key")).when(stripeService).syncWebhookEventDestination(revoked);

        sync.syncAll();

        verify(stripeService).syncWebhookEventDestination(failing);
        verify(stripeService).syncWebhookEventDestination(revoked);
        verify(stripeService).syncWebhookEventDestination(healthy);
    }
}
