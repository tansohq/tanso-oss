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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.tansoflow.tansocore.entity.AccountSetting;
import com.tansoflow.tansocore.entity.ExternalApiKey;
import com.tansoflow.tansocore.model.api.external.ExternalApiKeyType;
import com.tansoflow.tansocore.repository.AccountSettingRepository;
import com.tansoflow.tansocore.repository.ExternalApiKeyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fans one event out to every channel the account configured: Slack, a
 * generic webhook (JSON body, HMAC-SHA256 signature when a secret is stored)
 * and email. Each channel fails on its own and is logged; nothing here throws,
 * because an alert that was recorded must not be lost to a dead endpoint.
 */
@Slf4j
@Component
public class SpendNotifier {
    private final SlackNotifier slack;
    private final ExternalApiKeyRepository externalApiKeyRepository;
    private final AccountSettingRepository accountSettingRepository;
    private final Resend resend;
    private final ObjectMapper objectMapper;
    private final RestClient client;
    private final String from;
    private final boolean resendConfigured;

    public enum Outcome { SENT, FAILED, NOT_CONFIGURED }

    /** What happened on each channel; the console shows this instead of a blanket "sent". */
    public record Delivery(Outcome slack, Outcome webhook, Outcome email) {}

    public SpendNotifier(SlackNotifier slack, ExternalApiKeyRepository externalApiKeyRepository, AccountSettingRepository accountSettingRepository,
                         Resend resend, ObjectMapper objectMapper, RestClient.Builder builder,
                         @Value("${app.spend.alert-from:Tanso <alerts@your-domain.com>}") String from,
                         @Value("${app.resend.api-key:}") String resendApiKey) {
        this.resendConfigured = resendApiKey != null && !resendApiKey.isBlank();
        this.slack = slack;
        this.externalApiKeyRepository = externalApiKeyRepository;
        this.accountSettingRepository = accountSettingRepository;
        this.resend = resend;
        this.objectMapper = objectMapper;
        this.client = builder.build();
        this.from = from;
    }

    /**
     * @param event   "spend.alert" or "spend.digest" — the webhook's X-Tanso-Event and payload type
     * @param text    the one-line/plain-text form (Slack, email text)
     * @param html    the email body; null falls back to text
     * @param payload the object serialised into the webhook body under the event's key
     */
    public Delivery notify(UUID accountId, String event, String subject, String text, String html, Object payload) {
        Outcome slackOutcome = slack.configured(accountId)
                ? (slack.post(accountId, "[tanso] " + text) ? Outcome.SENT : Outcome.FAILED)
                : Outcome.NOT_CONFIGURED;
        return new Delivery(slackOutcome, postWebhook(accountId, event, payload), sendEmail(accountId, subject, text, html));
    }

    private Outcome postWebhook(UUID accountId, String event, Object payload) {
        ExternalApiKey hook = externalApiKeyRepository.findExternalApiKeyByKeyTypeAndAccount(ExternalApiKeyType.SPEND_WEBHOOK.name(), accountId);
        if (hook == null || hook.getKeyValue() == null || hook.getKeyValue().isBlank()) {
            return Outcome.NOT_CONFIGURED;
        }
        String body;
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();   // stable key order: receivers may sign the raw body
            envelope.put("type", event);
            envelope.put("accountId", accountId.toString());
            envelope.put(event.equals("spend.alert") ? "alert" : "digest", payload);
            body = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise " + event + " payload", e);
        }
        ExternalApiKey secret = externalApiKeyRepository.findExternalApiKeyByKeyTypeAndAccount(ExternalApiKeyType.SPEND_WEBHOOK_SECRET.name(), accountId);
        try {
            RestClient.RequestBodySpec req = client.post().uri(hook.getKeyValue())
                    .header("Content-Type", "application/json")
                    .header("User-Agent", VendorErrors.USER_AGENT)
                    .header("X-Tanso-Event", event);
            if (secret != null && secret.getKeyValue() != null && !secret.getKeyValue().isBlank()) {
                req = req.header("X-Tanso-Signature", "sha256=" + hmac(secret.getKeyValue(), body));
            }
            req.body(body).retrieve().toBodilessEntity();
            return Outcome.SENT;
        } catch (RuntimeException e) {
            log.warn("Spend webhook post failed for account {}: {}", accountId, e.getMessage());
            return Outcome.FAILED;
        }
    }

    private Outcome sendEmail(UUID accountId, String subject, String text, String html) {
        AccountSetting setting = accountSettingRepository.findAccountSettingById(accountId);
        List<String> to = recipients(setting == null ? null : setting.getSpendAlertEmails());
        if (to.isEmpty()) {
            return Outcome.NOT_CONFIGURED;
        }
        if (!resendConfigured) {
            log.warn("Spend alert email failed for account {}: {} recipient(s) configured but APP_RESEND_API_KEY is not set on the server", accountId, to.size());
            return Outcome.FAILED;
        }
        try {
            resend.emails().send(CreateEmailOptions.builder()
                    .from(from).to(to).subject(subject)
                    .text(text)
                    .html(html != null ? html : "<p>" + HtmlUtils.htmlEscape(text) + "</p>")
                    .build());
            return Outcome.SENT;
        } catch (ResendException | RuntimeException e) {
            log.warn("Spend alert email failed for account {}: {}", accountId, e.getMessage());
            return Outcome.FAILED;
        }
    }

    public static List<String> recipients(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isBlank()) {
            return List.of();
        }
        return Arrays.stream(commaSeparated.split("[,;\\s]+")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    static String hmac(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
