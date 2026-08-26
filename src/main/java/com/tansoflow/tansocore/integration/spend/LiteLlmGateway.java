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

import com.tansoflow.tansocore.model.exception.VendorApiException;
import com.tansoflow.tansocore.model.spend.type.AttributionMatchKind;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pushes a hard monthly budget onto the LiteLLM object a rule names: a team
 * (WORKSPACE_ID), a key (API_KEY_ID) or an internal user (ACTOR). LiteLLM
 * rejects requests once its own spend passes max_budget and resets it on
 * budget_duration, so this is where "Block" stops being a label.
 */
@Slf4j
@Component
public class LiteLlmGateway {
    private final RestClient client;

    public LiteLlmGateway(RestClient.Builder builder) {
        this.client = builder.build();
    }

    /** @return the target it was pushed to, e.g. "litellm:team:backend" */
    public String pushMonthlyBudget(String masterKey, String proxyUrl, AttributionMatchKind kind, String id, BigDecimal monthlyCents) {
        String base = LiteLlmUsagePuller.base(proxyUrl);
        Map<String, Object> body = new LinkedHashMap<>();
        String path;
        String label;
        switch (kind) {
            case WORKSPACE_ID -> { path = "/team/update"; body.put("team_id", id); label = "team"; }
            case API_KEY_ID -> { path = "/key/update"; body.put("key", id); label = "key"; }
            case ACTOR -> { path = "/user/update"; body.put("user_id", id); label = "user"; }
            default -> throw new IllegalArgumentException("Unsupported rule kind " + kind);
        }
        body.put("max_budget", monthlyCents == null ? null : monthlyCents.movePointLeft(2).setScale(2, RoundingMode.HALF_UP).doubleValue());
        body.put("budget_duration", "1mo");
        post(masterKey, base + path, body);
        return "litellm:" + label + ":" + id;
    }

    private void post(String masterKey, String url, Map<String, Object> body) {
        try {
            client.post().uri(URI.create(url))
                    .header("Authorization", "Bearer " + masterKey)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", VendorErrors.USER_AGENT)
                    .body(body).retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            String m = VendorErrors.jsonMessage(e.getResponseBodyAsString());
            throw new VendorApiException(e.getStatusCode().value(),
                    "LiteLLM returned " + e.getStatusCode().value() + (m == null ? "" : ": " + m));
        } catch (ResourceAccessException e) {
            throw new VendorApiException("Could not reach LiteLLM at " + url + ": " + e.getMessage(), e);
        }
    }
}
