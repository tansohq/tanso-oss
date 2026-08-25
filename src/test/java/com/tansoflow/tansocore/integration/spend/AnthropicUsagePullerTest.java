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
import com.tansoflow.tansocore.model.spend.type.VendorUsageSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AnthropicUsagePullerTest {

    private MockRestServiceServer server;
    private AnthropicUsagePuller puller;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        puller = new AnthropicUsagePuller(builder, "https://vendor.test");
    }

    @Test
    void pullsUsageCostAndClaudeCodeAndFollowsPagination() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://vendor.test/v1/organizations/usage_report/messages")))
                .andExpect(header("x-api-key", "sk-ant-admin01-test"))
                .andExpect(header("anthropic-version", AnthropicUsagePuller.VERSION_HEADER))
                .andExpect(queryParam("bucket_width", "1d"))
                .andExpect(queryParam("starting_at", "2026-08-20T00:00:00Z"))
                .andExpect(queryParam("ending_at", "2026-08-22T00:00:00Z"))
                .andRespond(withSuccess(new ClassPathResource("spend/anthropic-usage.json"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://vendor.test/v1/organizations/cost_report")))
                .andRespond(withSuccess(new ClassPathResource("spend/anthropic-cost-page1.json"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://vendor.test/v1/organizations/cost_report")))
                .andExpect(queryParam("page", "page_two"))
                .andRespond(withSuccess(new ClassPathResource("spend/anthropic-cost-page2.json"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://vendor.test/v1/organizations/usage_report/claude_code")))
                .andExpect(queryParam("starting_at", "2026-08-20"))
                .andRespond(withSuccess(new ClassPathResource("spend/anthropic-claude-code.json"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://vendor.test/v1/organizations/usage_report/claude_code")))
                .andExpect(queryParam("starting_at", "2026-08-21"))
                .andRespond(withSuccess(new ClassPathResource("spend/anthropic-claude-code-empty.json"), MediaType.APPLICATION_JSON));

        List<UsageBucketRecord> rows = puller.pull("sk-ant-admin01-test", LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 22));
        server.verify();

        assertEquals(5, rows.size());
        UsageBucketRecord usage = rows.get(0);
        assertEquals(VendorUsageSource.USAGE_API, usage.source());
        assertEquals("claude-sonnet-4-5", usage.model());
        assertEquals("wrkspc_01", usage.workspaceId());
        assertEquals("apikey_01", usage.vendorApiKeyId());
        assertEquals(1500, usage.uncachedInputTokens());
        assertEquals(200, usage.cacheReadTokens());
        assertEquals(1500, usage.cacheCreationTokens()); // 5m + 1h
        assertEquals(500, usage.outputTokens());
        assertNull(usage.vendorCostCents());
        assertNull(rows.get(1).workspaceId()); // default workspace stays null

        UsageBucketRecord cost = rows.get(2);
        assertEquals(VendorUsageSource.COST_API, cost.source());
        assertEquals(new BigDecimal("123.78912"), cost.vendorCostCents());
        assertEquals("USD", cost.currency());
        assertEquals("Claude Sonnet 4.5 Usage - Input Tokens", cost.description());
        assertEquals("claude-sonnet-4-5", cost.model());
        assertEquals(new BigDecimal("50"), rows.get(3).vendorCostCents());

        UsageBucketRecord cc = rows.get(4);
        assertEquals(VendorUsageSource.CLAUDE_CODE_API, cc.source());
        assertEquals("dev@acme.test", cc.actorId());
        assertEquals("claude-opus-5", cc.model());
        assertEquals(100000, cc.uncachedInputTokens());
        assertEquals(10000, cc.cacheReadTokens());
        assertEquals(5000, cc.cacheCreationTokens());
        assertEquals(new BigDecimal("141"), cc.vendorCostCents());
        assertEquals(5L, cc.requests());
    }

    @Test
    void rejectedKeySurfacesAsAuthFailure() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://vendor.test/v1/organizations/usage_report/messages")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}"));
        VendorApiException e = assertThrows(VendorApiException.class, () -> puller.probe("bad"));
        assertTrue(e.isAuthFailure());
        assertEquals(401, e.getStatus());
        assertTrue(e.getMessage().contains("invalid x-api-key"));
    }
}
