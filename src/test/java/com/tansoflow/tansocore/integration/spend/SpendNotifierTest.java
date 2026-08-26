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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resend.Resend;
import com.tansoflow.tansocore.entity.AccountSetting;
import com.tansoflow.tansocore.entity.ExternalApiKey;
import com.tansoflow.tansocore.model.api.external.ExternalApiKeyType;
import com.tansoflow.tansocore.repository.AccountSettingRepository;
import com.tansoflow.tansocore.repository.ExternalApiKeyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class SpendNotifierTest {
    @Mock private SlackNotifier slack;
    @Mock private ExternalApiKeyRepository keys;
    @Mock private AccountSettingRepository settings;
    @Mock private Resend resend;

    private final UUID account = UUID.randomUUID();

    private SpendNotifier notifier(RestClient.Builder builder) {
        return new SpendNotifier(slack, keys, settings, resend, new ObjectMapper(), builder, "Tanso <alerts@test>", "re_test");
    }

    private ExternalApiKey key(String value) {
        ExternalApiKey k = new ExternalApiKey();
        k.setKeyValue(value);
        return k;
    }

    @Test
    void webhookGetsJsonWithEventHeaderAndHmacSignature() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        when(keys.findExternalApiKeyByKeyTypeAndAccount(ExternalApiKeyType.SPEND_WEBHOOK.name(), account)).thenReturn(key("https://hooks.test/tanso"));
        when(keys.findExternalApiKeyByKeyTypeAndAccount(ExternalApiKeyType.SPEND_WEBHOOK_SECRET.name(), account)).thenReturn(key("s3cret"));
        when(settings.findAccountSettingById(account)).thenReturn(null);
        String body = "{\"type\":\"spend.alert\",\"accountId\":\"" + account + "\",\"alert\":{\"kind\":\"BREACH\"}}";
        server.expect(requestTo("https://hooks.test/tanso"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Tanso-Event", "spend.alert"))
                .andExpect(header("Content-Type", "application/json"))
                .andExpect(jsonPath("$.alert.kind").value("BREACH"))
                .andExpect(jsonPath("$.accountId").value(account.toString()))
                .andExpect(header("X-Tanso-Signature", "sha256=" + SpendNotifier.hmac("s3cret", body)))
                .andRespond(withSuccess());

        when(slack.configured(account)).thenReturn(true);
        when(slack.post(account, "[tanso] text")).thenReturn(true);
        SpendNotifier.Delivery d = notifier(builder).notify(account, "spend.alert", "subject", "text", null, Map.of("kind", "BREACH"));
        server.verify();
        verify(resend, never()).emails();
        assertEquals(SpendNotifier.Outcome.SENT, d.slack());
        assertEquals(SpendNotifier.Outcome.SENT, d.webhook());
        assertEquals(SpendNotifier.Outcome.NOT_CONFIGURED, d.email());
    }

    @Test
    void unsignedWebhookAndDeadEndpointNeverThrow() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        when(keys.findExternalApiKeyByKeyTypeAndAccount(ExternalApiKeyType.SPEND_WEBHOOK.name(), account)).thenReturn(key("https://hooks.test/dead"));
        when(keys.findExternalApiKeyByKeyTypeAndAccount(ExternalApiKeyType.SPEND_WEBHOOK_SECRET.name(), account)).thenReturn(null);
        when(settings.findAccountSettingById(account)).thenReturn(null);
        server.expect(requestTo("https://hooks.test/dead"))
                .andExpect(headerDoesNotExist("X-Tanso-Signature"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_GATEWAY));
        SpendNotifier.Delivery d = notifier(builder).notify(account, "spend.digest", "s", "t", null, Map.of());
        server.verify();
        assertEquals(SpendNotifier.Outcome.FAILED, d.webhook());
        assertEquals(SpendNotifier.Outcome.NOT_CONFIGURED, d.slack());
    }

    @Test
    void recipientsWithoutAResendKeyIsAFailureNotASilentSkip() {
        when(keys.findExternalApiKeyByKeyTypeAndAccount(ExternalApiKeyType.SPEND_WEBHOOK.name(), account)).thenReturn(null);
        AccountSetting s = new AccountSetting();
        s.setSpendAlertEmails("cto@x.io");
        when(settings.findAccountSettingById(account)).thenReturn(s);
        SpendNotifier n = new SpendNotifier(slack, keys, settings, resend, new ObjectMapper(), RestClient.builder(), "Tanso <alerts@test>", "");
        SpendNotifier.Delivery d = n.notify(account, "spend.alert", "s", "t", null, Map.of());
        assertEquals(SpendNotifier.Outcome.FAILED, d.email());
        verify(resend, never()).emails();
    }

    @Test
    void nothingConfiguredMeansNothingSent() {
        when(keys.findExternalApiKeyByKeyTypeAndAccount(ExternalApiKeyType.SPEND_WEBHOOK.name(), account)).thenReturn(null);
        AccountSetting s = new AccountSetting();
        s.setSpendAlertEmails("  ");
        when(settings.findAccountSettingById(account)).thenReturn(s);
        SpendNotifier.Delivery d = notifier(RestClient.builder()).notify(account, "spend.alert", "s", "t", null, Map.of());
        verify(resend, never()).emails();
        verify(slack, never()).post(eq(account), eq("[tanso] t"));
        assertEquals(SpendNotifier.Outcome.NOT_CONFIGURED, d.slack());
        assertEquals(SpendNotifier.Outcome.NOT_CONFIGURED, d.email());
    }

    @Test
    void recipientsAndHmacAreWhatTheReceiverExpects() {
        assertEquals(List.of("a@x.io", "b@x.io", "c@x.io"), SpendNotifier.recipients("a@x.io, b@x.io;c@x.io"));
        assertEquals(List.of(), SpendNotifier.recipients(null));
        // RFC 4231-style known answer
        assertEquals("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
                SpendNotifier.hmac("key", "The quick brown fox jumps over the lazy dog"));
    }
}
