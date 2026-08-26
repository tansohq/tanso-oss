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
package com.tansoflow.tansocore.integration.spend;

import com.tansoflow.tansocore.entity.ExternalApiKey;
import com.tansoflow.tansocore.model.api.external.ExternalApiKeyType;
import com.tansoflow.tansocore.repository.ExternalApiKeyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

/** Posts one line to the account's Slack incoming webhook, if one is stored. Failures are logged, never thrown — an alert is recorded either way. */
@Slf4j
@Component
public class SlackNotifier {
    private final ExternalApiKeyRepository externalApiKeyRepository;
    private final RestClient client;

    public SlackNotifier(ExternalApiKeyRepository externalApiKeyRepository, RestClient.Builder builder) {
        this.externalApiKeyRepository = externalApiKeyRepository;
        this.client = builder.build();
    }

    public boolean configured(UUID accountId) {
        ExternalApiKey webhook = externalApiKeyRepository
                .findExternalApiKeyByKeyTypeAndAccount(ExternalApiKeyType.SLACK_SPEND_WEBHOOK.name(), accountId);
        return webhook != null && webhook.getKeyValue() != null && !webhook.getKeyValue().isBlank();
    }

    public boolean post(UUID accountId, String text) {
        ExternalApiKey webhook = externalApiKeyRepository
                .findExternalApiKeyByKeyTypeAndAccount(ExternalApiKeyType.SLACK_SPEND_WEBHOOK.name(), accountId);
        if (webhook == null || webhook.getKeyValue() == null || webhook.getKeyValue().isBlank()) {
            return false;
        }
        try {
            client.post().uri(webhook.getKeyValue())
                    .body(Map.of("text", text))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RuntimeException e) {
            log.warn("Slack webhook post failed for account {}: {}", accountId, e.getMessage());
            return false;
        }
    }
}
