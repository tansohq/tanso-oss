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

import com.tansoflow.tansocore.entity.AccountSetting;
import com.tansoflow.tansocore.entity.ExternalApiKey;
import com.tansoflow.tansocore.model.spend.SpendSettingsDto;
import com.tansoflow.tansocore.model.spend.request.SpendSettingsRequest;
import com.tansoflow.tansocore.repository.AccountSettingRepository;
import com.tansoflow.tansocore.repository.ExternalApiKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpendSettingsServiceImplTest {

    @Mock private AccountSettingRepository accountSettingRepository;
    @Mock private ExternalApiKeyRepository externalApiKeyRepository;

    private SpendSettingsServiceImpl service;
    private final UUID accountId = UUID.randomUUID();
    private AccountSetting setting;

    @BeforeEach
    void setUp() {
        service = new SpendSettingsServiceImpl(accountSettingRepository, new com.tansoflow.tansocore.util.OutboundUrlPolicy(true, true), externalApiKeyRepository);
        setting = new AccountSetting();
        setting.setId(accountId);
        lenient().when(accountSettingRepository.findAccountSettingById(accountId)).thenReturn(setting);
        lenient().when(accountSettingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void personLevelNeedsTheNoticeFirst() {
        SpendSettingsRequest on = new SpendSettingsRequest();
        on.setPersonLevelEnabled(true);
        assertThrows(IllegalArgumentException.class, () -> service.update(accountId.toString(), on));
        assertFalse(service.personLevelEnabled(accountId.toString()));

        on.setWorkerNotice("  AI spend is attributed to you by name for budgeting. ");
        SpendSettingsDto dto = service.update(accountId.toString(), on);
        assertTrue(dto.isPersonLevelEnabled());
        assertEquals("AI spend is attributed to you by name for budgeting.", dto.getWorkerNotice());
        assertTrue(service.personLevelEnabled(accountId.toString()));
    }

    @Test
    void slackWebhookIsStoredAsASecretAndNeverEchoed() {
        when(externalApiKeyRepository.findExternalApiKeyByKeyTypeAndAccount("SLACK_SPEND_WEBHOOK", accountId)).thenReturn(null);
        SpendSettingsRequest bad = new SpendSettingsRequest();
        bad.setSlackWebhookUrl("https://example.com/hook");
        assertThrows(IllegalArgumentException.class, () -> service.update(accountId.toString(), bad));

        SpendSettingsRequest good = new SpendSettingsRequest();
        good.setSlackWebhookUrl("https://hooks.slack.com/services/T0/B0/xyz");
        ArgumentCaptor<ExternalApiKey> saved = ArgumentCaptor.forClass(ExternalApiKey.class);
        when(externalApiKeyRepository.save(saved.capture())).thenAnswer(inv -> inv.getArgument(0));
        SpendSettingsDto dto = service.update(accountId.toString(), good);
        assertEquals("SLACK_SPEND_WEBHOOK", saved.getValue().getKeyType());
        assertEquals("https://hooks.slack.com/services/T0/B0/xyz", saved.getValue().getKeyValue());
        assertFalse(dto.toString().contains("hooks.slack.com"));
        assertFalse(good.toString().contains("hooks.slack.com"));

        ExternalApiKey row = saved.getValue();
        when(externalApiKeyRepository.findExternalApiKeyByKeyTypeAndAccount("SLACK_SPEND_WEBHOOK", accountId)).thenReturn(row);
        SpendSettingsRequest remove = new SpendSettingsRequest();
        remove.setSlackWebhookUrl("");
        service.update(accountId.toString(), remove);
        verify(externalApiKeyRepository).delete(row);
    }
}
