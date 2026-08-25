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
import com.tansoflow.tansocore.model.spend.type.OutcomeKind;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OutcomePullersTest {

    @Test
    void githubKeepsMergedPrsInsideTheWindowAndStopsPaging() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubOutcomePuller puller = new GitHubOutcomePuller(builder, "https://gh.test");
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://gh.test/repos/acme/app/pulls?state=closed")))
                .andExpect(header("Authorization", "Bearer ghp_x"))
                .andExpect(header("X-GitHub-Api-Version", "2022-11-28"))
                .andRespond(withSuccess(new ClassPathResource("spend/github-pulls-page1.json"), MediaType.APPLICATION_JSON));

        List<OutcomeRecord> out = puller.pull("ghp_x", "acme/app", Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"));
        server.verify(); // one page: the last PR's updated_at is before the window, so no page 2

        assertEquals(1, out.size());
        assertEquals(OutcomeKind.PR_MERGED, out.get(0).kind());
        assertEquals("acme/app#42", out.get(0).externalId());
        assertEquals("alice", out.get(0).actorLogin());
        assertNull(out.get(0).actorEmail());
        assertEquals(Instant.parse("2026-08-20T10:00:00Z"), out.get(0).occurredAt());
    }

    @Test
    void githubScopeMustBeOwnerRepo() {
        assertThrows(IllegalArgumentException.class, () -> GitHubOutcomePuller.repos("acme"));
        assertThrows(IllegalArgumentException.class, () -> GitHubOutcomePuller.repos(" , "));
        assertEquals(List.of("acme/app", "acme/site"), GitHubOutcomePuller.repos("acme/app, acme/site"));
    }

    @Test
    void githubBadTokenIsAVendorError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubOutcomePuller puller = new GitHubOutcomePuller(builder, "https://gh.test");
        server.expect(requestTo("https://gh.test/repos/acme/app"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body("{\"message\":\"Bad credentials\"}"));
        VendorApiException e = assertThrows(VendorApiException.class, () -> puller.probe("bad", "acme/app"));
        assertTrue(e.isAuthFailure());
        assertTrue(e.getMessage().contains("Bad credentials"));
    }

    @Test
    void linearFiltersByTeamAndReadsAssigneeEmail() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LinearOutcomePuller puller = new LinearOutcomePuller(builder, "https://linear.test/graphql");
        server.expect(requestTo("https://linear.test/graphql"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "lin_api_x"))
                .andRespond(withSuccess(new ClassPathResource("spend/linear-issues.json"), MediaType.APPLICATION_JSON));

        List<OutcomeRecord> out = puller.pull("lin_api_x", "be", Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"));
        server.verify();

        assertEquals(1, out.size());
        assertEquals(OutcomeKind.ISSUE_DONE, out.get(0).kind());
        assertEquals("BE-12", out.get(0).externalId());
        assertEquals("alice@acme.test", out.get(0).actorEmail());
        assertEquals(List.of(), LinearOutcomePuller.teams("*"));
    }
}
