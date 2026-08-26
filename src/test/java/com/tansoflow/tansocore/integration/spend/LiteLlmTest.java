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
import com.tansoflow.tansocore.model.spend.type.VendorUsageSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LiteLlmTest {

    @Test
    void spendLogsAggregatePerDayModelTeamKeyAndActorWithSpendAsCostRows() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LiteLlmUsagePuller puller = new LiteLlmUsagePuller(builder);
        server.expect(requestTo("https://llm.test:4000/spend/logs?start_date=2026-08-20&end_date=2026-08-21&summarize=false"))
                .andExpect(header("Authorization", "Bearer sk-master"))
                .andRespond(withSuccess(new ClassPathResource("spend/litellm-spend-logs.json"), MediaType.APPLICATION_JSON));

        List<UsageBucketRecord> rows = puller.pull("sk-master", "https://llm.test:4000/", LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 22));
        server.verify();

        // r1+r2 fold into one backend bucket; r3 is support; r4 is outside the window
        assertEquals(4, rows.size());
        UsageBucketRecord backend = rows.stream().filter(r -> r.source() == VendorUsageSource.USAGE_API && "backend".equals(r.workspaceId())).findFirst().orElseThrow();
        assertEquals(1600, backend.uncachedInputTokens());
        assertEquals(400, backend.outputTokens());
        assertEquals(2, backend.requests());
        assertEquals("88dc28aa", backend.vendorApiKeyId());
        assertEquals("user-ana", backend.actorId());
        assertEquals("claude-sonnet-4-5", backend.model());
        UsageBucketRecord backendCost = rows.stream().filter(r -> r.source() == VendorUsageSource.COST_API && "backend".equals(r.workspaceId())).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("2.00").compareTo(backendCost.vendorCostCents()));
        assertEquals("LiteLLM spend via anthropic", backendCost.description());
        UsageBucketRecord support = rows.stream().filter(r -> r.source() == VendorUsageSource.USAGE_API && "support".equals(r.workspaceId())).findFirst().orElseThrow();
        assertEquals("cust-42", support.actorId(), "the end-user the caller passed wins over the key owner");
        UsageBucketRecord supportCost = rows.stream().filter(r -> r.source() == VendorUsageSource.COST_API && "support".equals(r.workspaceId())).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("50.00").compareTo(supportCost.vendorCostCents()));
        assertTrue(rows.stream().noneMatch(r -> r.bucketStart().toString().startsWith("2026-08-22")));
    }

    @Test
    void scopeMustBeTheProxyUrl() {
        LiteLlmUsagePuller puller = new LiteLlmUsagePuller(RestClient.builder());
        assertThrows(IllegalArgumentException.class, () -> puller.probe("sk-master"));
        assertThrows(IllegalArgumentException.class, () -> puller.probe("sk-master", "llm.internal"));
        assertTrue(puller.requiresScope());
        assertEquals("https://x", LiteLlmUsagePuller.base("https://x/"));
    }

    @Test
    void probeHitsLivelinessAndSurfacesAuthFailures() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LiteLlmUsagePuller puller = new LiteLlmUsagePuller(builder);
        server.expect(requestTo("https://llm.test/health/liveliness"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body("{\"error\":{\"message\":\"Authentication Error, invalid master key\"}}"));
        VendorApiException e = assertThrows(VendorApiException.class, () -> puller.probe("bad", "https://llm.test"));
        assertTrue(e.getMessage().contains("401"));
        assertTrue(e.getMessage().contains("invalid master key"));
        assertNull(null);
    }

    @Test
    void gatewayPushesMonthlyBudgetToTheObjectTheRuleNames() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LiteLlmGateway gateway = new LiteLlmGateway(builder);
        server.expect(requestTo("https://llm.test/team/update"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-master"))
                .andExpect(jsonPath("$.team_id").value("backend"))
                .andExpect(jsonPath("$.max_budget").value(250.0))
                .andExpect(jsonPath("$.budget_duration").value("1mo"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://llm.test/key/update"))
                .andExpect(jsonPath("$.key").value("sk-abc"))
                .andExpect(jsonPath("$.max_budget").doesNotExist())
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertEquals("litellm:team:backend", gateway.pushMonthlyBudget("sk-master", "https://llm.test/", AttributionMatchKind.WORKSPACE_ID, "backend", new BigDecimal("25000")));
        assertEquals("litellm:key:sk-abc", gateway.pushMonthlyBudget("sk-master", "https://llm.test", AttributionMatchKind.API_KEY_ID, "sk-abc", null));
        server.verify();
    }

    @Test
    void gatewayErrorsBecomeVendorErrors() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LiteLlmGateway gateway = new LiteLlmGateway(builder);
        server.expect(requestTo("https://llm.test/user/update"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body("{\"detail\":\"User not found\"}"));
        VendorApiException e = assertThrows(VendorApiException.class,
                () -> gateway.pushMonthlyBudget("sk-master", "https://llm.test", AttributionMatchKind.ACTOR, "ghost", new BigDecimal("100")));
        assertTrue(e.getMessage().contains("400"));
    }
}
