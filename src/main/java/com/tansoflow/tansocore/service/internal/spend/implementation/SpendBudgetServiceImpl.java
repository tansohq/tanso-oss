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

import jakarta.persistence.PersistenceContext;
import jakarta.persistence.EntityManager;
import com.tansoflow.tansocore.entity.SpendAlert;
import com.tansoflow.tansocore.entity.SpendAttributionRule;
import com.tansoflow.tansocore.entity.VendorUsageBucket;
import com.tansoflow.tansocore.model.spend.type.VendorProvider;
import com.tansoflow.tansocore.model.spend.type.VendorUsageSource;
import com.tansoflow.tansocore.entity.SpendBudget;
import com.tansoflow.tansocore.entity.SpendUnit;
import com.tansoflow.tansocore.integration.spend.SpendNotifier;
import com.tansoflow.tansocore.model.apikey.type.BudgetPeriod;
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.model.spend.SpendAlertDto;
import com.tansoflow.tansocore.model.spend.SpendAllocationReportDto;
import com.tansoflow.tansocore.model.spend.SpendBudgetDto;
import com.tansoflow.tansocore.model.spend.request.SpendBudgetBumpRequest;
import com.tansoflow.tansocore.model.spend.request.SpendBudgetRequest;
import com.tansoflow.tansocore.model.spend.type.BudgetMode;
import com.tansoflow.tansocore.model.spend.type.SpendAlertKind;
import com.tansoflow.tansocore.repository.SpendAlertRepository;
import com.tansoflow.tansocore.repository.SpendAttributionRuleRepository;
import com.tansoflow.tansocore.repository.SpendBudgetRepository;
import com.tansoflow.tansocore.repository.VendorUsageBucketRepository;
import com.tansoflow.tansocore.repository.SpendUnitRepository;
import com.tansoflow.tansocore.service.internal.spend.SpendAllocationService;
import com.tansoflow.tansocore.service.internal.spend.GatewayEnforcementService;
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
    private final SpendAttributionRuleRepository ruleRepository;
    private final VendorUsageBucketRepository bucketRepository;
    private final SpendNotifier notifier;
    private final GatewayEnforcementService gatewayEnforcement;
    private final Clock clock;
    @PersistenceContext
    private EntityManager entityManager;

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
        gatewayEnforcement.apply(budget);
        return toDto(budgetRepository.save(budget), account);
    }

    @Override
    @Transactional
    public void deleteBudget(String accountId, String unitId) {
        UUID account = UUID.fromString(accountId);
        SpendUnit unit = requireUnit(account, unitId);
        budgetRepository.findBySpendUnitIdAndAccountId(unit.getId(), account).ifPresent(b -> {
            b.setMonthlyMode(BudgetMode.ALERT);   // clears the hard limit at the gateway before the row goes
            gatewayEnforcement.apply(b);
            budgetRepository.delete(b);
        });
    }

    @Override
    @Transactional
    public SpendBudgetDto bump(String accountId, String unitId, SpendBudgetBumpRequest request) {
        UUID account = UUID.fromString(accountId);
        SpendUnit unit = requireUnit(account, unitId);
        SpendBudget budget = budgetRepository.findBySpendUnitIdAndAccountId(unit.getId(), account)
                .orElseThrow(() -> new ResourceNotFoundException("No budget on " + unit.getName() + " to bump — set a monthly ceiling first"));
        Instant now = clock.instant();
        if (budget.getMonthlyCents() == null || budget.getMonthlyCents().signum() <= 0) {
            throw new IllegalArgumentException("A bump lifts the monthly ceiling; set one first");
        }
        if (request.getMonthlyCents().compareTo(budget.getMonthlyCents()) <= 0) {
            throw new IllegalArgumentException("A bump must be above the standing ceiling of " + dollars(budget.getMonthlyCents()) + " — lower the budget instead");
        }
        if (!request.getExpiresAt().isAfter(now)) {
            throw new IllegalArgumentException("The bump's expiry is already in the past");
        }
        budget.setBumpMonthlyCents(request.getMonthlyCents());
        budget.setBumpExpiresAt(request.getExpiresAt());
        budget.setBumpReason(request.getReason() == null || request.getReason().isBlank() ? null : request.getReason().trim());
        gatewayEnforcement.apply(budget);
        return toDto(budgetRepository.save(budget), account);
    }

    @Override
    @Transactional
    public SpendBudgetDto clearBump(String accountId, String unitId) {
        UUID account = UUID.fromString(accountId);
        SpendUnit unit = requireUnit(account, unitId);
        SpendBudget budget = budgetRepository.findBySpendUnitIdAndAccountId(unit.getId(), account)
                .orElseThrow(() -> new ResourceNotFoundException("No budget on " + unit.getName()));
        budget.setBumpMonthlyCents(null);
        budget.setBumpExpiresAt(null);
        budget.setBumpReason(null);
        gatewayEnforcement.apply(budget);
        return toDto(budgetRepository.save(budget), account);
    }

    @Override
    @Transactional
    public List<SpendAlertDto> evaluate(String accountId) {
        UUID account = UUID.fromString(accountId);
        if (entityManager != null) {   // two evaluations of one account (job + sync + console) would both pass the once-per-window check
            entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:k))")
                    .setParameter("k", "spend-evaluate:" + accountId).getSingleResult();
        }
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
            if (budget.getBumpMonthlyCents() != null && !budget.bumpActive(now)) {
                // The bump ran out: drop it and put the standing ceiling back at the gateway.
                budget.setBumpMonthlyCents(null);
                budget.setBumpExpiresAt(null);
                budget.setBumpReason(null);
                gatewayEnforcement.apply(budget);
                budgetRepository.save(budget);
            }
            String unitKey = budget.getSpendUnitId().toString();
            String name = names.getOrDefault(budget.getSpendUnitId(), unitKey);
            BigDecimal spentToday = daySpend.getOrDefault(unitKey, BigDecimal.ZERO);
            BigDecimal spentMonth = monthSpend.getOrDefault(unitKey, BigDecimal.ZERO);
            if (budget.getDailyCents() != null && budget.getDailyCents().signum() > 0) {
                fired.addAll(check(budget, name, BudgetPeriod.DAY, day.start(), spentToday, budget.getDailyCents(), BudgetMode.ALERT));
            }
            BigDecimal monthlyLimit = budget.effectiveMonthlyCents(now);
            if (monthlyLimit != null && monthlyLimit.signum() > 0) {
                fired.addAll(check(budget, name, BudgetPeriod.MONTH, month.start(), spentMonth, monthlyLimit, budget.getMonthlyMode()));
                fired.addAll(project(budget, name, month, now, spentMonth, monthlyLimit));
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
            String tail = mode != BudgetMode.BLOCK ? ""
                    : budget.getEnforcementTarget() != null
                    ? " Enforced at " + budget.getEnforcementTarget() + " — the gateway is refusing further requests this month."
                    : " This budget is set to BLOCK; Tanso cannot stop requests itself — connect LiteLLM and add a rule naming this unit's team or key, or revoke the key.";
            fired.add(fire(budget, SpendAlertKind.BREACH, period, windowStart, spent, limit,
                    name + " is over its " + periodWord + " budget: " + dollars(spent) + " of " + dollars(limit) + " " + window + "." + tail, name));
        } else if (percent >= budget.getAlertThreshold() && percent < 100 && !exists(budget, SpendAlertKind.THRESHOLD, period, windowStart)) {
            fired.add(fire(budget, SpendAlertKind.THRESHOLD, period, windowStart, spent, limit,
                    name + " is at " + percent + "% of its " + periodWord + " budget: " + dollars(spent) + " of " + dollars(limit) + " " + window + ".", name));
        }
        return fired;
    }

    /**
     * Straight-line pace: spent / share of the month elapsed. Fires once per month when the projection
     * lands above the ceiling and the ceiling is not already breached, but never in the first fifth of
     * the month, where one heavy day would project to anything.
     */
    private List<SpendAlertDto> project(SpendBudget budget, String name, BudgetWindow month, Instant now, BigDecimal spent, BigDecimal limit) {
        long total = month.resetsAt().getEpochSecond() - month.start().getEpochSecond();
        long elapsed = now.getEpochSecond() - month.start().getEpochSecond();
        if (elapsed * 5 < total || spent.compareTo(limit) >= 0 || exists(budget, SpendAlertKind.PROJECTED, BudgetPeriod.MONTH, month.start())) {
            return List.of();
        }
        BigDecimal projected = spent.multiply(BigDecimal.valueOf(total)).divide(BigDecimal.valueOf(elapsed), 2, RoundingMode.HALF_UP);
        if (projected.compareTo(limit) <= 0) {
            return List.of();
        }
        return List.of(fire(budget, SpendAlertKind.PROJECTED, BudgetPeriod.MONTH, month.start(), spent, limit,
                name + " is on pace for " + dollars(projected) + " this month against a " + dollars(limit) + " budget (" + dollars(spent) + " so far).", name));
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
        SpendAlertDto dto = toDto(alert, unitName);
        notifier.notify(budget.getAccountId(), "spend.alert", "[tanso] " + kind.name().toLowerCase() + ": " + unitName, message, null, dto);
        log.info("Spend alert {} for unit {}: {}", kind, budget.getSpendUnitId(), message);
        return dto;
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
                .effectiveMonthlyCents(budget.effectiveMonthlyCents(now))
                .gatewaySpentCents(gatewaySpent(account, budget.getSpendUnitId(), month.start(), now))
                .bumpMonthlyCents(budget.getBumpMonthlyCents()).bumpExpiresAt(budget.getBumpExpiresAt()).bumpReason(budget.getBumpReason())
                .enforcementTarget(budget.getEnforcementTarget()).enforcedAt(budget.getEnforcedAt()).enforcementError(budget.getEnforcementError())
                .build();
    }

    /**
     * The proxy prices requests off its own model map, not our price book, and enforces
     * max_budget against that figure. Show it, or the card says $2 while the gateway says $40.
     */
    private BigDecimal gatewaySpent(UUID account, UUID unitId, Instant monthStart, Instant now) {
        List<SpendAttributionRule> rules = ruleRepository.findAllByAccountIdOrderByPriorityAscCreatedAtAsc(account).stream()
                .filter(r -> r.getSpendUnitId().equals(unitId) && r.getProvider() == VendorProvider.LITELLM).toList();
        if (rules.isEmpty()) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (VendorUsageBucket b : bucketRepository.findAllByAccountIdAndBucketStartGreaterThanEqualAndBucketStartLessThan(account, monthStart, now)) {
            if (b.getProvider() != VendorProvider.LITELLM || b.getSource() != VendorUsageSource.COST_API || b.getVendorCostCents() == null) {
                continue;
            }
            for (SpendAttributionRule r : rules) {
                String value = switch (r.getMatchKind()) {
                    case WORKSPACE_ID -> b.getWorkspaceId();
                    case API_KEY_ID -> b.getVendorApiKeyId();
                    case ACTOR -> b.getActorId();
                };
                if (r.getMatchValue().equals(value)) {
                    total = total.add(b.getVendorCostCents());
                    break;
                }
            }
        }
        return total;
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
