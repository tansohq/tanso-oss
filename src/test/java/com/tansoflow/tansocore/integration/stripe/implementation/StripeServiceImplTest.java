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

import com.stripe.param.v2.core.EventDestinationCreateParams;
import com.tansoflow.tansocore.property.AppProperty;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

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
}
