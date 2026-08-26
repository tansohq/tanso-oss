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
package com.tansoflow.tansocore.service.internal.spend.implementation;

import com.tansoflow.tansocore.util.OutboundUrlPolicy;
import com.tansoflow.tansocore.entity.AccountSetting;
import com.tansoflow.tansocore.entity.ExternalApiKey;
import com.tansoflow.tansocore.model.api.external.ExternalApiKeyEntityName;
import com.tansoflow.tansocore.integration.spend.SpendNotifier;
import com.tansoflow.tansocore.model.api.external.ExternalApiKeyType;
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.model.spend.SpendSettingsDto;
import com.tansoflow.tansocore.model.spend.request.SpendSettingsRequest;
import com.tansoflow.tansocore.repository.AccountSettingRepository;
import com.tansoflow.tansocore.repository.ExternalApiKeyRepository;
import com.tansoflow.tansocore.service.internal.spend.SpendSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.modules.build.enabled", havingValue = "true", matchIfMissing = true)
public class SpendSettingsServiceImpl implements SpendSettingsService {
    private final AccountSettingRepository accountSettingRepository;
    private final OutboundUrlPolicy outboundUrlPolicy;
    private final ExternalApiKeyRepository externalApiKeyRepository;

    @Override
    @Transactional(readOnly = true)
    public SpendSettingsDto get(String accountId) {
        return toDto(require(accountId));
    }

    @Override
    @Transactional
    public SpendSettingsDto update(String accountId, SpendSettingsRequest request) {
        AccountSetting setting = require(accountId);
        if (request.getWorkerNotice() != null) {
            setting.setSpendWorkerNotice(request.getWorkerNotice().isBlank() ? null : request.getWorkerNotice().trim());
        }
        if (request.getPersonLevelEnabled() != null) {
            if (request.getPersonLevelEnabled() && setting.getSpendWorkerNotice() == null) {
                throw new IllegalArgumentException(
                        "Write the worker notice before enabling person-level attribution — staff should know spend is attributed to them by name");
            }
            setting.setSpendPersonLevelEnabled(request.getPersonLevelEnabled());
        }
        if (request.getAlertEmails() != null) {
            String emails = String.join(", ", SpendNotifier.recipients(request.getAlertEmails()));
            for (String e : SpendNotifier.recipients(emails)) {
                if (!e.contains("@")) {
                    throw new IllegalArgumentException("Not an email address: " + e);
                }
            }
            setting.setSpendAlertEmails(emails.isEmpty() ? null : emails);
        }
        if (request.getDigestEnabled() != null) {
            setting.setSpendDigestEnabled(request.getDigestEnabled());
        }
        accountSettingRepository.save(setting);
        UUID id = UUID.fromString(accountId);
        if (request.getWebhookUrl() != null) {
            String url = request.getWebhookUrl().trim();
            if (!url.isEmpty()) {
                url = outboundUrlPolicy.check(url, "The webhook URL");
            }
            storeSecret(id, ExternalApiKeyType.SPEND_WEBHOOK, ExternalApiKeyEntityName.WEBHOOK, url);
        }
        if (request.getWebhookSecret() != null) {
            storeSecret(id, ExternalApiKeyType.SPEND_WEBHOOK_SECRET, ExternalApiKeyEntityName.WEBHOOK, request.getWebhookSecret().trim());
        }
        if (request.getSlackWebhookUrl() != null) {
            ExternalApiKey row = externalApiKeyRepository
                    .findExternalApiKeyByKeyTypeAndAccount(ExternalApiKeyType.SLACK_SPEND_WEBHOOK.name(), id);
            String url = request.getSlackWebhookUrl().trim();
            if (url.isEmpty()) {
                if (row != null) {
                    externalApiKeyRepository.delete(row);
                }
            } else {
                if (!url.startsWith("https://hooks.slack.com/")) {
                    throw new IllegalArgumentException("That is not a Slack incoming webhook URL (expected https://hooks.slack.com/…)");
                }
                if (row == null) {
                    row = new ExternalApiKey();
                    row.setAccount(id);
                    row.setExternalApiEntityName(ExternalApiKeyEntityName.SLACK.name());
                    row.setKeyType(ExternalApiKeyType.SLACK_SPEND_WEBHOOK.name());
                    row.setIsActive(true);
                }
                row.setKeyValue(url);
                externalApiKeyRepository.save(row);
            }
        }
        return toDto(setting);
    }

    private void storeSecret(UUID account, ExternalApiKeyType type, ExternalApiKeyEntityName entity, String value) {
        ExternalApiKey row = externalApiKeyRepository.findExternalApiKeyByKeyTypeAndAccount(type.name(), account);
        if (value.isEmpty()) {
            if (row != null) {
                externalApiKeyRepository.delete(row);
            }
            return;
        }
        if (row == null) {
            row = new ExternalApiKey();
            row.setAccount(account);
            row.setExternalApiEntityName(entity.name());
            row.setKeyType(type.name());
            row.setIsActive(true);
        }
        row.setKeyValue(value);
        externalApiKeyRepository.save(row);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean personLevelEnabled(String accountId) {
        AccountSetting setting = accountSettingRepository.findAccountSettingById(UUID.fromString(accountId));
        return setting != null && setting.isSpendPersonLevelEnabled();
    }

    private AccountSetting require(String accountId) {
        AccountSetting setting = accountSettingRepository.findAccountSettingById(UUID.fromString(accountId));
        if (setting == null) {
            throw new ResourceNotFoundException("Account settings not found: " + accountId);
        }
        return setting;
    }

    private SpendSettingsDto toDto(AccountSetting setting) {
        boolean slack = externalApiKeyRepository.findExternalApiKeyByKeyTypeAndAccount(
                ExternalApiKeyType.SLACK_SPEND_WEBHOOK.name(), setting.getId()) != null;
        boolean webhook = externalApiKeyRepository.findExternalApiKeyByKeyTypeAndAccount(
                ExternalApiKeyType.SPEND_WEBHOOK.name(), setting.getId()) != null;
        boolean signed = externalApiKeyRepository.findExternalApiKeyByKeyTypeAndAccount(
                ExternalApiKeyType.SPEND_WEBHOOK_SECRET.name(), setting.getId()) != null;
        return SpendSettingsDto.builder()
                .webhookConfigured(webhook).webhookSigned(signed)
                .alertEmails(setting.getSpendAlertEmails()).digestEnabled(setting.isSpendDigestEnabled())
                .personLevelEnabled(setting.isSpendPersonLevelEnabled())
                .workerNotice(setting.getSpendWorkerNotice())
                .slackConfigured(slack)
                .build();
    }
}
