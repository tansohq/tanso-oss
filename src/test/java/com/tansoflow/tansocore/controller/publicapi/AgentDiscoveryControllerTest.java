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
package com.tansoflow.tansocore.controller.publicapi;

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.AccountSetting;
import com.tansoflow.tansocore.repository.AccountRepository;
import com.tansoflow.tansocore.repository.AccountSettingRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class AgentDiscoveryControllerTest {

    private static final String BASE = "https://billing.example.com";

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountSettingRepository accountSettingRepository;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private AgentDiscoveryController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private void requestFor(String path) {
        when(request.getRequestURL()).thenReturn(new StringBuffer(BASE + path));
        when(request.getRequestURI()).thenReturn(path);
    }

    private Account publishedAccount(String slug, boolean signupEnabled) {
        Account account = new Account();
        account.setId(UUID.randomUUID());
        account.setSlug(slug);
        AccountSetting settings = new AccountSetting();
        settings.setPublicCatalogEnabled(true);
        settings.setAgentSignupEnabled(signupEnabled);
        settings.setAgentSignupDefaultPlanId(signupEnabled ? UUID.randomUUID() : null);
        when(accountSettingRepository.findAccountSettingById(account.getId())).thenReturn(settings);
        return account;
    }

    @Test
    void skillsIndexListsTheRunbookAsOneSkill() {
        requestFor("/.well-known/agent-skills/index.json");

        ResponseEntity<Map<String, Object>> response = controller.skillsIndex(request);

        assertEquals(200, response.getStatusCode().value());
        List<?> skills = (List<?>) response.getBody().get("skills");
        assertEquals(1, skills.size());
        Map<?, ?> skill = (Map<?, ?>) skills.get(0);
        assertEquals("tanso-agent-signup", skill.get("name"));
        assertEquals(BASE + "/agent-signup.md", skill.get("url"));
        assertFalse(((String) skill.get("description")).isBlank());
    }

    @Test
    void manifestLinksRunbookAndSkillsIndex() {
        requestFor("/.well-known/agent.json");
        when(accountRepository.findAll()).thenReturn(List.of());

        Map<String, Object> manifest = controller.agentManifest(request).getBody();

        assertEquals(BASE + "/agent-signup.md", manifest.get("runbook"));
        assertEquals(BASE + "/.well-known/agent-skills/index.json", manifest.get("skills"));
        Map<?, ?> payment = (Map<?, ?>) manifest.get("payment");
        assertEquals("http-402", payment.get("protocol"));
        String description = (String) payment.get("description");
        for (String field : List.of("error.gate", "error.action", "error.url", "error.poll", "error.retry_after")) {
            assertTrue(description.contains(field), "payment description missing " + field);
        }
    }

    @Test
    void llmsTxtLinksRunbookAndDescribesProvisionalFlow() {
        requestFor("/llms.txt");
        Account acme = publishedAccount("acme", true);
        when(accountRepository.findAll()).thenReturn(List.of(acme));

        String body = controller.llmsTxt(request).getBody();

        assertTrue(body.contains("[Signup runbook](" + BASE + "/agent-signup.md)"));
        assertTrue(body.contains("(no fields required)"));
        assertTrue(body.contains("provisional"));
        assertTrue(body.contains("/api/v1/client/customers/{referenceId}/status"));
        assertTrue(body.contains("`gate`"));
        assertTrue(body.contains("There is no claim"));
        assertTrue(body.contains("every signup"));
        assertFalse(body.contains("POST an email"));
        assertFalse(body.contains("claim_required"));
    }

    @Test
    void runbookIsMarkdownAndUsesTheSignupEnabledSlug() {
        requestFor("/agent-signup.md");
        Account catalogOnly = publishedAccount("catalog-only", false);
        Account acme = publishedAccount("acme", true);
        when(accountRepository.findAll()).thenReturn(List.of(catalogOnly, acme));

        ResponseEntity<String> response = controller.runbook(request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("text/markdown;charset=utf-8", response.getHeaders().getContentType().toString().replace(" ", ""));
        String body = response.getBody();
        assertTrue(body.contains("curl -X POST " + BASE + "/public/v1/catalog/acme/signup"));
        assertTrue(body.contains("`catalog-only`: pricing at " + BASE + "/public/v1/catalog/catalog-only/pricing.json, signup not enabled"));
        assertTrue(body.contains("The examples below use `acme`."));
        assertFalse(body.contains("{slug}"));
        assertFalse(body.contains("{base}"));
        assertFalse(body.contains("{catalogs}"));
        assertTrue(body.contains("{featureKey}"));
    }

    @Test
    void runbookCoversTheV2Contract() {
        requestFor("/agent-signup.md");
        Account acme = publishedAccount("acme", true);
        when(accountRepository.findAll()).thenReturn(List.of(acme));

        String body = controller.runbook(request).getBody();

        for (String field : List.of("\"status\": \"provisional\"", "\"expires_at\"", "\"limits\"", "\"spend_mandate\"",
                "\"status_url\"", "\"owner_url\"", "\"nextSteps\"", "\"apiKeyScopes\": [\"read\", \"purchase\"]")) {
            assertTrue(body.contains(field), "runbook missing " + field);
        }
        for (String gate : List.of("`payment`", "`budget`", "`scope`")) {
            assertTrue(body.contains(gate), "runbook missing gate " + gate);
        }
        for (String code : List.of("payment_required", "budget_exceeded", "spend_cap_exceeded", "scope_denied", "`forbidden`")) {
            assertTrue(body.contains(code), "runbook missing code " + code);
        }
        for (String action : List.of("complete_checkout", "nominate_owner", "wait", "raise_spend_cap", "use_own_reference", "request_scope")) {
            assertTrue(body.contains("`" + action + "`"), "runbook missing action " + action);
        }
        assertFalse(body.contains("claim_required"));
        assertFalse(body.contains("claim_account"));
        assertTrue(body.contains("There is no claim gate."));
        assertTrue(body.contains("\"detail\": \"errorId="));
        assertTrue(body.contains("Payment is required: hand url to a human to complete checkout, then poll for the outcome."));
        assertTrue(body.contains("`401 unauthorized`"));
        assertTrue(body.contains("If you get 401 on every endpoint after your"));
        assertTrue(body.contains("expiry date, the account expired. Sign up again."));
        assertTrue(body.contains("\"status\": \"unavailable\""));
        assertTrue(body.contains("`expired`"));
        assertTrue(body.contains("\"check_entitlement_example\""));
        assertTrue(body.contains("\"runbook\": \"" + BASE + "/agent-signup.md\""));
        assertTrue(body.contains("Signup rate limit reached for this catalog; retry after Retry-After seconds"));
        assertTrue(body.contains("Signup rate limit reached for this address; retry after Retry-After seconds"));
        assertTrue(body.contains("## Other errors"));
        assertTrue(body.contains("never omitted"));
        assertTrue(body.contains("03:30 UTC"));
        assertTrue(body.contains("`validation_failed`"));
        assertFalse(body.contains("X-Forwarded-For"));
        assertFalse(body.contains("same customer"));
        assertTrue(body.contains("PUT " + BASE + "/api/v1/client/customers/agent_7f3c9a2e/owner"));
        assertTrue(body.contains("14 days after signup by default"));
        assertTrue(body.contains("`Retry-After` header"));
        assertTrue(body.contains("5 signups per hour by default"));
    }

    @Test
    void mappingsDoNotRestrictAcceptHeader() throws NoSuchMethodException {
        for (String method : List.of("llmsTxt", "agentManifest", "skillsIndex", "runbook")) {
            org.springframework.web.bind.annotation.GetMapping mapping = AgentDiscoveryController.class
                    .getMethod(method, HttpServletRequest.class)
                    .getAnnotation(org.springframework.web.bind.annotation.GetMapping.class);
            assertEquals(0, mapping.produces().length, method + " must not set produces; Accept: application/json would get 406");
        }
    }

    @Test
    void runbookWithoutCatalogsUsesPlaceholderSlug() {
        requestFor("/agent-signup.md");
        when(accountRepository.findAll()).thenReturn(List.of());

        String body = controller.runbook(request).getBody();

        assertTrue(body.contains("No public catalog is published on this instance."));
        assertTrue(body.contains(BASE + "/public/v1/catalog/{slug}/signup"));
    }
}
