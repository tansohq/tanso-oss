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

import com.tansoflow.tansocore.model.spend.type.VendorUsageSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiUsagePullerTest {

    @Test
    void modelComesFromTheLineItemHead() {
        assertEquals("gpt-4o-mini", OpenAiUsagePuller.modelFromLineItem("gpt-4o-mini, input"));
        assertEquals("gpt-4o", OpenAiUsagePuller.modelFromLineItem("gpt-4o"));
        assertEquals(null, OpenAiUsagePuller.modelFromLineItem(null));
        assertEquals(null, OpenAiUsagePuller.modelFromLineItem(" , input"));
    }

    @Test
    void mapsCachedTokensAndDollarsToCents() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiUsagePuller puller = new OpenAiUsagePuller(builder, "https://vendor.test");

        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://vendor.test/v1/organization/usage/completions")))
                .andExpect(header("Authorization", "Bearer sk-admin-test"))
                .andExpect(queryParam("start_time", "1787184000"))
                .andExpect(queryParam("end_time", "1787270400"))
                .andExpect(queryParam("group_by", "project_id", "user_id", "api_key_id", "model"))
                .andExpect(header("User-Agent", "tanso-oss (https://github.com/tansohq/tanso-oss)"))
                .andRespond(withSuccess(new ClassPathResource("spend/openai-usage.json"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://vendor.test/v1/organization/costs")))
                .andRespond(withSuccess(new ClassPathResource("spend/openai-costs.json"), MediaType.APPLICATION_JSON));

        List<UsageBucketRecord> rows = puller.pull("sk-admin-test", LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 21));
        server.verify();

        assertEquals(2, rows.size());
        UsageBucketRecord usage = rows.get(0);
        assertEquals(VendorUsageSource.USAGE_API, usage.source());
        assertEquals(Instant.ofEpochSecond(1787184000L), usage.bucketStart());
        assertEquals("gpt-4o-mini", usage.model());
        assertEquals("proj_1", usage.workspaceId());
        assertEquals("user_1", usage.actorId());
        assertEquals("key_1", usage.vendorApiKeyId());
        assertEquals(600, usage.uncachedInputTokens());
        assertEquals(400, usage.cacheReadTokens());
        assertEquals(300, usage.outputTokens());
        assertEquals(12L, usage.requests());

        UsageBucketRecord cost = rows.get(1);
        assertEquals(VendorUsageSource.COST_API, cost.source());
        assertEquals(0, new BigDecimal("4.32").compareTo(cost.vendorCostCents()));
        assertEquals("USD", cost.currency());
        assertEquals("gpt-4o-mini, input", cost.description());
        assertEquals("gpt-4o-mini", cost.model());
        assertEquals("proj_1", cost.workspaceId());
    }
}
