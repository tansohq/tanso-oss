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
package com.tansoflow.tansocore.integration.stripe.implementation;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.v2.core.EventDestination;
import com.stripe.param.WebhookEndpointCreateParams;
import com.stripe.param.v2.core.EventDestinationCreateParams;
import com.stripe.param.v2.core.EventDestinationListParams;
import com.stripe.param.v2.core.EventDestinationUpdateParams;
import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.AccountSetting;
import com.tansoflow.tansocore.entity.ExternalApiKey;
import com.tansoflow.tansocore.integration.stripe.StripeClientFactory;
import com.tansoflow.tansocore.integration.stripe.StripeService;
import com.tansoflow.tansocore.model.api.external.ExternalApiKeyEntityName;
import com.tansoflow.tansocore.model.api.external.ExternalApiKeyType;
import com.tansoflow.tansocore.model.api.external.StripeMode;
import com.tansoflow.tansocore.model.data.stripe.response.StripeApiKeysResponse;
import com.tansoflow.tansocore.property.AppProperty;
import com.tansoflow.tansocore.repository.AccountSettingRepository;
import com.tansoflow.tansocore.repository.ExternalApiKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class StripeServiceImpl implements StripeService {
    private final AppProperty appProperty;
    private final ExternalApiKeyRepository externalApiKeyRepository;
    private final AccountSettingRepository accountSettingRepository;


    private static final String TANSO_WEBHOOK_NAME = "Tanso Webhook StripeController Endpoint";
    private static final String TANSO_WEBHOOK_DESCRIPTION = "In use by Tanso to subscribe to events. Do not delete";
    private final StripeClientFactory stripeClientFactory;

    @Override
    public void registerStripeApiKey(String clientStripeApiKey, Account account) {
        ExternalApiKey externalApiKey = new ExternalApiKey();
        externalApiKey.setExternalApiEntityName(ExternalApiKeyEntityName.STRIPE.name());
        externalApiKey.setKeyType(ExternalApiKeyType.STRIPE_API_KEY.name());
        externalApiKey.setKeyValue(clientStripeApiKey);
        externalApiKey.setAccount(account.getId());
        externalApiKey.setIsActive(true);
        externalApiKeyRepository.save(externalApiKey);
    }

    @Override
    public StripeApiKeysResponse getStripeKeys(Account account) {
        ExternalApiKey externalApiKey = externalApiKeyRepository.findExternalApiKeyByKeyTypeAndAccount(ExternalApiKeyType.STRIPE_API_KEY.name(), account.getId());
        ExternalApiKey webhookSigningKey = externalApiKeyRepository.findExternalApiKeyByKeyTypeAndAccount(ExternalApiKeyType.WEBHOOK_SECRET_SIGNING.name(), account.getId());
        StripeApiKeysResponse stripeApiKeysResponse = new StripeApiKeysResponse();

        if (externalApiKey != null) {
            String apiKey = externalApiKey.getKeyValue();
            String maskedApiKey = apiKey.substring(0, 4) + "************" + apiKey.substring(apiKey.length() - 4);
            stripeApiKeysResponse.setStripeApiKey(maskedApiKey);
        }

        if (webhookSigningKey != null) {
            String webhookSigningSecret = webhookSigningKey.getKeyValue();
            String maskedWebhookSigningSecret = webhookSigningSecret.substring(0, 4) + "************" + webhookSigningSecret.substring(webhookSigningSecret.length() - 4);
            stripeApiKeysResponse.setWebhookSecret(maskedWebhookSigningSecret);
        }

        return stripeApiKeysResponse;
    }

    @Override
    public void deleteStripeKeys(Account account) {
        ExternalApiKey externalApiKey = externalApiKeyRepository.findExternalApiKeyByKeyTypeAndAccount(ExternalApiKeyType.STRIPE_API_KEY.name(), account.getId());
        if (externalApiKey != null) {
            externalApiKeyRepository.delete(externalApiKey);
        }

        ExternalApiKey webhookSigningKey = externalApiKeyRepository.findExternalApiKeyByKeyTypeAndAccount(ExternalApiKeyType.WEBHOOK_SECRET_SIGNING.name(), account.getId());
        if (webhookSigningKey != null) {
            externalApiKeyRepository.delete(webhookSigningKey);
        }

        // Without a key, any stripeMode other than NONE leaves the account
        // believing Stripe is still wired up (isStripeIntegration()/isEnabled()
        // checks stay true), so webhook and sync code paths keep trying to call
        // an account that just had its credentials removed. Reset it. The stored event destination
        // belongs to the Stripe account being disconnected, so it goes too.
        AccountSetting accountSetting = accountSettingRepository.findAccountSettingById(account.getId());
        if (accountSetting != null
                && (accountSetting.getStripeMode() != StripeMode.NONE || accountSetting.getStripeEventDestinationId() != null)) {
            accountSetting.setStripeMode(StripeMode.NONE);
            accountSetting.setStripeEventDestinationId(null);
            accountSettingRepository.save(accountSetting);
        }
    }

    @Override
    public void createNewWebhookEndpoint(Account account) throws StripeException {
        try {
            String clientStripeApiKey = externalApiKeyRepository.findExternalApiKeyByKeyTypeAndAccount(ExternalApiKeyType.STRIPE_API_KEY.name(), account.getId()).getKeyValue();
            if (clientStripeApiKey == null) {
                throw new IllegalStateException("Stripe API key not found for account: " + account.getId());
            }
            StripeClient client = new StripeClient(clientStripeApiKey);

            EventDestinationCreateParams params = getEventDestinationParams(account.getId().toString());

            EventDestination eventDestination = client.v2().core().eventDestinations().create(params);

            // retrieve signing secret from response after creating a new webhook endpoint
            String signingSecret = eventDestination.getWebhookEndpoint().getSigningSecret();

            ExternalApiKey externalApiKey = new ExternalApiKey();
            externalApiKey.setExternalApiEntityName(ExternalApiKeyEntityName.STRIPE.name());
            externalApiKey.setKeyType(ExternalApiKeyType.WEBHOOK_SECRET_SIGNING.name());

            externalApiKey.setKeyValue(signingSecret);
            externalApiKey.setAccount(account.getId());
            externalApiKey.setIsActive(true);

            externalApiKeyRepository.save(externalApiKey);

            AccountSetting accountSetting = accountSettingRepository.findAccountSettingById(account.getId());
            accountSetting.setStripeEventDestinationId(eventDestination.getId());
            accountSettingRepository.save(accountSetting);

            if (!eventDestination.getStatus().equals("enabled")) {
                throw new IllegalStateException("Webhook endpoint is not enabled");
            }
        } catch (StripeException e) {
            log.error("Stripe occurred while creating new webhook endpoint:", e);
            throw e;
        } catch (Exception e) {
            log.error("Error occurred while creating new webhook endpoint:", e);
            throw e;
        }
    }

    private String getTansoWebhookEndpoint() {
        return appProperty.getStripeWebhookEndpoint();
    }

    EventDestinationCreateParams getEventDestinationParams(String accountId) {
        String endpointUrl = getTansoWebhookEndpoint() + "/" + accountId;

        return EventDestinationCreateParams.builder()
                .setName(TANSO_WEBHOOK_NAME)
                .setDescription(TANSO_WEBHOOK_DESCRIPTION)
                .addAllEnabledEvent(tansoEnabledEvents())
                .setType(EventDestinationCreateParams.Type.WEBHOOK_ENDPOINT)
                .addInclude(EventDestinationCreateParams.Include.WEBHOOK_ENDPOINT__SIGNING_SECRET)
                .setWebhookEndpoint(EventDestinationCreateParams.WebhookEndpoint.builder()
                        .setUrl(endpointUrl).build())
                // TODO: check thin vs snapshot response load size
                .setEventPayload(EventDestinationCreateParams.EventPayload.SNAPSHOT)
                .addInclude(EventDestinationCreateParams.Include.WEBHOOK_ENDPOINT__URL)
                .build();
    }

    // Every event StripeWebhookImpl handles. New connections register these; syncWebhookEventDestination
    // adds any that an existing destination is missing.
    static List<String> tansoEnabledEvents() {
        return List.of(
                WebhookEndpointCreateParams.EnabledEvent.CHECKOUT__SESSION__ASYNC_PAYMENT_SUCCEEDED.getValue(),
                WebhookEndpointCreateParams.EnabledEvent.CHECKOUT__SESSION__COMPLETED.getValue(),
                WebhookEndpointCreateParams.EnabledEvent.CHECKOUT__SESSION__EXPIRED.getValue(),

                // Card setup and off-session top-ups
                WebhookEndpointCreateParams.EnabledEvent.SETUP_INTENT__SUCCEEDED.getValue(),
                WebhookEndpointCreateParams.EnabledEvent.PAYMENT_INTENT__SUCCEEDED.getValue(),

                // Customer <> Subscription events
                WebhookEndpointCreateParams.EnabledEvent.CUSTOMER__SUBSCRIPTION__CREATED.getValue(),
                WebhookEndpointCreateParams.EnabledEvent.CUSTOMER__SUBSCRIPTION__UPDATED.getValue(),
                WebhookEndpointCreateParams.EnabledEvent.CUSTOMER__SUBSCRIPTION__DELETED.getValue(),
                WebhookEndpointCreateParams.EnabledEvent.CUSTOMER__SUBSCRIPTION__PAUSED.getValue(),
                WebhookEndpointCreateParams.EnabledEvent.CUSTOMER__SUBSCRIPTION__RESUMED.getValue(),
                WebhookEndpointCreateParams.EnabledEvent.CUSTOMER__SUBSCRIPTION__PENDING_UPDATE_EXPIRED.getValue(),

                // customer events
                WebhookEndpointCreateParams.EnabledEvent.CUSTOMER__CREATED.getValue(),
                WebhookEndpointCreateParams.EnabledEvent.CUSTOMER__DELETED.getValue(),
                WebhookEndpointCreateParams.EnabledEvent.CUSTOMER__UPDATED.getValue(),

                // Invoice events
                WebhookEndpointCreateParams.EnabledEvent.INVOICE__PAID.getValue(),
                WebhookEndpointCreateParams.EnabledEvent.INVOICE__PAYMENT_SUCCEEDED.getValue(),
                WebhookEndpointCreateParams.EnabledEvent.INVOICE_PAYMENT__PAID.getValue(),
                WebhookEndpointCreateParams.EnabledEvent.INVOICE__PAYMENT_FAILED.getValue(),
                WebhookEndpointCreateParams.EnabledEvent.INVOICE__FINALIZED.getValue(),
                WebhookEndpointCreateParams.EnabledEvent.INVOICE__CREATED.getValue(),
                WebhookEndpointCreateParams.EnabledEvent.INVOICE__UPDATED.getValue(),
                WebhookEndpointCreateParams.EnabledEvent.INVOICE__DELETED.getValue());
    }

    @Override
    public void syncWebhookEventDestination(UUID accountId) throws StripeException {
        StripeClient client = stripeClientFactory.forAccount(accountId);
        AccountSetting setting = accountSettingRepository.findAccountSettingById(accountId);

        EventDestination destination;
        if (setting.getStripeEventDestinationId() != null) {
            destination = client.v2().core().eventDestinations().retrieve(setting.getStripeEventDestinationId());
        } else {
            // Connected before the id was stored: find the destination Tanso created by its name and URL
            destination = findTansoEventDestination(client, accountId);
            if (destination == null) {
                log.warn("Account {} has a Stripe key but no Tanso event destination in Stripe; Stripe delivers no events to Tanso until the webhook is registered", accountId);
                return;
            }
            setting.setStripeEventDestinationId(destination.getId());
            accountSettingRepository.save(setting);
        }

        List<String> current = destination.getEnabledEvents() == null ? List.of() : destination.getEnabledEvents();
        List<String> missing = tansoEnabledEvents().stream().filter(event -> !current.contains(event)).toList();
        if (missing.isEmpty()) {
            return;
        }

        // Keep any events the operator added by hand; enabled_events replaces the whole list
        List<String> enabledEvents = new ArrayList<>(current);
        enabledEvents.addAll(missing);
        client.v2().core().eventDestinations().update(destination.getId(),
                EventDestinationUpdateParams.builder().addAllEnabledEvent(enabledEvents).build());
        log.info("Added {} to Stripe event destination {} for account {}", missing, destination.getId(), accountId);
    }

    private EventDestination findTansoEventDestination(StripeClient client, UUID accountId) throws StripeException {
        EventDestinationListParams params = EventDestinationListParams.builder()
                .addInclude(EventDestinationListParams.Include.WEBHOOK_ENDPOINT__URL)
                .build();
        for (EventDestination destination : client.v2().core().eventDestinations().list(params).autoPagingIterable()) {
            boolean namedByTanso = TANSO_WEBHOOK_NAME.equals(destination.getName())
                    || TANSO_WEBHOOK_DESCRIPTION.equals(destination.getDescription());
            boolean forThisAccount = destination.getWebhookEndpoint() != null
                    && destination.getWebhookEndpoint().getUrl() != null
                    && destination.getWebhookEndpoint().getUrl().endsWith("/" + accountId);
            if (namedByTanso && forThisAccount) {
                return destination;
            }
        }
        return null;
    }

}