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
package com.tansoflow.tansocore.jobs.scheduler.telemetry;

import com.tansoflow.tansocore.entity.InstanceTelemetry;
import com.tansoflow.tansocore.property.AppProperty;
import com.tansoflow.tansocore.repository.AccountRepository;
import com.tansoflow.tansocore.repository.CustomerRepository;
import com.tansoflow.tansocore.repository.EventRepository;
import com.tansoflow.tansocore.repository.InstanceTelemetryRepository;
import com.tansoflow.tansocore.repository.PlanRepository;
import com.tansoflow.tansocore.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelemetryPingJobTest {

    @Mock private AppProperty appProperty;
    @Mock private WebClient webClient;
    @Mock private ObjectProvider<BuildProperties> buildProperties;
    @Mock private InstanceTelemetryRepository instanceTelemetryRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private PlanRepository planRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private EventRepository eventRepository;

    @InjectMocks private TelemetryPingJob job;

    @Test
    void bucketsEventCounts() {
        assertThat(TelemetryPingJob.bucket(0)).isEqualTo("0");
        assertThat(TelemetryPingJob.bucket(1)).isEqualTo("1-100");
        assertThat(TelemetryPingJob.bucket(100)).isEqualTo("1-100");
        assertThat(TelemetryPingJob.bucket(101)).isEqualTo("101-1k");
        assertThat(TelemetryPingJob.bucket(5_000)).isEqualTo("1k-10k");
        assertThat(TelemetryPingJob.bucket(50_000)).isEqualTo("10k-100k");
        assertThat(TelemetryPingJob.bucket(200_000)).isEqualTo("100k+");
    }

    @Test
    void payloadContainsOnlyAnonymousFields() {
        InstanceTelemetry instance = new InstanceTelemetry();
        instance.setInstanceId(UUID.randomUUID());
        when(instanceTelemetryRepository.findAll()).thenReturn(List.of(instance));
        when(buildProperties.getIfAvailable()).thenReturn(null);
        when(accountRepository.count()).thenReturn(2L);
        when(customerRepository.count()).thenReturn(10L);
        when(planRepository.count()).thenReturn(3L);
        when(subscriptionRepository.count()).thenReturn(8L);
        when(eventRepository.countByOccurredAtAfter(any(Instant.class))).thenReturn(250L);
        when(appProperty.isDogfoodingEnabled()).thenReturn(false);

        Map<String, Object> payload = job.buildPayload();

        assertThat(payload.keySet()).containsExactly(
                "instance_id", "version", "accounts", "customers", "plans",
                "subscriptions", "events_last_24h", "mcp_enabled", "dogfooding_enabled");
        assertThat(payload.get("instance_id")).isEqualTo(instance.getInstanceId().toString());
        assertThat(payload.get("version")).isEqualTo("dev");
        assertThat(payload.get("events_last_24h")).isEqualTo("101-1k");
    }

    @Test
    void createsInstanceIdOnFirstRun() {
        InstanceTelemetry created = new InstanceTelemetry();
        created.setInstanceId(UUID.randomUUID());
        when(instanceTelemetryRepository.findAll()).thenReturn(List.of());
        when(instanceTelemetryRepository.save(any(InstanceTelemetry.class))).thenReturn(created);
        when(buildProperties.getIfAvailable()).thenReturn(null);

        Map<String, Object> payload = job.buildPayload();

        assertThat(payload.get("instance_id")).isEqualTo(created.getInstanceId().toString());
    }
}
