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

import com.tansoflow.tansocore.entity.SpendAlert;
import com.tansoflow.tansocore.entity.SpendBudget;
import com.tansoflow.tansocore.entity.SpendUnit;
import com.tansoflow.tansocore.integration.spend.SlackNotifier;
import com.tansoflow.tansocore.model.apikey.type.BudgetPeriod;
import com.tansoflow.tansocore.model.spend.SpendAlertDto;
import com.tansoflow.tansocore.model.spend.SpendAllocationReportDto;
import com.tansoflow.tansocore.model.spend.SpendBudgetDto;
import com.tansoflow.tansocore.model.spend.request.SpendBudgetRequest;
import com.tansoflow.tansocore.model.spend.type.BudgetMode;
import com.tansoflow.tansocore.model.spend.type.SpendAlertKind;
import com.tansoflow.tansocore.model.spend.type.SpendUnitType;
import com.tansoflow.tansocore.repository.SpendAlertRepository;
import com.tansoflow.tansocore.repository.SpendBudgetRepository;
import com.tansoflow.tansocore.repository.SpendUnitRepository;
import com.tansoflow.tansocore.service.internal.spend.SpendAllocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpendBudgetServiceImplTest {

    @Mock private SpendBudgetRepository budgetRepository;
    @Mock private SpendAlertRepository alertRepository;
    @Mock private SpendUnitRepository unitRepository;
    @Mock private SpendAllocationService allocationService;
    @Mock private SlackNotifier slackNotifier;
    @Mock private com.tansoflow.tansocore.service.internal.spend.GatewayEnforcementService gatewayEnforcement;

    private final Instant now = Instant.parse("2026-08-25T14:00:00Z");
    private final UUID accountId = UUID.randomUUID();
    private SpendBudgetServiceImpl service;
    private SpendUnit unit;
    private SpendBudget budget;

    @BeforeEach
    void setUp() {
        service = new SpendBudgetServiceImpl(budgetRepository, alertRepository, unitRepository, allocationService, slackNotifier, gatewayEnforcement,
                Clock.fixed(now, ZoneOffset.UTC));
        unit = new SpendUnit();
        unit.setId(UUID.randomUUID());
        unit.setAccountId(accountId);
        unit.setType(SpendUnitType.TEAM);
        unit.setName("Backend");
        budget = new SpendBudget();
        budget.setId(UUID.randomUUID());
        budget.setAccountId(accountId);
        budget.setSpendUnitId(unit.getId());
        budget.setDailyCents(new BigDecimal("1000"));
        budget.setMonthlyCents(new BigDecimal("20000"));
        lenient().when(unitRepository.findAllByAccountIdOrderByNameAsc(accountId)).thenReturn(List.of(unit));
        lenient().when(alertRepository.save(any())).thenAnswer(inv -> { SpendAlert a = inv.getArgument(0); if (a.getId() == null) a.setId(UUID.randomUUID()); return a; });
    }

    /** today / month-to-date / trailing week, in cents for the unit */
    private void spend(String today, String month, String week) {
        lenient().when(allocationService.allocate(eq(accountId.toString()), any(), any())).thenAnswer(inv -> {
            LocalDate from = inv.getArgument(1);
            LocalDate to = inv.getArgument(2);
            String cents;
            if (from.equals(LocalDate.of(2026, 8, 25)) && to.equals(LocalDate.of(2026, 8, 26))) cents = today;
            else if (from.equals(LocalDate.of(2026, 8, 1))) cents = month;
            else if (from.equals(LocalDate.of(2026, 8, 18)) && to.equals(LocalDate.of(2026, 8, 25))) cents = week;
            else throw new AssertionError("unexpected window " + from + " → " + to);
            return SpendAllocationReportDto.builder().rows(List.of(SpendAllocationReportDto.AllocationRow.builder()
                    .unitId(unit.getId().toString()).name("Backend").spendCents(new BigDecimal(cents)).build())).build();
        });
    }

    @Test
    void thresholdFiresOncePerWindowAndBreachReplacesIt() {
        when(budgetRepository.findAllByAccountId(accountId)).thenReturn(List.of(budget));
        spend("850", "5000", "3500");
        when(alertRepository.existsBySpendUnitIdAndKindAndPeriodAndWindowStart(any(), any(), any(), any())).thenReturn(false);

        List<SpendAlertDto> fired = service.evaluate(accountId.toString());

        assertEquals(1, fired.size(), fired.stream().map(f -> f.getKind() + "/" + f.getPeriod() + ": " + f.getMessage()).toList().toString());
        assertEquals(SpendAlertKind.THRESHOLD, fired.get(0).getKind());
        assertEquals("Backend", fired.get(0).getUnitName());
        assertEquals(now, fired.get(0).getFiredAt());
        assertEquals(BudgetPeriod.DAY, fired.get(0).getPeriod());
        assertEquals(Instant.parse("2026-08-25T00:00:00Z"), fired.get(0).getWindowStart());
        assertTrue(fired.get(0).getMessage().contains("85%"));
        verify(slackNotifier).post(eq(accountId), anyString());

        // same window, already recorded → silent
        when(alertRepository.existsBySpendUnitIdAndKindAndPeriodAndWindowStart(unit.getId(), SpendAlertKind.THRESHOLD, BudgetPeriod.DAY,
                Instant.parse("2026-08-25T00:00:00Z"))).thenReturn(true);
        assertEquals(0, service.evaluate(accountId.toString()).size());

        // crosses the ceiling → BREACH, once (week mean raised so this is not also a spike)
        spend("1200", "5000", "7000");
        List<SpendAlertDto> breach = service.evaluate(accountId.toString());
        assertEquals(1, breach.size());
        assertEquals(SpendAlertKind.BREACH, breach.get(0).getKind());
        assertEquals(0, new BigDecimal("1000").compareTo(breach.get(0).getLimitCents()));
    }

    @Test
    void blockModeSaysItCannotBlock() {
        budget.setMonthlyMode(BudgetMode.BLOCK);
        budget.setDailyCents(null);
        when(budgetRepository.findAllByAccountId(accountId)).thenReturn(List.of(budget));
        spend("100", "25000", "700");
        when(alertRepository.existsBySpendUnitIdAndKindAndPeriodAndWindowStart(any(), any(), any(), any())).thenReturn(false);
        List<SpendAlertDto> fired = service.evaluate(accountId.toString());
        assertEquals(1, fired.size());
        assertEquals(BudgetPeriod.MONTH, fired.get(0).getPeriod());
        assertTrue(fired.get(0).getMessage().contains("cannot stop requests"));
    }

    @Test
    void spikeNeedsBothTheFactorAndTheFloor() {
        budget.setDailyCents(null);
        budget.setMonthlyCents(null);
        when(budgetRepository.findAllByAccountId(accountId)).thenReturn(List.of(budget));
        when(alertRepository.existsBySpendUnitIdAndKindAndPeriodAndWindowStart(any(), any(), any(), any())).thenReturn(false);

        spend("400", "400", "70");     // 4x the week mean but under the floor
        assertEquals(0, service.evaluate(accountId.toString()).size());

        spend("1500", "1500", "3500"); // 3x the 500/day mean and over the floor
        List<SpendAlertDto> fired = service.evaluate(accountId.toString());
        assertEquals(1, fired.size());
        assertEquals(SpendAlertKind.SPIKE, fired.get(0).getKind());
        assertTrue(fired.get(0).getMessage().contains("$15.00"));
    }

    @Test
    void putValidatesAndReportsStanding() {
        when(unitRepository.findByIdAndAccountId(unit.getId(), accountId)).thenReturn(Optional.of(unit));
        when(budgetRepository.findBySpendUnitIdAndAccountId(unit.getId(), accountId)).thenReturn(Optional.empty());
        when(budgetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        spend("250", "4000", "0");
        SpendBudgetRequest req = new SpendBudgetRequest();
        req.setDailyCents(new BigDecimal("1000"));
        req.setAlertThreshold(90);

        SpendBudgetDto dto = service.putBudget(accountId.toString(), unit.getId().toString(), req);

        assertEquals(90, dto.getAlertThreshold());
        assertEquals(0, new BigDecimal("250").compareTo(dto.getDailySpentCents()));
        assertEquals(0, new BigDecimal("4000").compareTo(dto.getMonthlySpentCents()));
        assertEquals(Instant.parse("2026-08-26T00:00:00Z"), dto.getDailyResetsAt());
        assertEquals(Instant.parse("2026-09-01T00:00:00Z"), dto.getMonthlyResetsAt());

        SpendBudgetRequest empty = new SpendBudgetRequest();
        assertThrows(IllegalArgumentException.class, () -> service.putBudget(accountId.toString(), unit.getId().toString(), empty));
        SpendBudgetRequest negative = new SpendBudgetRequest();
        negative.setDailyCents(new BigDecimal("-1"));
        assertThrows(IllegalArgumentException.class, () -> service.putBudget(accountId.toString(), unit.getId().toString(), negative));
    }

    @Test
    void ackStampsOnceAndKeepsTheFirstActor() {
        SpendAlert alert = new SpendAlert();
        alert.setId(UUID.randomUUID());
        alert.setAccountId(accountId);
        alert.setSpendUnitId(unit.getId());
        alert.setKind(SpendAlertKind.BREACH);
        alert.setSpentCents(BigDecimal.TEN);
        alert.setMessage("x");
        when(alertRepository.findByIdAndAccountId(alert.getId(), accountId)).thenReturn(Optional.of(alert));

        service.ack(accountId.toString(), alert.getId().toString(), "kat@tanso.test");
        service.ack(accountId.toString(), alert.getId().toString(), "someone-else");

        assertEquals("kat@tanso.test", alert.getAckedBy());
        assertEquals(now, alert.getAckedAt());
        verify(alertRepository, times(1)).save(alert);
    }

    @Test
    void noBudgetsMeansNoWork() {
        when(budgetRepository.findAllByAccountId(accountId)).thenReturn(List.of());
        assertEquals(0, service.evaluate(accountId.toString()).size());
        verify(allocationService, never()).allocate(anyString(), any(), any());
    }
}
