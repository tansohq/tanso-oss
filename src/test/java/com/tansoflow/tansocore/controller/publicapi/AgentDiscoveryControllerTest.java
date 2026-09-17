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
        assertFalse(body.contains("POST an email"));
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
        for (String gate : List.of("`payment`", "`budget`", "`claim`", "`scope`")) {
            assertTrue(body.contains(gate), "runbook missing gate " + gate);
        }
        for (String code : List.of("payment_required", "budget_exceeded", "claim_required", "scope_denied", "spend_cap_exceeded")) {
            assertTrue(body.contains(code), "runbook missing code " + code);
        }
        for (String action : List.of("complete_checkout", "wait", "claim_account", "request_scope", "raise_spend_cap")) {
            assertTrue(body.contains("`" + action + "`"), "runbook missing action " + action);
        }
        assertTrue(body.contains("PUT " + BASE + "/api/v1/client/customers/agent_7f3c9a2e/owner"));
        assertTrue(body.contains("14 days after signup by default"));
        assertTrue(body.contains("`Retry-After` header"));
        assertTrue(body.contains("5 signups per hour by default"));
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
