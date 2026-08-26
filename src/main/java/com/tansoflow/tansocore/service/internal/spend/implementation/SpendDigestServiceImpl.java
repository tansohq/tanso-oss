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
import com.tansoflow.tansocore.model.apikey.type.BudgetPeriod;
import com.tansoflow.tansocore.repository.SpendAlertRepository;
import com.tansoflow.tansocore.repository.SpendBudgetRepository;
import com.tansoflow.tansocore.service.internal.spend.SpendAllocationService;
import com.tansoflow.tansocore.service.internal.spend.SpendDigestService;
import com.tansoflow.tansocore.util.BudgetWindow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.modules.build.enabled", havingValue = "true", matchIfMissing = true)
public class SpendDigestServiceImpl implements SpendDigestService {
    private final SpendAllocationService allocationService;
    private final SpendBudgetRepository budgetRepository;
    private final SpendAlertRepository alertRepository;
    private final SpendNotifier notifier;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public SpendDigestDto build(String accountId) {
        Instant now = clock.instant();
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate from = today.minusDays(7);
        LocalDate previousFrom = today.minusDays(14);
        SpendAllocationReportDto week = allocationService.allocate(accountId, from, today);
        Map<String, BigDecimal> previous = new HashMap<>();
        for (SpendAllocationReportDto.AllocationRow r : allocationService.allocate(accountId, previousFrom, from).getRows()) {
            previous.put(r.getUnitId(), r.getTotalCents());
        }
        LocalDate monthStart = BudgetWindow.calendar(BudgetPeriod.MONTH, now).start().atZone(ZoneOffset.UTC).toLocalDate();
        Map<String, BigDecimal> monthToDate = new HashMap<>();
        for (SpendAllocationReportDto.AllocationRow r : allocationService.allocate(accountId, monthStart, today.plusDays(1)).getRows()) {
            monthToDate.put(r.getUnitId(), r.getSpendCents());
        }
        Map<UUID, SpendBudget> budgets = new HashMap<>();
        for (SpendBudget b : budgetRepository.findAllByAccountId(UUID.fromString(accountId))) {
            budgets.put(b.getSpendUnitId(), b);
        }
        List<SpendDigestDto.DigestRow> rows = new ArrayList<>();
        for (SpendAllocationReportDto.AllocationRow r : week.getRows()) {
            SpendBudget b = budgets.get(UUID.fromString(r.getUnitId()));
            BigDecimal limit = b == null ? null : b.effectiveMonthlyCents(now);
            rows.add(SpendDigestDto.DigestRow.builder()
                    .unitId(r.getUnitId()).name(r.getName())
                    .cents(r.getTotalCents()).previousCents(previous.getOrDefault(r.getUnitId(), BigDecimal.ZERO))
                    .monthlySpentCents(limit == null ? null : monthToDate.getOrDefault(r.getUnitId(), BigDecimal.ZERO))
                    .monthlyLimitCents(limit)
                    .bumpReason(b != null && b.bumpActive(now) ? b.getBumpReason() : null)
                    .build());
        }
        rows.sort((a, c) -> c.getCents().compareTo(a.getCents()));
        BigDecimal previousTotal = BigDecimal.ZERO;
        for (SpendAllocationReportDto.AllocationRow r : allocationService.allocate(accountId, previousFrom, from).getRows()) {
            if (r.getParentId() == null) {
                previousTotal = previousTotal.add(r.getTotalCents());
            }
        }
        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        int fired = 0;
        for (SpendAlert a : alertRepository.findTop200ByAccountIdOrderByFiredAtDesc(UUID.fromString(accountId))) {
            if (!a.getFiredAt().isBefore(fromInstant)) {
                fired++;
            }
        }
        return SpendDigestDto.builder()
                .from(from).to(today)
                .totalCents(week.getTotalMeteredCents())
                .previousTotalCents(previousTotalMetered(accountId, previousFrom, from))
                .unattributedCents(week.getUnattributedCents())
                .alertsFired(fired)
                .rows(rows)
                .build();
    }

    private BigDecimal previousTotalMetered(String accountId, LocalDate from, LocalDate to) {
        return allocationService.allocate(accountId, from, to).getTotalMeteredCents();
    }

