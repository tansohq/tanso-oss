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
package com.tansoflow.tansocore.service.internal.analytics.implementation;

import com.tansoflow.tansocore.model.analytics.AgentFunnelResponse;
import com.tansoflow.tansocore.model.analytics.AgentFunnelSignupRow;
import com.tansoflow.tansocore.repository.AgentFunnelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentFunnelServiceImplTest {

    @Mock
    private AgentFunnelRepository agentFunnelRepository;

    @InjectMocks
    private AgentFunnelServiceImpl service;

    private final UUID accountId = UUID.randomUUID();
    private final LocalDate from = LocalDate.of(2026, 9, 1);
    private final LocalDate to = LocalDate.of(2026, 9, 4);

    @Test
    void emptyPeriod_returnsZeroRatesAndNullMedians() {
        when(agentFunnelRepository.findAgentSignups(eq(accountId), any(), any())).thenReturn(List.of());

        AgentFunnelResponse funnel = service.getFunnel(accountId.toString(), from, to);

        assertEquals(0, funnel.getStages().getSignups());
        assertEquals(0.0, funnel.getRates().getActivation());
        assertEquals(0.0, funnel.getRates().getClaim());
        assertEquals(0.0, funnel.getRates().getPaid());
        assertNull(funnel.getMedianHoursSignupToFirstJob());
        assertNull(funnel.getMedianHoursSignupToPaid());
        assertEquals(0, funnel.getExpired());
        assertEquals(3, funnel.getByDay().size());
        assertEquals("2026-09-01", funnel.getByDay().get(0).getDate());
        assertEquals("2026-09-03", funnel.getByDay().get(2).getDate());
        assertEquals("2026-09-01", funnel.getPeriod().getFrom());
        assertEquals("2026-09-04", funnel.getPeriod().getTo());
    }

    @Test
    void countsStagesRatesMediansAndByDay() {
        Instant day1 = Instant.parse("2026-09-01T10:00:00Z");
        Instant day2 = Instant.parse("2026-09-02T10:00:00Z");
        List<AgentFunnelSignupRow> rows = List.of(
                // signed up, first job after 2h, claimed and paid after 24h
                new AgentFunnelSignupRow(day1, "CLAIMED", day1.plusSeconds(86_400),
                        day1.plusSeconds(7_200), day1.plusSeconds(86_400)),
                // signed up, first job after 4h, never paid, expired
                new AgentFunnelSignupRow(day1, "EXPIRED", null, day1.plusSeconds(14_400), null),
                // signed up on day 2, never did anything
                new AgentFunnelSignupRow(day2, "PROVISIONAL", null, null, null),
                // signed up on day 2, paid after 48h without an events row
                new AgentFunnelSignupRow(day2, "CLAIMED", day2.plusSeconds(172_800), null, day2.plusSeconds(172_800)));
        when(agentFunnelRepository.findAgentSignups(eq(accountId), any(), any())).thenReturn(rows);

        AgentFunnelResponse funnel = service.getFunnel(accountId.toString(), from, to);

        assertEquals(4, funnel.getStages().getSignups());
        assertEquals(2, funnel.getStages().getFirstVerifiedJob());
        assertEquals(2, funnel.getStages().getClaimed());
        assertEquals(2, funnel.getStages().getPaid());
        assertEquals(0.5, funnel.getRates().getActivation());
        assertEquals(0.5, funnel.getRates().getClaim());
        assertEquals(0.5, funnel.getRates().getPaid());
        assertEquals(3.0, funnel.getMedianHoursSignupToFirstJob());
        assertEquals(36.0, funnel.getMedianHoursSignupToPaid());
        assertEquals(1, funnel.getExpired());

        AgentFunnelResponse.Day first = funnel.getByDay().get(0);
        assertEquals(2, first.getSignups());
        assertEquals(2, first.getFirstVerifiedJob());
        assertEquals(1, first.getClaimed());
        assertEquals(1, first.getPaid());
        AgentFunnelResponse.Day second = funnel.getByDay().get(1);
        assertEquals(2, second.getSignups());
        assertEquals(0, second.getFirstVerifiedJob());
        assertEquals(1, second.getClaimed());
        assertEquals(1, second.getPaid());
        assertEquals(0, funnel.getByDay().get(2).getSignups());
    }

    @Test
    void ratesRoundToFourDecimals() {
        Instant day1 = Instant.parse("2026-09-01T10:00:00Z");
        List<AgentFunnelSignupRow> rows = List.of(
                new AgentFunnelSignupRow(day1, "PROVISIONAL", null, day1.plusSeconds(60), null),
                new AgentFunnelSignupRow(day1, "PROVISIONAL", null, null, null),
                new AgentFunnelSignupRow(day1, "PROVISIONAL", null, null, null));
        when(agentFunnelRepository.findAgentSignups(eq(accountId), any(), any())).thenReturn(rows);

        AgentFunnelResponse funnel = service.getFunnel(accountId.toString(), from, to);

        assertEquals(0.3333, funnel.getRates().getActivation());
    }
}
