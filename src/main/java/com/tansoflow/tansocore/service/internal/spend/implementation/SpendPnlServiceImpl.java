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

import com.tansoflow.tansocore.entity.Feature;
import com.tansoflow.tansocore.entity.SpendUnit;
import com.tansoflow.tansocore.model.event.events.type.EventType;
import com.tansoflow.tansocore.model.spend.SpendAllocationReportDto;
import com.tansoflow.tansocore.model.spend.SpendOutcomeReportDto;
import com.tansoflow.tansocore.model.spend.SpendPnlReportDto;
import com.tansoflow.tansocore.model.spend.type.SpendUnitType;
import com.tansoflow.tansocore.repository.EventRepository;
import com.tansoflow.tansocore.repository.FeatureRepository;
import com.tansoflow.tansocore.repository.SpendUnitRepository;
import com.tansoflow.tansocore.service.internal.spend.OutcomeService;
import com.tansoflow.tansocore.service.internal.spend.SpendAllocationService;
import com.tansoflow.tansocore.service.internal.spend.SpendPnlService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.modules.build.enabled", havingValue = "true", matchIfMissing = true)
public class SpendPnlServiceImpl implements SpendPnlService {
    static final int DEFAULT_WINDOW_DAYS = 30;
    private static final List<EventType> REVENUE_EVENTS = List.of(EventType.CLIENT_TRACKED, EventType.ENTITLEMENT_CHECKED);

    private final SpendUnitRepository unitRepository;
    private final FeatureRepository featureRepository;
    private final EventRepository eventRepository;
    private final SpendAllocationService allocationService;
    private final OutcomeService outcomeService;

    @Override
    @Transactional(readOnly = true)
    public SpendPnlReportDto report(String accountId, LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC).plusDays(1);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_WINDOW_DAYS);
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("from must be before to");
        }
        UUID account = UUID.fromString(accountId);
        List<SpendUnit> all = unitRepository.findAllByAccountIdOrderByNameAsc(account);
        Map<UUID, SpendUnit> byId = new HashMap<>();
        for (SpendUnit u : all) {
            byId.put(u.getId(), u);
        }
        List<SpendUnit> projects = all.stream().filter(u -> u.getType() == SpendUnitType.PROJECT).toList();
        List<String> unlinked = new ArrayList<>();
        List<UUID> featureIds = new ArrayList<>();
        for (SpendUnit p : projects) {
            if (p.getFeatureId() == null) {
                unlinked.add(p.getName());
            } else {
                featureIds.add(p.getFeatureId());
            }
        }
        // Serve side: what the feature earned and cost to serve, dollars on the events → cents here.
        Map<UUID, BigDecimal[]> serve = new HashMap<>();
        if (!featureIds.isEmpty()) {
            // Only amounts stamped as currency (or unstamped legacy rows); a revenue in CREDITS is not dollars.
            for (Object[] row : eventRepository.sumRevenueAndCostByFeature(account, featureIds, REVENUE_EVENTS,
                    start.atStartOfDay(ZoneOffset.UTC).toInstant(), end.atStartOfDay(ZoneOffset.UTC).toInstant(),
                    com.tansoflow.tansocore.model.event.events.type.CostUnit.CURRENCY)) {
                serve.put((UUID) row[0], new BigDecimal[]{toCents((BigDecimal) row[1]), toCents((BigDecimal) row[2])});
            }
        }
        Map<UUID, Feature> features = new HashMap<>();
        for (UUID id : featureIds) {
            featureRepository.findByIdAndAccountId(id, account).ifPresent(f -> features.put(id, f));
        }
        // Build side: the project's attributed spend with descendants, and what it shipped.
        Map<String, BigDecimal> build = new HashMap<>();
        for (SpendAllocationReportDto.AllocationRow r : allocationService.allocate(accountId, start, end).getRows()) {
            build.put(r.getUnitId(), r.getTotalCents());
        }
        Map<String, Long> outcomes = new HashMap<>();
        for (SpendOutcomeReportDto.OutcomeRow r : outcomeService.report(accountId, start, end).getRows()) {
            outcomes.put(r.getUnitId(), r.getOutcomes());
        }
        List<SpendPnlReportDto.PnlRow> rows = new ArrayList<>();
        BigDecimal totalBuild = BigDecimal.ZERO;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalServe = BigDecimal.ZERO;
        for (SpendUnit p : projects) {
            if (p.getFeatureId() == null) {
                continue;
            }
            Feature f = features.get(p.getFeatureId());
            BigDecimal[] s = serve.getOrDefault(p.getFeatureId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal buildCents = build.getOrDefault(p.getId().toString(), BigDecimal.ZERO);
            long shipped = outcomes.getOrDefault(p.getId().toString(), 0L);
            BigDecimal serveMargin = s[0].subtract(s[1]);
            rows.add(SpendPnlReportDto.PnlRow.builder()
                    .unitId(p.getId().toString()).name(p.getName())
                    .featureId(p.getFeatureId().toString())
                    .featureKey(f == null ? null : f.getKey()).featureName(f == null ? null : f.getName())
                    .buildCents(buildCents).outcomes(shipped)
                    .revenueCents(s[0]).serveCostCents(s[1]).serveMarginCents(serveMargin)
                    .netCents(serveMargin.subtract(buildCents))
                    .buildPerOutcomeCents(shipped == 0 ? null : buildCents.divide(BigDecimal.valueOf(shipped), 2, RoundingMode.HALF_UP))
                    .build());
            if (hasLinkedProjectAncestor(p, byId)) {
                continue;   // its build cost is already inside the ancestor's totalCents
            }
            totalBuild = totalBuild.add(buildCents);
            totalRevenue = totalRevenue.add(s[0]);
            totalServe = totalServe.add(s[1]);
        }
        rows.sort(Comparator.comparing(SpendPnlReportDto.PnlRow::getNetCents).reversed());
        return SpendPnlReportDto.builder()
                .from(start).to(end).rows(rows).unlinkedProjects(unlinked)
                .totalBuildCents(totalBuild).totalRevenueCents(totalRevenue).totalServeCostCents(totalServe)
                .totalNetCents(totalRevenue.subtract(totalServe).subtract(totalBuild))
                .build();
    }

    private static boolean hasLinkedProjectAncestor(SpendUnit unit, Map<UUID, SpendUnit> byId) {
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        UUID parent = unit.getParentId();
        while (parent != null && seen.add(parent)) {
            SpendUnit p = byId.get(parent);
            if (p == null) {
                return false;
            }
            if (p.getType() == SpendUnitType.PROJECT && p.getFeatureId() != null) {
                return true;
            }
            parent = p.getParentId();
        }
        return false;
    }

    private static BigDecimal toCents(BigDecimal dollars) {
        return dollars == null ? BigDecimal.ZERO : dollars.movePointRight(2).setScale(2, RoundingMode.HALF_UP);
    }
}
