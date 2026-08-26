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
import com.tansoflow.tansocore.integration.spend.SpendNotifier;
import com.tansoflow.tansocore.model.spend.SpendAllocationReportDto;
import com.tansoflow.tansocore.model.spend.SpendDigestDto;
import com.tansoflow.tansocore.repository.SpendAlertRepository;
import com.tansoflow.tansocore.repository.SpendBudgetRepository;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpendDigestServiceImplTest {
    @Mock private SpendAllocationService allocationService;
    @Mock private SpendBudgetRepository budgetRepository;
    @Mock private SpendAlertRepository alertRepository;
    @Mock private SpendNotifier notifier;

    private final Instant now = Instant.parse("2026-08-25T14:00:00Z");
    private final UUID account = UUID.randomUUID();
    private final UUID backend = UUID.randomUUID();
    private final UUID support = UUID.randomUUID();
    private SpendDigestServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SpendDigestServiceImpl(allocationService, budgetRepository, alertRepository, notifier, Clock.fixed(now, ZoneOffset.UTC));
        lenient().when(allocationService.allocate(eq(account.toString()), any(), any())).thenAnswer(inv -> {
            LocalDate from = inv.getArgument(1);
            LocalDate to = inv.getArgument(2);
            String b, s, total, unattributed;
            if (from.equals(LocalDate.of(2026, 8, 18)) && to.equals(LocalDate.of(2026, 8, 25))) { b = "12000"; s = "3000"; total = "16000"; unattributed = "1000"; }
            else if (from.equals(LocalDate.of(2026, 8, 11)) && to.equals(LocalDate.of(2026, 8, 18))) { b = "8000"; s = "0"; total = "8000"; unattributed = "0"; }
            else if (from.equals(LocalDate.of(2026, 8, 1)) && to.equals(LocalDate.of(2026, 8, 26))) { b = "40000"; s = "9000"; total = "50000"; unattributed = "1000"; }
            else throw new AssertionError("unexpected window " + from + " → " + to);
            return SpendAllocationReportDto.builder()
                    .totalMeteredCents(new BigDecimal(total)).unattributedCents(new BigDecimal(unattributed))
                    .rows(List.of(
                            row(backend, "Backend", b),
                            row(support, "Support", s)))
                    .build();
        });
        SpendBudget budget = new SpendBudget();
        budget.setSpendUnitId(backend);
        budget.setMonthlyCents(new BigDecimal("50000"));
        budget.setBumpMonthlyCents(new BigDecimal("70000"));
        budget.setBumpExpiresAt(now.plusSeconds(3600));
        budget.setBumpReason("launch week");
        lenient().when(budgetRepository.findAllByAccountId(account)).thenReturn(List.of(budget));
        SpendAlert recent = new SpendAlert();
        recent.setFiredAt(now.minusSeconds(3600));
        SpendAlert old = new SpendAlert();
        old.setFiredAt(Instant.parse("2026-08-10T00:00:00Z"));
        lenient().when(alertRepository.findTop200ByAccountIdOrderByFiredAtDesc(account)).thenReturn(List.of(recent, old));
    }

    private static SpendAllocationReportDto.AllocationRow row(UUID id, String name, String cents) {
        return SpendAllocationReportDto.AllocationRow.builder().unitId(id.toString()).name(name)
                .spendCents(new BigDecimal(cents)).totalCents(new BigDecimal(cents)).ownCents(new BigDecimal(cents)).build();
    }

    @Test
    void digestComparesLastSevenDaysWithTheSevenBeforeAndCarriesBudgetStanding() {
        SpendDigestDto d = service.build(account.toString());
        assertEquals(LocalDate.of(2026, 8, 18), d.getFrom());
        assertEquals(LocalDate.of(2026, 8, 25), d.getTo());
        assertEquals(new BigDecimal("16000"), d.getTotalCents());
        assertEquals(new BigDecimal("8000"), d.getPreviousTotalCents());
        assertEquals(new BigDecimal("1000"), d.getUnattributedCents());
        assertEquals(1, d.getAlertsFired(), "only alerts fired inside the week count");
        assertEquals("Backend", d.getRows().get(0).getName(), "biggest spender first");
        SpendDigestDto.DigestRow b = d.getRows().get(0);
        assertEquals(new BigDecimal("8000"), b.getPreviousCents());
        assertEquals(new BigDecimal("40000"), b.getMonthlySpentCents());
        assertEquals(new BigDecimal("70000"), b.getMonthlyLimitCents(), "the bump is the ceiling in force");
        assertEquals("launch week", b.getBumpReason());
        assertNull(d.getRows().get(1).getMonthlyLimitCents(), "no budget → no standing");
    }

    @Test
    void sendRendersTextAndHtmlAndFansOutAsADigestEvent() {
        when(notifier.notify(eq(account), eq("spend.digest"), any(), any(), any(), any()))
                .thenReturn(new SpendNotifier.Delivery(SpendNotifier.Outcome.NOT_CONFIGURED, SpendNotifier.Outcome.SENT, SpendNotifier.Outcome.FAILED));
        SpendDigestDto sent = service.send(account.toString());
        assertEquals("SENT", sent.getDelivery().getWebhook());
        assertEquals("FAILED", sent.getDelivery().getEmail());
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(notifier).notify(eq(account), eq("spend.digest"), subject.capture(), text.capture(), html.capture(), any(SpendDigestDto.class));
        assertEquals("AI spend this week: $160.00 (+100% vs last week)", subject.getValue());
        assertTrue(text.getValue().contains("- Backend: $120.00 (+50%), month to date $400.00 of $700.00 [bumped: launch week]"), text.getValue());
        assertTrue(text.getValue().contains("- Support: $30.00 (new)"), text.getValue());
        assertTrue(text.getValue().contains("unattributed $10.00"));
        assertTrue(html.getValue().contains("<td>Backend</td>"));
        assertTrue(html.getValue().contains("(bumped: launch week)"));
    }

    @Test
    void deltaWording() {
        assertEquals("flat", SpendDigestServiceImpl.delta(BigDecimal.ZERO, BigDecimal.ZERO));
        assertEquals("new", SpendDigestServiceImpl.delta(BigDecimal.TEN, BigDecimal.ZERO));
        assertEquals("-25%", SpendDigestServiceImpl.delta(new BigDecimal("75"), new BigDecimal("100")));
    }
}
