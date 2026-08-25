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
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.model.spend.SpendAlertDto;
import com.tansoflow.tansocore.model.spend.SpendAllocationReportDto;
import com.tansoflow.tansocore.model.spend.SpendBudgetDto;
import com.tansoflow.tansocore.model.spend.request.SpendBudgetRequest;
import com.tansoflow.tansocore.model.spend.type.BudgetMode;
import com.tansoflow.tansocore.model.spend.type.SpendAlertKind;
import com.tansoflow.tansocore.repository.SpendAlertRepository;
import com.tansoflow.tansocore.repository.SpendBudgetRepository;
import com.tansoflow.tansocore.repository.SpendUnitRepository;
import com.tansoflow.tansocore.service.internal.spend.SpendAllocationService;
import com.tansoflow.tansocore.service.internal.spend.SpendBudgetService;
import com.tansoflow.tansocore.util.BudgetWindow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

/**
 * The Databricks shape: a small daily ceiling that catches a runaway agent
 * within the day, a monthly one for the real budget. Both are calendar
 * windows in UTC. An alert fires once per (unit, kind, window); acknowledging
 * it is the operator's "seen". Nothing here blocks a request — Tanso is not in
 * the request path — a BLOCK budget says so in its alert.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.modules.build.enabled", havingValue = "true", matchIfMissing = true)
public class SpendBudgetServiceImpl implements SpendBudgetService {
    /** A day counts as a spike when it is this many times the trailing-week mean … */
    static final BigDecimal SPIKE_FACTOR = new BigDecimal("2");
    /** … and at least this much in cents, so quiet units do not spike on noise. */
    static final BigDecimal SPIKE_FLOOR_CENTS = new BigDecimal("500");

    private final SpendBudgetRepository budgetRepository;
    private final SpendAlertRepository alertRepository;
    private final SpendUnitRepository unitRepository;
    private final SpendAllocationService allocationService;
    private final SlackNotifier slackNotifier;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public SpendBudgetDto getBudget(String accountId, String unitId) {
        UUID account = UUID.fromString(accountId);
        SpendUnit unit = requireUnit(account, unitId);
        SpendBudget budget = budgetRepository.findBySpendUnitIdAndAccountId(unit.getId(), account)
                .orElseThrow(() -> new ResourceNotFoundException("No budget on unit " + unitId));
        return toDto(budget, account);
    }

    @Override
    @Transactional
    public SpendBudgetDto putBudget(String accountId, String unitId, SpendBudgetRequest request) {
        UUID account = UUID.fromString(accountId);
        SpendUnit unit = requireUnit(account, unitId);
        if (request.getDailyCents() == null && request.getMonthlyCents() == null) {
            throw new IllegalArgumentException("Set a daily or a monthly ceiling (or both)");
        }
        for (BigDecimal v : new BigDecimal[]{request.getDailyCents(), request.getMonthlyCents()}) {
            if (v != null && v.signum() < 0) {
                throw new IllegalArgumentException("A ceiling cannot be negative");
            }
        }
        SpendBudget budget = budgetRepository.findBySpendUnitIdAndAccountId(unit.getId(), account).orElseGet(() -> {
            SpendBudget b = new SpendBudget();
            b.setAccountId(account);
            b.setSpendUnitId(unit.getId());
            return b;
        });
        budget.setDailyCents(request.getDailyCents());
        budget.setMonthlyCents(request.getMonthlyCents());
        if (request.getAlertThreshold() != null) {
            budget.setAlertThreshold(request.getAlertThreshold());
        }
        if (request.getMonthlyMode() != null) {
            budget.setMonthlyMode(request.getMonthlyMode());
        }
        return toDto(budgetRepository.save(budget), account);
    }

    @Override
    @Transactional
    public void deleteBudget(String accountId, String unitId) {
        UUID account = UUID.fromString(accountId);
        SpendUnit unit = requireUnit(account, unitId);
        budgetRepository.findBySpendUnitIdAndAccountId(unit.getId(), account).ifPresent(budgetRepository::delete);
    }

    @Override
    @Transactional
    public List<SpendAlertDto> evaluate(String accountId) {
        UUID account = UUID.fromString(accountId);
        List<SpendBudget> budgets = budgetRepository.findAllByAccountId(account);
        if (budgets.isEmpty()) {
            return List.of();
        }
        Instant now = clock.instant();
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        BudgetWindow day = BudgetWindow.calendar(BudgetPeriod.DAY, now);
        BudgetWindow month = BudgetWindow.calendar(BudgetPeriod.MONTH, now);
        Map<String, BigDecimal> daySpend = spendByUnit(accountId, today, today.plusDays(1));
        Map<String, BigDecimal> monthSpend = spendByUnit(accountId, month.start().atZone(ZoneOffset.UTC).toLocalDate(), today.plusDays(1));
        Map<String, BigDecimal> weekSpend = spendByUnit(accountId, today.minusDays(7), today);
        Map<UUID, String> names = new HashMap<>();
        for (SpendUnit u : unitRepository.findAllByAccountIdOrderByNameAsc(account)) {
            names.put(u.getId(), u.getName());
        }

        List<SpendAlertDto> fired = new ArrayList<>();
        for (SpendBudget budget : budgets) {
            String unitKey = budget.getSpendUnitId().toString();
            String name = names.getOrDefault(budget.getSpendUnitId(), unitKey);
            BigDecimal spentToday = daySpend.getOrDefault(unitKey, BigDecimal.ZERO);
            BigDecimal spentMonth = monthSpend.getOrDefault(unitKey, BigDecimal.ZERO);
            if (budget.getDailyCents() != null && budget.getDailyCents().signum() > 0) {
                fired.addAll(check(budget, name, BudgetPeriod.DAY, day.start(), spentToday, budget.getDailyCents(), BudgetMode.ALERT));
            }
            if (budget.getMonthlyCents() != null && budget.getMonthlyCents().signum() > 0) {
                fired.addAll(check(budget, name, BudgetPeriod.MONTH, month.start(), spentMonth, budget.getMonthlyCents(), budget.getMonthlyMode()));
            }
            BigDecimal weekMean = weekSpend.getOrDefault(unitKey, BigDecimal.ZERO).divide(BigDecimal.valueOf(7), 2, RoundingMode.HALF_UP);
            if (spentToday.compareTo(SPIKE_FLOOR_CENTS) >= 0 && spentToday.compareTo(weekMean.multiply(SPIKE_FACTOR)) > 0
                    && !alertRepository.existsBySpendUnitIdAndKindAndPeriodAndWindowStart(budget.getSpendUnitId(), SpendAlertKind.SPIKE, BudgetPeriod.DAY, day.start())) {
                fired.add(fire(budget, SpendAlertKind.SPIKE, BudgetPeriod.DAY, day.start(), spentToday, null,
                        name + " has spent " + dollars(spentToday) + " today, against a trailing-week average of " + dollars(weekMean) + " a day.", name));
            }
        }
        return fired;
    }

    private List<SpendAlertDto> check(SpendBudget budget, String name, BudgetPeriod period, Instant windowStart,
                                      BigDecimal spent, BigDecimal limit, BudgetMode mode) {
        List<SpendAlertDto> fired = new ArrayList<>();
        int percent = spent.multiply(BigDecimal.valueOf(100)).divide(limit, 0, RoundingMode.FLOOR).intValue();
        String window = period == BudgetPeriod.DAY ? "today" : "this month";
        String periodWord = period == BudgetPeriod.DAY ? "daily" : "monthly";
        if (percent >= 100 && !exists(budget, SpendAlertKind.BREACH, period, windowStart)) {
            String tail = mode == BudgetMode.BLOCK
                    ? " This budget is set to BLOCK; Tanso cannot stop requests itself — revoke the key or enforce it at your gateway."
                    : "";
            fired.add(fire(budget, SpendAlertKind.BREACH, period, windowStart, spent, limit,
                    name + " is over its " + periodWord + " budget: " + dollars(spent) + " of " + dollars(limit) + " " + window + "." + tail, name));
        } else if (percent >= budget.getAlertThreshold() && percent < 100 && !exists(budget, SpendAlertKind.THRESHOLD, period, windowStart)) {
            fired.add(fire(budget, SpendAlertKind.THRESHOLD, period, windowStart, spent, limit,
                    name + " is at " + percent + "% of its " + periodWord + " budget: " + dollars(spent) + " of " + dollars(limit) + " " + window + ".", name));
        }
        return fired;
    }

    private boolean exists(SpendBudget budget, SpendAlertKind kind, BudgetPeriod period, Instant windowStart) {
        return alertRepository.existsBySpendUnitIdAndKindAndPeriodAndWindowStart(budget.getSpendUnitId(), kind, period, windowStart);
    }

    private SpendAlertDto fire(SpendBudget budget, SpendAlertKind kind, BudgetPeriod period, Instant windowStart,
                               BigDecimal spent, BigDecimal limit, String message, String unitName) {
        SpendAlert alert = new SpendAlert();
        alert.setAccountId(budget.getAccountId());
        alert.setSpendUnitId(budget.getSpendUnitId());
        alert.setKind(kind);
        alert.setPeriod(period);
        alert.setWindowStart(windowStart);
        alert.setSpentCents(spent);
        alert.setLimitCents(limit);
        alert.setMessage(message);
        alert.setFiredAt(clock.instant());
        alert = alertRepository.save(alert);
        slackNotifier.post(budget.getAccountId(), "[tanso] " + message);
        log.info("Spend alert {} for unit {}: {}", kind, budget.getSpendUnitId(), message);
        return toDto(alert, unitName);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SpendAlertDto> listAlerts(String accountId, boolean unackedOnly) {
        UUID account = UUID.fromString(accountId);
        Map<UUID, String> names = new HashMap<>();
        for (SpendUnit u : unitRepository.findAllByAccountIdOrderByNameAsc(account)) {
            names.put(u.getId(), u.getName());
        }
        List<SpendAlert> alerts = unackedOnly
                ? alertRepository.findAllByAccountIdAndAckedAtIsNullOrderByFiredAtDesc(account)
                : alertRepository.findTop200ByAccountIdOrderByFiredAtDesc(account);
        return alerts.stream().map(a -> toDto(a, names.get(a.getSpendUnitId()))).toList();
    }

    @Override
    @Transactional
    public SpendAlertDto ack(String accountId, String alertId, String actor) {
        SpendAlert alert = alertRepository.findByIdAndAccountId(UUID.fromString(alertId), UUID.fromString(accountId))
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found: " + alertId));
        if (alert.getAckedAt() == null) {
            alert.setAckedAt(clock.instant());
            alert.setAckedBy(actor);
            alertRepository.save(alert);
        }
        String unitName = unitRepository.findById(alert.getSpendUnitId()).map(SpendUnit::getName).orElse(null);
        return toDto(alert, unitName);
    }

    private Map<String, BigDecimal> spendByUnit(String accountId, LocalDate from, LocalDate to) {
        Map<String, BigDecimal> out = new HashMap<>();
        if (!from.isBefore(to)) {
            return out;
        }
        for (SpendAllocationReportDto.AllocationRow row : allocationService.allocate(accountId, from, to).getRows()) {
            out.put(row.getUnitId(), row.getSpendCents());
        }
        return out;
    }

    private SpendBudgetDto toDto(SpendBudget budget, UUID account) {
        Instant now = clock.instant();
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        BudgetWindow day = BudgetWindow.calendar(BudgetPeriod.DAY, now);
        BudgetWindow month = BudgetWindow.calendar(BudgetPeriod.MONTH, now);
        String unitKey = budget.getSpendUnitId().toString();
        BigDecimal spentToday = spendByUnit(account.toString(), today, today.plusDays(1)).getOrDefault(unitKey, BigDecimal.ZERO);
        BigDecimal spentMonth = spendByUnit(account.toString(), month.start().atZone(ZoneOffset.UTC).toLocalDate(), today.plusDays(1))
                .getOrDefault(unitKey, BigDecimal.ZERO);
        return SpendBudgetDto.builder()
                .spendUnitId(unitKey)
                .dailyCents(budget.getDailyCents()).monthlyCents(budget.getMonthlyCents())
                .alertThreshold(budget.getAlertThreshold()).monthlyMode(budget.getMonthlyMode())
                .dailySpentCents(spentToday).monthlySpentCents(spentMonth)
                .dailyResetsAt(day.resetsAt()).monthlyResetsAt(month.resetsAt())
                .build();
    }

    private static SpendAlertDto toDto(SpendAlert a, String unitName) {
        return SpendAlertDto.builder()
                .id(a.getId() == null ? null : a.getId().toString())
                .spendUnitId(a.getSpendUnitId().toString()).unitName(unitName)
                .kind(a.getKind()).period(a.getPeriod()).windowStart(a.getWindowStart())
                .spentCents(a.getSpentCents()).limitCents(a.getLimitCents()).message(a.getMessage())
                .firedAt(a.getFiredAt()).ackedAt(a.getAckedAt()).ackedBy(a.getAckedBy())
                .build();
    }

    private SpendUnit requireUnit(UUID account, String unitId) {
        return unitRepository.findByIdAndAccountId(UUID.fromString(unitId), account)
                .orElseThrow(() -> new ResourceNotFoundException("Spend unit not found: " + unitId));
    }

    private static String dollars(BigDecimal cents) {
        return "$" + cents.movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
