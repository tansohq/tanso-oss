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

import com.fasterxml.jackson.databind.ObjectMapper;
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

class CursorAndCopilotPullersTest {

    @Test
    void cursorAggregatesEventsPerDayPersonModelAndCarriesWhatWasCharged() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CursorUsagePuller puller = new CursorUsagePuller(builder, "https://cursor.test");
        server.expect(requestTo("https://cursor.test/teams/filtered-usage-events"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", CursorUsagePuller.basic("key_crash")))
                .andExpect(jsonPath("$.startDate").value(1787184000000L))
                .andExpect(jsonPath("$.pageSize").value(1000))
                .andRespond(withSuccess(new ClassPathResource("spend/cursor-events.json"), MediaType.APPLICATION_JSON));

        List<UsageBucketRecord> rows = puller.pull("key_crash", null, LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 22));
        server.verify();

        assertEquals(4, rows.size()); // two (day, person, model) groups × usage + cost
        UsageBucketRecord alice = rows.get(0);
        assertEquals(VendorUsageSource.USAGE_API, alice.source());
        assertEquals("alice@acme.test", alice.actorId());
        assertEquals("claude-4-sonnet", alice.model());
        assertEquals(1500, alice.uncachedInputTokens());
        assertEquals(300, alice.cacheReadTokens());
        assertEquals(100, alice.cacheCreationTokens());
        assertEquals(300, alice.outputTokens());
        assertEquals(2L, alice.requests());
        UsageBucketRecord aliceCost = rows.get(1);
        assertEquals(VendorUsageSource.COST_API, aliceCost.source());
        assertEquals(0, new BigDecimal("17").compareTo(aliceCost.vendorCostCents()));
        assertEquals("alice@acme.test", aliceCost.actorId());
        assertEquals(30, CursorUsagePuller.class.cast(puller).maxWindowDays());
    }

    @Test
    void cursorDailyActivityBecomesActorMetricsAndSkipsIdleMembers() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CursorUsagePuller puller = new CursorUsagePuller(builder, "https://cursor.test");
        server.expect(requestTo("https://cursor.test/teams/daily-usage-data"))
                .andRespond(withSuccess(new ClassPathResource("spend/cursor-daily.json"), MediaType.APPLICATION_JSON));

        List<ActorMetricRecord> m = puller.pullActorMetrics("key_crash", null, LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 21));

        assertEquals(1, m.size());
        ActorMetricRecord a = m.get(0);
        assertEquals("alice@acme.test", a.actorId());
        assertEquals(LocalDate.of(2026, 8, 20), a.day());
        assertEquals(300, a.linesAdded());
        assertEquals(40, a.linesRemoved());
        assertEquals(400, a.linesSuggested());
        assertEquals(25, a.accepted());   // applies only, same basis as rejected
        assertEquals(5, a.rejected());
        assertEquals(30, a.requests());   // agent + chat + composer + cmdk
        assertEquals("claude-4-sonnet", a.tool());
        assertNull(a.commits());
    }

    @Test
    void copilotFollowsDownloadLinksAndReadsNdjson() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CopilotUsagePuller puller = new CopilotUsagePuller(builder, "https://gh.test");
        // one day → report → link, fetched once: the actor-metrics pass reads what the usage pass fetched
        server.expect(requestTo("https://gh.test/orgs/acme/copilot/metrics/reports/users-1-day?day=2026-08-20"))
                .andExpect(header("Authorization", "Bearer ghp_x"))
                .andRespond(withSuccess("{\"download_links\":[\"https://files.test/report.ndjson\"],\"report_day\":\"2026-08-20\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://files.test/report.ndjson"))
                .andRespond(withSuccess(new ClassPathResource("spend/copilot-users.ndjson"), MediaType.TEXT_PLAIN));

        VendorUsagePuller.PullResult both = puller.pullAll("ghp_x", "acme", LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 21));
        List<UsageBucketRecord> rows = both.usage();
        List<ActorMetricRecord> metrics = both.actors();
        server.verify();

        assertEquals(1, rows.size()); // bob has no CLI/app tokens
        assertEquals("alice", rows.get(0).actorId());
        assertEquals(3800, rows.get(0).uncachedInputTokens());
        assertEquals(5000, rows.get(0).outputTokens());
        assertEquals(2, metrics.size());
        ActorMetricRecord alice = metrics.get(0);
        assertEquals(9, alice.requests());
        assertEquals(4, alice.accepted());
        assertEquals(80, alice.linesAdded());
        assertEquals(120, alice.linesSuggested());
        assertEquals(2, alice.sessions());
        assertEquals(0, new BigDecimal("12.5").compareTo(alice.creditsUsed()));
        assertEquals("agent", alice.tool());
        assertTrue(puller.requiresScope());
    }

    @Test
    void copilotNoActivityDayIs204AndScopeIsValidated() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CopilotUsagePuller puller = new CopilotUsagePuller(builder, "https://gh.test");
        server.expect(requestTo("https://gh.test/orgs/acme/copilot/metrics/reports/users-1-day?day=2026-08-21"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
        assertEquals(0, puller.pull("ghp_x", "acme", LocalDate.of(2026, 8, 21), LocalDate.of(2026, 8, 22)).size());
        assertThrows(IllegalArgumentException.class, () -> CopilotUsagePuller.org("acme/app"));
        assertThrows(IllegalArgumentException.class, () -> puller.pull("ghp_x", LocalDate.of(2026, 8, 21), LocalDate.of(2026, 8, 22)));
    }

    @Test
    void copilotReportParserAcceptsArrayOrLines() throws Exception {
        assertEquals(2, CopilotUsagePuller.parseReport("{\"a\":1}\n{\"a\":2}\n").size());
        assertEquals(2, CopilotUsagePuller.parseReport("[{\"a\":1},{\"a\":2}]").size());
        assertEquals(0, CopilotUsagePuller.parseReport("  ").size());
        new ObjectMapper(); // keep the import honest
    }

    @Test
    void githubPullRequestAiToolFromLabelsTrailersAndBots() throws Exception {
        ObjectMapper m = new ObjectMapper();
        assertEquals("claude-code", GitHubOutcomePuller.aiTool(m.readTree("{\"labels\":[{\"name\":\"claude-code-assisted\"}],\"body\":\"\",\"user\":{\"login\":\"alice\",\"type\":\"User\"}}")));
        assertEquals("cursor", GitHubOutcomePuller.aiTool(m.readTree("{\"labels\":[],\"body\":\"Made-with: Cursor\",\"user\":{\"login\":\"alice\",\"type\":\"User\"}}")));
        assertEquals("copilot", GitHubOutcomePuller.aiTool(m.readTree("{\"labels\":[],\"body\":null,\"user\":{\"login\":\"copilot-swe-agent[bot]\",\"type\":\"Bot\"}}")));
        assertNull(GitHubOutcomePuller.aiTool(m.readTree("{\"labels\":[{\"name\":\"bug\"}],\"body\":\"fixes a thing\",\"user\":{\"login\":\"alice\",\"type\":\"User\"}}")));
    }
}
