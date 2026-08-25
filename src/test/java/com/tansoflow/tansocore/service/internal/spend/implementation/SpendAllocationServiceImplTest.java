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
import com.tansoflow.tansocore.entity.SpendUnit;
import com.tansoflow.tansocore.entity.VendorUsageBucket;
import com.tansoflow.tansocore.model.spend.SpendAllocationReportDto;
import com.tansoflow.tansocore.model.spend.type.AttributionMatchKind;
import com.tansoflow.tansocore.model.spend.type.SpendUnitType;
import com.tansoflow.tansocore.model.spend.type.VendorProvider;
import com.tansoflow.tansocore.model.spend.type.VendorUsageSource;
import com.tansoflow.tansocore.repository.SpendAttributionRuleRepository;
import com.tansoflow.tansocore.repository.SpendUnitRepository;
import com.tansoflow.tansocore.repository.VendorUsageBucketRepository;
import com.tansoflow.tansocore.service.internal.spend.SpendSettingsService;
import com.tansoflow.tansocore.util.VendorCostEstimator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpendAllocationServiceImplTest {

    @Mock private VendorUsageBucketRepository bucketRepository;
    @Mock private SpendUnitRepository unitRepository;
    @Mock private SpendAttributionRuleRepository ruleRepository;
    @Mock private SpendSettingsService settingsService;
    @Mock private VendorCostEstimator estimator;

    private SpendAllocationServiceImpl service;
    private final UUID accountId = UUID.randomUUID();
    private final Instant day = Instant.parse("2026-08-20T00:00:00Z");
    private SpendUnit team;
    private SpendUnit alice;
    private SpendUnit project;

    @BeforeEach
    void setUp() {
        service = new SpendAllocationServiceImpl(bucketRepository, unitRepository, ruleRepository, settingsService, estimator);
        // 1 cent per 100 tokens of any class
        lenient().when(estimator.estimate(anyString(), anyLong(), anyLong(), anyLong(), anyLong())).thenAnswer(inv ->
                new VendorCostEstimator.Estimate(BigDecimal.valueOf((inv.<Long>getArgument(1) + inv.<Long>getArgument(2)
                        + inv.<Long>getArgument(3) + inv.<Long>getArgument(4)) / 100L), true, true));
        project = unit(SpendUnitType.PROJECT, "Platform", null);
        team = unit(SpendUnitType.TEAM, "Backend", project.getId());
        alice = unit(SpendUnitType.PERSON, "Alice", team.getId());
        lenient().when(unitRepository.findAllByAccountIdOrderByNameAsc(accountId)).thenReturn(List.of(alice, team, project));
    }

    private SpendUnit unit(SpendUnitType type, String name, UUID parent) {
        SpendUnit u = new SpendUnit();
        u.setId(UUID.randomUUID());
        u.setAccountId(accountId);
        u.setType(type);
        u.setName(name);
        u.setParentId(parent);
        return u;
    }

    private SpendAttributionRule rule(SpendUnit unit, AttributionMatchKind kind, String value, int priority) {
        SpendAttributionRule r = new SpendAttributionRule();
        r.setId(UUID.randomUUID());
        r.setAccountId(accountId);
        r.setSpendUnitId(unit.getId());
        r.setProvider(VendorProvider.ANTHROPIC);
        r.setMatchKind(kind);
        r.setMatchValue(value);
        r.setPriority(priority);
        return r;
    }

    private VendorUsageBucket bucket(VendorUsageSource source, String workspace, String key, String actor, long tokens, String vendorCents) {
        VendorUsageBucket b = new VendorUsageBucket();
        b.setAccountId(accountId);
        b.setProvider(VendorProvider.ANTHROPIC);
        b.setSource(source);
        b.setBucketStart(day);
        b.setBucketEnd(day.plusSeconds(86400));
        b.setModel("claude-sonnet-4-5");
        b.setWorkspaceId(workspace);
        b.setVendorApiKeyId(key);
        b.setActorId(actor);
        b.setUncachedInputTokens(tokens);
        b.setVendorCostCents(vendorCents == null ? null : new BigDecimal(vendorCents));
        return b;
    }

    @Test
    void allocatesByPriorityRollsUpAndKeepsPersonEstimatesSeparate() {
        when(settingsService.personLevelEnabled(accountId.toString())).thenReturn(true);
        when(ruleRepository.findAllByAccountIdOrderByPriorityAscCreatedAtAsc(accountId)).thenReturn(List.of(
                rule(alice, AttributionMatchKind.API_KEY_ID, "key_alice", 10),
                rule(team, AttributionMatchKind.WORKSPACE_ID, "wrkspc_backend", 100),
                rule(alice, AttributionMatchKind.ACTOR, "alice@acme.test", 100)));
        when(bucketRepository.findAllByAccountIdAndBucketStartGreaterThanEqualAndBucketStartLessThan(eq(accountId), any(), any()))
                .thenReturn(List.of(
                        bucket(VendorUsageSource.USAGE_API, "wrkspc_backend", "key_alice", null, 10_000, null),   // alice by key (priority 10 beats workspace)
                        bucket(VendorUsageSource.USAGE_API, "wrkspc_backend", "key_ci", null, 20_000, null),      // team by workspace
                        bucket(VendorUsageSource.USAGE_API, "wrkspc_other", "key_x", null, 5_000, null),          // nobody
                        bucket(VendorUsageSource.COST_API, "wrkspc_backend", null, null, 0, "999"),               // vendor cost: never allocated
                        bucket(VendorUsageSource.CLAUDE_CODE_API, null, null, "alice@acme.test", 3_000, "77")));   // person estimate

        SpendAllocationReportDto r = service.allocate(accountId.toString(), LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 21));

        assertTrue(r.isPersonLevelEnabled());
        assertEquals(0, new BigDecimal("350").compareTo(r.getTotalMeteredCents()));
        assertEquals(0, new BigDecimal("50").compareTo(r.getUnattributedCents()));
        SpendAllocationReportDto.Row a = row(r, "Alice");
        SpendAllocationReportDto.Row t = row(r, "Backend");
        SpendAllocationReportDto.Row p = row(r, "Platform");
        assertEquals(0, new BigDecimal("100").compareTo(a.getOwnCents()));
        assertEquals(0, new BigDecimal("100").compareTo(a.getTotalCents()));
        assertEquals(0, new BigDecimal("77").compareTo(a.getPersonEstimateCents()));
        assertEquals(0, new BigDecimal("177").compareTo(a.getSpendCents()));
        assertEquals(0, new BigDecimal("200").compareTo(t.getOwnCents()));
        assertEquals(0, new BigDecimal("300").compareTo(t.getTotalCents()));   // + Alice's own, not her estimate
        assertNull(t.getPersonEstimateCents());
        assertEquals(0, new BigDecimal("300").compareTo(p.getTotalCents()));
        assertEquals(0, BigDecimal.ZERO.compareTo(p.getOwnCents()));
    }

    @Test
    void personLevelOffIgnoresPersonRulesAndEstimates() {
        when(settingsService.personLevelEnabled(accountId.toString())).thenReturn(false);
        when(ruleRepository.findAllByAccountIdOrderByPriorityAscCreatedAtAsc(accountId)).thenReturn(List.of(
                rule(alice, AttributionMatchKind.API_KEY_ID, "key_alice", 10),
                rule(team, AttributionMatchKind.WORKSPACE_ID, "wrkspc_backend", 100)));
        when(bucketRepository.findAllByAccountIdAndBucketStartGreaterThanEqualAndBucketStartLessThan(eq(accountId), any(), any()))
                .thenReturn(List.of(
                        bucket(VendorUsageSource.USAGE_API, "wrkspc_backend", "key_alice", null, 10_000, null),
                        bucket(VendorUsageSource.CLAUDE_CODE_API, null, null, "alice@acme.test", 3_000, "77")));

        SpendAllocationReportDto r = service.allocate(accountId.toString(), LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 21));

        assertFalse(r.isPersonLevelEnabled());
        assertEquals(0, BigDecimal.ZERO.compareTo(row(r, "Alice").getSpendCents()));
        assertEquals(0, new BigDecimal("100").compareTo(row(r, "Backend").getOwnCents())); // falls through to the workspace rule
        assertEquals(0, BigDecimal.ZERO.compareTo(r.getUnattributedCents()));
    }

    @Test
    void aLoopInTheTreeCountsEachUnitOnce() {
        project.setParentId(team.getId()); // Platform → Backend → Platform
        when(settingsService.personLevelEnabled(accountId.toString())).thenReturn(false);
        when(ruleRepository.findAllByAccountIdOrderByPriorityAscCreatedAtAsc(accountId)).thenReturn(List.of(
                rule(team, AttributionMatchKind.WORKSPACE_ID, "wrkspc_backend", 100)));
        when(bucketRepository.findAllByAccountIdAndBucketStartGreaterThanEqualAndBucketStartLessThan(eq(accountId), any(), any()))
                .thenReturn(List.of(bucket(VendorUsageSource.USAGE_API, "wrkspc_backend", null, null, 10_000, null)));
        SpendAllocationReportDto r = service.allocate(accountId.toString(), LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 21));
        assertEquals(0, new BigDecimal("100").compareTo(row(r, "Backend").getTotalCents()));
        assertEquals(0, new BigDecimal("100").compareTo(row(r, "Platform").getTotalCents()));
    }

    @Test
    void providerMismatchDoesNotMatch() {
        VendorUsageBucket b = bucket(VendorUsageSource.USAGE_API, "proj_1", null, null, 100, null);
        b.setProvider(VendorProvider.OPENAI);
        assertNull(SpendAllocationServiceImpl.firstMatch(List.of(rule(team, AttributionMatchKind.WORKSPACE_ID, "proj_1", 1)), b));
    }

    private static SpendAllocationReportDto.Row row(SpendAllocationReportDto r, String name) {
        return r.getRows().stream().filter(x -> x.getName().equals(name)).findFirst().orElseThrow();
    }
}
