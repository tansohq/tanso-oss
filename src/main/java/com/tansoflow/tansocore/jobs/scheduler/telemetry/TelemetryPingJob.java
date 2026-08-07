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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends one anonymous ping per day so we know how many self-hosted instances
 * exist and roughly how they're used. This class is the ENTIRE telemetry
 * surface — nothing else in the codebase phones home. The exact payload is
 * documented in the README (Telemetry section). Opt out with
 * TANSO_TELEMETRY_ENABLED=false.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TelemetryPingJob {

    private final AppProperty appProperty;
    private final WebClient webClient;
    private final ObjectProvider<BuildProperties> buildProperties;
    private final InstanceTelemetryRepository instanceTelemetryRepository;
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final EventRepository eventRepository;

    @Value("${app.mcp.enabled:false}")
    private boolean mcpEnabled;

    @Scheduled(initialDelayString = "PT5M", fixedDelayString = "PT24H")
    public void sendDailyPing() {
        if (!appProperty.getTelemetry().isEnabled()) {
            return;
        }
        Map<String, Object> payload = buildPayload();
        try {
            webClient.post()
                    .uri(appProperty.getTelemetry().getEndpoint())
                    .bodyValue(payload)
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(10));
        } catch (Exception e) {
            log.debug("Telemetry ping failed (this is harmless): {}", e.getMessage());
        }
    }

    Map<String, Object> buildPayload() {
        BuildProperties build = buildProperties.getIfAvailable();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("instance_id", instanceId().toString());
        payload.put("version", build != null ? build.getVersion() : "dev");
        payload.put("accounts", accountRepository.count());
        payload.put("customers", customerRepository.count());
        payload.put("plans", planRepository.count());
        payload.put("subscriptions", subscriptionRepository.count());
        payload.put("events_last_24h", bucket(eventRepository.countByOccurredAtAfter(Instant.now().minus(Duration.ofHours(24)))));
        payload.put("mcp_enabled", mcpEnabled);
        payload.put("dogfooding_enabled", appProperty.isDogfoodingEnabled());
        return payload;
    }

    private java.util.UUID instanceId() {
        return instanceTelemetryRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> instanceTelemetryRepository.save(new InstanceTelemetry()))
                .getInstanceId();
    }

    static String bucket(long count) {
        if (count == 0) return "0";
        if (count <= 100) return "1-100";
        if (count <= 1_000) return "101-1k";
        if (count <= 10_000) return "1k-10k";
        if (count <= 100_000) return "10k-100k";
        return "100k+";
    }
}
