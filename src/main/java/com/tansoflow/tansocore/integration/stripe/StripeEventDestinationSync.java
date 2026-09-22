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

import com.tansoflow.tansocore.entity.ExternalApiKey;
import com.tansoflow.tansocore.model.api.external.ExternalApiKeyType;
import com.tansoflow.tansocore.repository.ExternalApiKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adds any event Tanso now handles to each connected account's Stripe event destination.
 * Stripe only delivers the events a destination was registered for, so without this an
 * account connected before a handler was added never receives that event. Runs once per
 * boot; a destination that already has every event is left alone.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.stripe-event-destination-sync-enabled", havingValue = "true", matchIfMissing = true)
public class StripeEventDestinationSync {
    private final ExternalApiKeyRepository externalApiKeyRepository;
    private final StripeService stripeService;

    @EventListener(ApplicationReadyEvent.class)
    public void syncAll() {
        List<ExternalApiKey> stripeKeys = externalApiKeyRepository.findAllByKeyType(ExternalApiKeyType.STRIPE_API_KEY.name());
        for (ExternalApiKey stripeKey : stripeKeys) {
            try {
                stripeService.syncWebhookEventDestination(stripeKey.getAccount());
            } catch (Exception e) {
                // One account's revoked key or deleted destination must not stop the others from updating
                log.error("Could not update the Stripe event destination for account {}", stripeKey.getAccount(), e);
            }
        }
    }
}
