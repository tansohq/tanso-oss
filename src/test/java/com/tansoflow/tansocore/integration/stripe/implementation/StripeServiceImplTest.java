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
import com.stripe.model.v2.StripeCollection;
import com.stripe.model.v2.core.EventDestination;
import com.stripe.param.v2.core.EventDestinationCreateParams;
import com.stripe.param.v2.core.EventDestinationListParams;
import com.stripe.param.v2.core.EventDestinationUpdateParams;
import com.stripe.service.v2.core.EventDestinationService;
import com.tansoflow.tansocore.entity.AccountSetting;
import com.tansoflow.tansocore.integration.stripe.StripeClientFactory;
import com.tansoflow.tansocore.property.AppProperty;
import com.tansoflow.tansocore.repository.AccountSettingRepository;
import com.tansoflow.tansocore.repository.ExternalApiKeyRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StripeServiceImplTest {

    /**
     * Stripe only delivers the events the endpoint was registered for, and `stripe listen` forwards everything,
     * so a handled-but-unregistered event passes local testing and silently never arrives in production.
     */
    @Test
    void registersEveryEventTheWebhookHandles() throws Exception {
        AppProperty appProperty = new AppProperty();
        appProperty.setStripeWebhookEndpoint("https://tanso.example.com/api/v1/stripe/webhook");
        StripeServiceImpl service = new StripeServiceImpl(appProperty, null, null, null);

        EventDestinationCreateParams params = service.getEventDestinationParams("account-id");

        String handler = Files.readString(Path.of(
                "src/main/java/com/tansoflow/tansocore/integration/stripe/implementation/StripeWebhookImpl.java"));
        Matcher handled = Pattern.compile("case \"([a-z_]+\\.[a-z_.]+)\"").matcher(handler);
        List<String> handledEvents = handled.results().map(m -> m.group(1)).toList();

        assertThat(handledEvents).isNotEmpty();
        assertThat(params.getEnabledEvents()).containsAll(handledEvents);
    }

    private final UUID accountId = UUID.randomUUID();
    private final StripeClientFactory stripeClientFactory = mock(StripeClientFactory.class);
    private final AccountSettingRepository accountSettingRepository = mock(AccountSettingRepository.class);
    private final EventDestinationService eventDestinations = mock(EventDestinationService.class);
    private final StripeServiceImpl service = new StripeServiceImpl(
            new AppProperty(), mock(ExternalApiKeyRepository.class), accountSettingRepository, stripeClientFactory);

    private AccountSetting settingWithDestinationId(String destinationId) throws Exception {
        StripeClient client = mock(StripeClient.class, RETURNS_DEEP_STUBS);
        when(client.v2().core().eventDestinations()).thenReturn(eventDestinations);
        when(stripeClientFactory.forAccount(accountId)).thenReturn(client);

        AccountSetting setting = new AccountSetting();
        setting.setId(accountId);
        setting.setStripeEventDestinationId(destinationId);
        when(accountSettingRepository.findAccountSettingById(accountId)).thenReturn(setting);
        return setting;
    }

    private EventDestination destination(String id, String name, String url, List<String> enabledEvents) {
        EventDestination destination = new EventDestination();
        destination.setId(id);
        destination.setName(name);
        EventDestination.WebhookEndpoint webhookEndpoint = new EventDestination.WebhookEndpoint();
        webhookEndpoint.setUrl(url);
        destination.setWebhookEndpoint(webhookEndpoint);
        destination.setEnabledEvents(enabledEvents);
        return destination;
    }

    @SuppressWarnings("unchecked")
    private void listReturns(EventDestination... destinations) throws Exception {
        StripeCollection<EventDestination> page = mock(StripeCollection.class);
        when(page.autoPagingIterable()).thenReturn(List.of(destinations));
        when(eventDestinations.list(any(EventDestinationListParams.class))).thenReturn(page);
    }

    @Test
    void syncAddsMissingEventsToTheStoredDestinationAndKeepsExtras() throws Exception {
        settingWithDestinationId("ed_stored");
        List<String> registeredBeforeCheckoutEvents = new ArrayList<>(StripeServiceImpl.tansoEnabledEvents());
        registeredBeforeCheckoutEvents.remove("checkout.session.completed");
        registeredBeforeCheckoutEvents.remove("setup_intent.succeeded");
        registeredBeforeCheckoutEvents.add("charge.refunded");
        when(eventDestinations.retrieve("ed_stored")).thenReturn(destination("ed_stored", "Tanso Webhook StripeController Endpoint",
                "https://tanso.example.com/api/v1/stripe/webhook/" + accountId, registeredBeforeCheckoutEvents));

        service.syncWebhookEventDestination(accountId);

        ArgumentCaptor<EventDestinationUpdateParams> params = ArgumentCaptor.forClass(EventDestinationUpdateParams.class);
        verify(eventDestinations).update(eq("ed_stored"), params.capture());
        assertThat(params.getValue().getEnabledEvents())
                .containsAll(StripeServiceImpl.tansoEnabledEvents())
                .contains("charge.refunded")
                .doesNotHaveDuplicates();
        verify(eventDestinations, never()).list(any(EventDestinationListParams.class));
    }

    @Test
    void syncLeavesAnUpToDateDestinationAlone() throws Exception {
        settingWithDestinationId("ed_stored");
        when(eventDestinations.retrieve("ed_stored")).thenReturn(destination("ed_stored", "Tanso Webhook StripeController Endpoint",
                "https://tanso.example.com/api/v1/stripe/webhook/" + accountId, StripeServiceImpl.tansoEnabledEvents()));

        service.syncWebhookEventDestination(accountId);

        verify(eventDestinations, never()).update(anyString(), any(EventDestinationUpdateParams.class));
        verify(accountSettingRepository, never()).save(any());
    }

    @Test
    void syncFindsAnOlderAccountsDestinationByNameAndUrlAndStoresItsId() throws Exception {
        AccountSetting setting = settingWithDestinationId(null);
        EventDestination otherAccounts = destination("ed_other", "Tanso Webhook StripeController Endpoint",
                "https://tanso.example.com/api/v1/stripe/webhook/" + UUID.randomUUID(), List.of("invoice.paid"));
        EventDestination someoneElses = destination("ed_foreign", "Zapier",
                "https://hooks.example.com/" + accountId, List.of("invoice.paid"));
        EventDestination ours = destination("ed_ours", "Tanso Webhook StripeController Endpoint",
                "https://tanso.example.com/api/v1/stripe/webhook/" + accountId, List.of("invoice.paid"));
        listReturns(otherAccounts, someoneElses, ours);

        service.syncWebhookEventDestination(accountId);

        assertThat(setting.getStripeEventDestinationId()).isEqualTo("ed_ours");
        verify(accountSettingRepository).save(setting);
        ArgumentCaptor<EventDestinationUpdateParams> params = ArgumentCaptor.forClass(EventDestinationUpdateParams.class);
        verify(eventDestinations).update(eq("ed_ours"), params.capture());
        assertThat(params.getValue().getEnabledEvents()).containsAll(StripeServiceImpl.tansoEnabledEvents());
        verify(eventDestinations, never()).update(eq("ed_other"), any(EventDestinationUpdateParams.class));
        verify(eventDestinations, never()).update(eq("ed_foreign"), any(EventDestinationUpdateParams.class));
    }

    @Test
    void syncDoesNothingWhenNoTansoDestinationExists() throws Exception {
        AccountSetting setting = settingWithDestinationId(null);
        listReturns(destination("ed_foreign", "Zapier", "https://hooks.example.com/" + accountId, List.of("invoice.paid")));

        service.syncWebhookEventDestination(accountId);

        assertThat(setting.getStripeEventDestinationId()).isNull();
        verify(accountSettingRepository, never()).save(any());
        verify(eventDestinations, never()).update(anyString(), any(EventDestinationUpdateParams.class));
    }
}