    @Override
    @Transactional(readOnly = true)
    public SpendDigestDto send(String accountId) {
        SpendDigestDto digest = build(accountId);
        String subject = "AI spend this week: " + dollars(digest.getTotalCents()) + " (" + delta(digest.getTotalCents(), digest.getPreviousTotalCents()) + " vs last week)";
        notifier.notify(UUID.fromString(accountId), "spend.digest", subject, text(digest), html(digest), digest);
        log.info("Spend digest sent for account {}: {}", accountId, subject);
        return digest;
    }

    static String text(SpendDigestDto d) {
        StringBuilder sb = new StringBuilder();
        sb.append("AI spend ").append(d.getFrom()).append(" to ").append(d.getTo().minusDays(1)).append(": ")
                .append(dollars(d.getTotalCents())).append(" (").append(delta(d.getTotalCents(), d.getPreviousTotalCents())).append(" vs the week before)");
        if (d.getUnattributedCents().signum() > 0) {
            sb.append("; unattributed ").append(dollars(d.getUnattributedCents()));
        }
        sb.append("; ").append(d.getAlertsFired()).append(" alert").append(d.getAlertsFired() == 1 ? "" : "s").append(" fired.");
        for (SpendDigestDto.DigestRow r : d.getRows()) {
            sb.append("\n- ").append(r.getName()).append(": ").append(dollars(r.getCents()))
                    .append(" (").append(delta(r.getCents(), r.getPreviousCents())).append(")");
            if (r.getMonthlyLimitCents() != null) {
                sb.append(", month to date ").append(dollars(r.getMonthlySpentCents())).append(" of ").append(dollars(r.getMonthlyLimitCents()));
                if (r.getBumpReason() != null) {
                    sb.append(" [bumped: ").append(r.getBumpReason()).append("]");
                }
            }
        }
        return sb.toString();
    }

    static String html(SpendDigestDto d) {
        StringBuilder sb = new StringBuilder("<p><strong>AI spend ").append(d.getFrom()).append(" to ").append(d.getTo().minusDays(1)).append(":</strong> ")
                .append(dollars(d.getTotalCents())).append(" (").append(delta(d.getTotalCents(), d.getPreviousTotalCents())).append(" vs the week before). ")
                .append(d.getAlertsFired()).append(" alert").append(d.getAlertsFired() == 1 ? "" : "s").append(" fired.</p>");
        sb.append("<table cellpadding=\"6\" style=\"border-collapse:collapse\"><tr><th align=\"left\">Unit</th><th align=\"right\">This week</th><th align=\"right\">Change</th><th align=\"left\">Month to date</th></tr>");
        for (SpendDigestDto.DigestRow r : d.getRows()) {
            sb.append("<tr><td>").append(HtmlUtils.htmlEscape(r.getName())).append("</td><td align=\"right\">").append(dollars(r.getCents()))
                    .append("</td><td align=\"right\">").append(delta(r.getCents(), r.getPreviousCents())).append("</td><td>");
            if (r.getMonthlyLimitCents() != null) {
                sb.append(dollars(r.getMonthlySpentCents())).append(" of ").append(dollars(r.getMonthlyLimitCents()));
                if (r.getBumpReason() != null) {
                    sb.append(" <em>(bumped: ").append(HtmlUtils.htmlEscape(r.getBumpReason())).append(")</em>");
                }
            }
            sb.append("</td></tr>");
        }
        sb.append("</table>");
        if (d.getUnattributedCents().signum() > 0) {
            sb.append("<p>Unattributed: ").append(dollars(d.getUnattributedCents())).append(" — add rules to claim it.</p>");
        }
        return sb.toString();
    }

    static String delta(BigDecimal now, BigDecimal before) {
        if (before == null || before.signum() == 0) {
            return now.signum() == 0 ? "flat" : "new";
        }
        BigDecimal pct = now.subtract(before).multiply(BigDecimal.valueOf(100)).divide(before, 0, RoundingMode.HALF_UP);
        return (pct.signum() >= 0 ? "+" : "") + pct.toPlainString() + "%";
    }

    private static String dollars(BigDecimal cents) {
        return "$" + cents.movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
