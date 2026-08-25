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
package com.tansoflow.tansocore.service.internal.spend.implementation;

import com.tansoflow.tansocore.entity.SpendAttributionRule;
import com.tansoflow.tansocore.entity.SpendBudget;
import com.tansoflow.tansocore.entity.VendorConnection;
import com.tansoflow.tansocore.integration.spend.LiteLlmGateway;
import com.tansoflow.tansocore.model.exception.VendorApiException;
import com.tansoflow.tansocore.model.spend.type.AttributionMatchKind;
import com.tansoflow.tansocore.model.spend.type.BudgetMode;
import com.tansoflow.tansocore.model.spend.type.VendorProvider;
import com.tansoflow.tansocore.repository.SpendAttributionRuleRepository;
import com.tansoflow.tansocore.repository.VendorConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GatewayEnforcementServiceImplTest {

    @Mock private VendorConnectionRepository connectionRepository;
    @Mock private SpendAttributionRuleRepository ruleRepository;
    @Mock private LiteLlmGateway liteLlm;

    private final Instant now = Instant.parse("2026-08-25T14:00:00Z");
    private final UUID accountId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();
    private GatewayEnforcementServiceImpl service;
    private SpendBudget budget;
    private VendorConnection gateway;
    private SpendAttributionRule teamRule;

    @BeforeEach
    void setUp() {
        service = new GatewayEnforcementServiceImpl(connectionRepository, ruleRepository, liteLlm, Clock.fixed(now, ZoneOffset.UTC));
        budget = new SpendBudget();
        budget.setAccountId(accountId);
        budget.setSpendUnitId(unitId);
        budget.setMonthlyCents(new BigDecimal("25000"));
        budget.setMonthlyMode(BudgetMode.BLOCK);
        gateway = new VendorConnection();
        gateway.setProvider(VendorProvider.LITELLM);
        gateway.setAdminKey("sk-master");
        gateway.setScope("https://llm.test");
        teamRule = new SpendAttributionRule();
        teamRule.setAccountId(accountId);
        teamRule.setSpendUnitId(unitId);
        teamRule.setProvider(VendorProvider.LITELLM);
        teamRule.setMatchKind(AttributionMatchKind.WORKSPACE_ID);
        teamRule.setMatchValue("backend");
        lenient().when(connectionRepository.findAllByAccountIdOrderByCreatedAtAsc(accountId)).thenReturn(List.of(gateway));
        lenient().when(ruleRepository.findAllByAccountIdOrderByPriorityAscCreatedAtAsc(accountId)).thenReturn(List.of(teamRule));
    }

    @Test
    void blockBudgetIsPushedAndRecordedOnTheBudget() {
        when(liteLlm.pushMonthlyBudget("sk-master", "https://llm.test", AttributionMatchKind.WORKSPACE_ID, "backend", new BigDecimal("25000")))
                .thenReturn("litellm:team:backend");
        service.apply(budget);
        assertEquals("litellm:team:backend", budget.getEnforcementTarget());
        assertEquals(now, budget.getEnforcedAt());
        assertNull(budget.getEnforcementError());
    }

    @Test
    void alertBudgetClearsTheHardLimitInsteadOfLeavingAStaleOne() {
        budget.setMonthlyMode(BudgetMode.ALERT);
        when(liteLlm.pushMonthlyBudget(eq("sk-master"), eq("https://llm.test"), eq(AttributionMatchKind.WORKSPACE_ID), eq("backend"), isNull()))
                .thenReturn("litellm:team:backend");
        service.apply(budget);
        assertNull(budget.getEnforcementTarget());
        assertNull(budget.getEnforcedAt());
        assertNull(budget.getEnforcementError());
    }

    @Test
    void gatewayDownIsRecordedNotThrown() {
        when(liteLlm.pushMonthlyBudget(any(), any(), any(), any(), any())).thenThrow(new VendorApiException(503, "Could not reach LiteLLM at https://llm.test/team/update: refused"));
        service.apply(budget);
        assertNull(budget.getEnforcementTarget());
        assertTrue(budget.getEnforcementError().contains("WORKSPACE_ID backend: Could not reach"));
    }

    @Test
    void gatewayWithoutARuleOnTheUnitSaysSo() {
        when(ruleRepository.findAllByAccountIdOrderByPriorityAscCreatedAtAsc(accountId)).thenReturn(List.of());
        service.apply(budget);
        verify(liteLlm, never()).pushMonthlyBudget(any(), any(), any(), any(), any());
        assertTrue(budget.getEnforcementError().contains("No LiteLLM rule"));
    }

    @Test
    void noGatewayMeansAdvisoryAndSilent() {
        when(connectionRepository.findAllByAccountIdOrderByCreatedAtAsc(accountId)).thenReturn(List.of());
        service.apply(budget);
        verify(liteLlm, never()).pushMonthlyBudget(any(), any(), any(), any(), any());
        assertNull(budget.getEnforcementError());
        assertNull(budget.getEnforcementTarget());
    }
}
