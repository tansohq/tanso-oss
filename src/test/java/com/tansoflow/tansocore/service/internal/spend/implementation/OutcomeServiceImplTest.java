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

import com.tansoflow.tansocore.entity.Outcome;
import com.tansoflow.tansocore.entity.OutcomeSourceConnection;
import com.tansoflow.tansocore.entity.SpendUnit;
import com.tansoflow.tansocore.integration.spend.OutcomePuller;
import com.tansoflow.tansocore.integration.spend.OutcomeRecord;
import com.tansoflow.tansocore.model.spend.OutcomeDto;
import com.tansoflow.tansocore.model.spend.SpendAllocationReportDto;
import com.tansoflow.tansocore.model.spend.SpendOutcomeReportDto;
import com.tansoflow.tansocore.model.spend.request.OutcomeRequest;
import com.tansoflow.tansocore.model.spend.request.OutcomeSourceRequest;
import com.tansoflow.tansocore.model.spend.type.OutcomeKind;
import com.tansoflow.tansocore.model.spend.type.OutcomeSource;
import com.tansoflow.tansocore.model.spend.type.SpendUnitType;
import com.tansoflow.tansocore.repository.OutcomeRepository;
import com.tansoflow.tansocore.repository.OutcomeSourceConnectionRepository;
import com.tansoflow.tansocore.repository.SpendUnitRepository;
import com.tansoflow.tansocore.service.internal.spend.SpendAllocationService;
import com.tansoflow.tansocore.service.internal.spend.SpendSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutcomeServiceImplTest {

    @Mock private OutcomeSourceConnectionRepository sourceRepository;
    @Mock private OutcomeRepository outcomeRepository;
    @Mock private SpendUnitRepository unitRepository;
    @Mock private SpendAllocationService allocationService;
    @Mock private SpendSettingsService settingsService;
    @Mock private OutcomePuller github;
    @Mock private PlatformTransactionManager transactionManager;

    private final Instant now = Instant.parse("2026-08-25T12:00:00Z");
    private final UUID accountId = UUID.randomUUID();
    private OutcomeServiceImpl service;
    private SpendUnit team;
    private SpendUnit alice;

    @BeforeEach
    void setUp() {
        lenient().when(github.source()).thenReturn(OutcomeSource.GITHUB);
        service = new OutcomeServiceImpl(sourceRepository, outcomeRepository, unitRepository, allocationService, settingsService,
                List.of(github), transactionManager, Clock.fixed(now, ZoneOffset.UTC));
        team = unit(SpendUnitType.TEAM, "Backend", null);
        alice = unit(SpendUnitType.PERSON, "Alice", team.getId());
        alice.setEmail("alice@acme.test");
        alice.setGithubLogin("alice");
        lenient().when(unitRepository.findAllByAccountIdOrderByNameAsc(accountId)).thenReturn(List.of(alice, team));
        lenient().when(unitRepository.findByIdAndAccountId(team.getId(), accountId)).thenReturn(Optional.of(team));
        lenient().when(outcomeRepository.save(any())).thenAnswer(inv -> { Outcome o = inv.getArgument(0); if (o.getId() == null) o.setId(UUID.randomUUID()); return o; });
        lenient().when(outcomeRepository.findByAccountIdAndSourceAndExternalId(any(), any(), any())).thenReturn(Optional.empty());
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

    @Test
    void manualOutcomeAttributesToThePersonByEmailWhenPersonLevelIsOn() {
        when(settingsService.personLevelEnabled(accountId.toString())).thenReturn(true);
        OutcomeRequest req = new OutcomeRequest();
        req.setKind(OutcomeKind.CUSTOM);
        req.setExternalId("deploy-123");
        req.setActorEmail("Alice@Acme.test");
        req.setSpendUnitId(team.getId().toString());
        OutcomeDto dto = service.record(accountId.toString(), req);
        assertEquals(alice.getId().toString(), dto.getSpendUnitId());
        assertEquals(now, dto.getOccurredAt());
    }

    @Test
    void personLevelOffFallsBackToTheGivenUnit() {
        when(settingsService.personLevelEnabled(accountId.toString())).thenReturn(false);
        OutcomeRequest req = new OutcomeRequest();
        req.setKind(OutcomeKind.PR_MERGED);
        req.setExternalId("acme/app#7");
        req.setActorLogin("alice");
        req.setSpendUnitId(team.getId().toString());
        assertEquals(team.getId().toString(), service.record(accountId.toString(), req).getSpendUnitId());
        req.setSpendUnitId(null);
        assertNull(service.record(accountId.toString(), req).getSpendUnitId());
    }

    @Test
    void syncUpsertsByExternalIdAndUsesTheSourceDefault() {
        when(settingsService.personLevelEnabled(accountId.toString())).thenReturn(true);
        OutcomeSourceConnection src = new OutcomeSourceConnection();
        src.setId(UUID.randomUUID());
        src.setAccountId(accountId);
        src.setSource(OutcomeSource.GITHUB);
        src.setToken("ghp");
        src.setScope("acme/app");
        src.setDefaultSpendUnitId(team.getId());
        when(sourceRepository.findByIdAndAccountId(src.getId(), accountId)).thenReturn(Optional.of(src));
        when(sourceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Outcome existing = new Outcome();
        existing.setId(UUID.randomUUID());
        existing.setAccountId(accountId);
        existing.setTitle("old title");
        when(outcomeRepository.findByAccountIdAndSourceAndExternalId(accountId, OutcomeSource.GITHUB, "acme/app#1")).thenReturn(Optional.of(existing));
        when(github.pull(eq("ghp"), eq("acme/app"), any(), any())).thenReturn(List.of(
                new OutcomeRecord(OutcomeKind.PR_MERGED, "acme/app#1", "new title", null, null, "alice", now),
                new OutcomeRecord(OutcomeKind.PR_MERGED, "acme/app#2", "by a stranger", null, null, "zed", now)));

        service.sync(accountId.toString(), src.getId().toString(), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1));

        assertEquals("new title", existing.getTitle());
        assertEquals(alice.getId(), existing.getSpendUnitId());
        ArgumentCaptor<Outcome> saved = ArgumentCaptor.forClass(Outcome.class);
        org.mockito.Mockito.verify(outcomeRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        Outcome stranger = saved.getAllValues().stream().filter(o -> "acme/app#2".equals(o.getExternalId())).findFirst().orElseThrow();
        assertEquals(team.getId(), stranger.getSpendUnitId());
        assertEquals(now, src.getLastSyncedAt());
    }

    @Test
    void reportDividesRolledUpSpendByRolledUpOutcomes() {
        Instant d = Instant.parse("2026-08-10T00:00:00Z");
        Outcome pr = new Outcome(); pr.setKind(OutcomeKind.PR_MERGED); pr.setSpendUnitId(alice.getId()); pr.setOccurredAt(d);
        Outcome issue = new Outcome(); issue.setKind(OutcomeKind.ISSUE_DONE); issue.setSpendUnitId(team.getId()); issue.setOccurredAt(d);
        Outcome nobody = new Outcome(); nobody.setKind(OutcomeKind.CUSTOM); nobody.setSpendUnitId(null); nobody.setOccurredAt(d);
        when(outcomeRepository.findAllByAccountIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDesc(eq(accountId), any(), any()))
                .thenReturn(List.of(pr, issue, nobody));
        when(allocationService.allocate(eq(accountId.toString()), any(), any())).thenReturn(SpendAllocationReportDto.builder()
                .totalMeteredCents(new BigDecimal("900"))
                .rows(List.of(
                        SpendAllocationReportDto.Row.builder().unitId(team.getId().toString()).spendCents(new BigDecimal("600")).build(),
                        SpendAllocationReportDto.Row.builder().unitId(alice.getId().toString()).spendCents(new BigDecimal("250")).build()))
                .build());

        SpendOutcomeReportDto r = service.report(accountId.toString(), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1));

        assertEquals(3, r.getTotalOutcomes());
        assertEquals(1, r.getUnattributedOutcomes());
        assertEquals(0, new BigDecimal("300.00").compareTo(r.getCostPerOutcomeCents()));
        SpendOutcomeReportDto.Row t = r.getRows().stream().filter(x -> x.getName().equals("Backend")).findFirst().orElseThrow();
        assertEquals(2, t.getOutcomes());   // Alice's PR rolls up
        assertEquals(1, t.getPrsMerged());
        assertEquals(1, t.getIssuesDone());
        assertEquals(0, new BigDecimal("300.00").compareTo(t.getCostPerOutcomeCents()));
        SpendOutcomeReportDto.Row a = r.getRows().stream().filter(x -> x.getName().equals("Alice")).findFirst().orElseThrow();
        assertEquals(1, a.getOutcomes());
        assertEquals(0, new BigDecimal("250.00").compareTo(a.getCostPerOutcomeCents()));
    }

    @Test
    void manualSourcesAreNotAThing() {
        OutcomeSourceRequest req = new OutcomeSourceRequest();
        req.setSource(OutcomeSource.MANUAL);
        req.setLabel("x");
        req.setToken("t");
        req.setScope("s");
        assertThrows(IllegalArgumentException.class, () -> service.createSource(accountId.toString(), req));
    }
}
