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
import com.tansoflow.tansocore.service.internal.analytics.AgentFunnelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AgentFunnelServiceImpl implements AgentFunnelService {

    private final AgentFunnelRepository agentFunnelRepository;

    @Override
    public AgentFunnelResponse getFunnel(String accountId, LocalDate from, LocalDate to) {
        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = to.atStartOfDay(ZoneOffset.UTC).toInstant();
        List<AgentFunnelSignupRow> rows = agentFunnelRepository.findAgentSignups(
                UUID.fromString(accountId), fromInstant, toInstant);

        long signups = rows.size();
        long firstJob = 0;
        long claimed = 0;
        long paid = 0;
        long expired = 0;
        List<Double> hoursToFirstJob = new ArrayList<>();
        List<Double> hoursToPaid = new ArrayList<>();
        for (AgentFunnelSignupRow row : rows) {
            if (row.firstEventAt() != null) {
                firstJob++;
                hoursToFirstJob.add(hoursBetween(row.createdAt(), row.firstEventAt()));
            }
            if (row.agentClaimedAt() != null) {
                claimed++;
            }
            if (row.firstPaidAt() != null) {
                paid++;
                hoursToPaid.add(hoursBetween(row.createdAt(), row.firstPaidAt()));
            }
            if ("EXPIRED".equals(row.agentStatus())) {
                expired++;
            }
        }

        return AgentFunnelResponse.builder()
                .period(new AgentFunnelResponse.Period(from.toString(), to.toString()))
                .stages(new AgentFunnelResponse.Stages(signups, firstJob, claimed, paid))
                .rates(new AgentFunnelResponse.Rates(
                        rate(firstJob, signups), rate(claimed, signups), rate(paid, signups)))
                .medianHoursSignupToFirstJob(median(hoursToFirstJob))
                .medianHoursSignupToPaid(median(hoursToPaid))
                .expired(expired)
                .byDay(byDay(rows, from, to))
                .build();
    }

    private static List<AgentFunnelResponse.Day> byDay(List<AgentFunnelSignupRow> rows, LocalDate from, LocalDate to) {
        List<AgentFunnelResponse.Day> days = new ArrayList<>();
        for (LocalDate date = from; date.isBefore(to); date = date.plusDays(1)) {
            long signups = 0;
            long firstJob = 0;
            long claimed = 0;
            long paid = 0;
            for (AgentFunnelSignupRow row : rows) {
                if (!row.createdAt().atZone(ZoneOffset.UTC).toLocalDate().equals(date)) {
                    continue;
                }
                signups++;
                if (row.firstEventAt() != null) {
                    firstJob++;
                }
                if (row.agentClaimedAt() != null) {
                    claimed++;
                }
                if (row.firstPaidAt() != null) {
                    paid++;
                }
            }
            days.add(new AgentFunnelResponse.Day(date.toString(), signups, firstJob, claimed, paid));
        }
        return days;
    }

    private static double hoursBetween(Instant start, Instant end) {
        return Duration.between(start, end).toMillis() / 3_600_000.0;
    }

    private static double rate(long count, long signups) {
        if (signups == 0) {
            return 0;
        }
        return Math.round((double) count / signups * 10_000) / 10_000.0;
    }

    private static Double median(List<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        Collections.sort(values);
        int middle = values.size() / 2;
        double median = values.size() % 2 == 1
                ? values.get(middle)
                : (values.get(middle - 1) + values.get(middle)) / 2;
        return Math.round(median * 10) / 10.0;
    }
}
