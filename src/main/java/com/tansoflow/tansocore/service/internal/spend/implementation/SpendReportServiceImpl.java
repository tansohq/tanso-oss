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

import com.tansoflow.tansocore.entity.VendorInvoice;
import com.tansoflow.tansocore.entity.VendorUsageBucket;
import com.tansoflow.tansocore.model.spend.SpendReconcileReportDto;
import com.tansoflow.tansocore.model.spend.SpendUsageReportDto;
import com.tansoflow.tansocore.model.spend.type.VendorProvider;
import com.tansoflow.tansocore.model.spend.type.VendorUsageSource;
import com.tansoflow.tansocore.repository.VendorInvoiceRepository;
import com.tansoflow.tansocore.repository.VendorUsageBucketRepository;
import com.tansoflow.tansocore.service.internal.spend.SpendReportService;
import com.tansoflow.tansocore.service.internal.spend.SpendSettingsService;
import com.tansoflow.tansocore.util.VendorCostEstimator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.modules.build.enabled", havingValue = "true", matchIfMissing = true)
public class SpendReportServiceImpl implements SpendReportService {
    static final int DEFAULT_WINDOW_DAYS = 30;
    /** Buckets are aggregated in memory; a year is the most a report page should ever ask for. */
    static final int MAX_SPAN_DAYS = 366;

    private final VendorUsageBucketRepository bucketRepository;
    private final VendorInvoiceRepository invoiceRepository;
    private final VendorCostEstimator estimator;
    private final SpendSettingsService settingsService;

    @Override
    @Transactional(readOnly = true)
    public SpendUsageReportDto usage(String accountId, LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC).plusDays(1);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_WINDOW_DAYS);
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("from must be before to");
        }
        if (start.plusDays(MAX_SPAN_DAYS).isBefore(end)) {
            throw new IllegalArgumentException("Window is longer than " + MAX_SPAN_DAYS + " days");
        }
        List<VendorUsageBucket> buckets = load(accountId, start, end);

        Map<String, SpendUsageReportDto.ModelRow.ModelRowBuilder> byModel = new LinkedHashMap<>();
        Map<String, long[]> modelTokens = new LinkedHashMap<>();
        java.util.Set<String> modelHasRequests = new java.util.HashSet<>();
        Map<String, BigDecimal> vendorCostByModel = new LinkedHashMap<>();
        Map<LocalDate, BigDecimal[]> byDay = new TreeMap<>();
        Map<LocalDate, long[]> dayTokens = new TreeMap<>();
        Map<String, long[]> actorTokens = new LinkedHashMap<>();
        Map<String, BigDecimal[]> actorCost = new LinkedHashMap<>();
        Map<String, VendorProvider> actorProvider = new LinkedHashMap<>();
        long[] totals = new long[5];
        boolean anyRequests = false;
        java.util.Set<String> cacheRatesUnknown = new java.util.HashSet<>();
        BigDecimal vendorTotal = BigDecimal.ZERO;
        BigDecimal meteredTotal = BigDecimal.ZERO;
        TreeSet<String> unpriced = new TreeSet<>();

        for (VendorUsageBucket b : buckets) {
            LocalDate day = b.getBucketStart().atZone(ZoneOffset.UTC).toLocalDate();
            switch (b.getSource()) {
                case USAGE_API -> {
                    VendorCostEstimator.Estimate est = estimator.estimate(b.getModel(),
                            b.getUncachedInputTokens(), b.getCacheReadTokens(), b.getCacheCreationTokens(), b.getOutputTokens());
                    if (!est.priced() && b.getModel() != null) {
                        unpriced.add(b.getModel());
                    }
                    String key = b.getProvider() + "|" + b.getModel();
                    long[] mt = modelTokens.computeIfAbsent(key, k -> new long[5]);
                    add(mt, b);
                    add(totals, b);
                    if (b.getRequests() != null) {
                        anyRequests = true;
                        modelHasRequests.add(key);
                    }
                    if (est.priced() && !est.cacheRatesKnown()) {
                        cacheRatesUnknown.add(key);
                    }
                    byModel.computeIfAbsent(key, k -> SpendUsageReportDto.ModelRow.builder()
                            .provider(b.getProvider()).model(b.getModel()).priced(est.priced()).meteredCostCents(BigDecimal.ZERO));
                    SpendUsageReportDto.ModelRow.ModelRowBuilder row = byModel.get(key);
                    row.meteredCostCents(row.build().getMeteredCostCents().add(est.cents()));
                    meteredTotal = meteredTotal.add(est.cents());
                    BigDecimal[] d = byDay.computeIfAbsent(day, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                    d[0] = d[0].add(est.cents());
                    long[] dt = dayTokens.computeIfAbsent(day, k -> new long[5]);
                    add(dt, b);
                    if (b.getActorId() != null) {
                        String actorKey = b.getProvider() + "|" + b.getActorId();
                        add(actorTokens.computeIfAbsent(actorKey, k -> new long[5]), b);
                        BigDecimal[] ac = actorCost.computeIfAbsent(actorKey, k -> new BigDecimal[]{BigDecimal.ZERO, null});
                        ac[0] = ac[0].add(est.cents());
                        actorProvider.put(actorKey, b.getProvider());
                    }
                }
                case COST_API -> {
                    BigDecimal cents = b.getVendorCostCents() == null ? BigDecimal.ZERO : b.getVendorCostCents();
                    vendorTotal = vendorTotal.add(cents);
                    BigDecimal[] d = byDay.computeIfAbsent(day, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                    d[1] = d[1].add(cents);
                    if (b.getModel() != null) {
                        vendorCostByModel.merge(b.getProvider() + "|" + b.getModel(), cents, BigDecimal::add);
                    }
                }
                case CLAUDE_CODE_API -> {
                    String actorKey = b.getProvider() + "|" + b.getActorId();
                    add(actorTokens.computeIfAbsent(actorKey, k -> new long[5]), b);
                    VendorCostEstimator.Estimate est = estimator.estimate(b.getModel(),
                            b.getUncachedInputTokens(), b.getCacheReadTokens(), b.getCacheCreationTokens(), b.getOutputTokens());
                    BigDecimal[] ac = actorCost.computeIfAbsent(actorKey, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                    ac[0] = ac[0].add(est.cents());
                    if (b.getVendorCostCents() != null) {
                        ac[1] = (ac[1] == null ? BigDecimal.ZERO : ac[1]).add(b.getVendorCostCents());
                    }
                    actorProvider.put(actorKey, b.getProvider());
                }
            }
        }

        List<SpendUsageReportDto.ModelRow> modelRows = new ArrayList<>();
        for (Map.Entry<String, SpendUsageReportDto.ModelRow.ModelRowBuilder> e : byModel.entrySet()) {
            long[] t = modelTokens.get(e.getKey());
            modelRows.add(e.getValue()
                    .uncachedInputTokens(t[0]).cacheReadTokens(t[1]).cacheCreationTokens(t[2]).outputTokens(t[3])
                    .requests(modelHasRequests.contains(e.getKey()) ? t[4] : null)
                    .cacheRatesKnown(!cacheRatesUnknown.contains(e.getKey()))
                    .vendorCostCents(vendorCostByModel.get(e.getKey()))
                    .build());
        }
        // Cost the vendor attributes to something no usage row names (OpenAI "Image models",
        // "Fine-tuning", …) still belongs in the table, or the column under-sums its own total.
        for (Map.Entry<String, BigDecimal> e : vendorCostByModel.entrySet()) {
            if (!byModel.containsKey(e.getKey())) {
                int bar = e.getKey().indexOf('|');
                modelRows.add(SpendUsageReportDto.ModelRow.builder()
                        .provider(VendorProvider.valueOf(e.getKey().substring(0, bar)))
                        .model(e.getKey().substring(bar + 1))
                        .meteredCostCents(BigDecimal.ZERO).vendorCostCents(e.getValue())
                        .priced(true).cacheRatesKnown(true)
                        .build());
            }
        }
        modelRows.sort(Comparator.comparing(SpendUsageReportDto.ModelRow::getMeteredCostCents).reversed());

        List<SpendUsageReportDto.DayRow> dayRows = new ArrayList<>();
        for (Map.Entry<LocalDate, BigDecimal[]> e : byDay.entrySet()) {
            long[] t = dayTokens.getOrDefault(e.getKey(), new long[5]);
            dayRows.add(SpendUsageReportDto.DayRow.builder().date(e.getKey())
                    .totalTokens(t[0] + t[1] + t[2] + t[3])
                    .meteredCostCents(e.getValue()[0]).vendorCostCents(e.getValue()[1]).build());
        }

        List<SpendUsageReportDto.ActorRow> actorRows = new ArrayList<>();
        for (Map.Entry<String, long[]> e : actorTokens.entrySet()) {
            long[] t = e.getValue();
            BigDecimal[] c = actorCost.get(e.getKey());
            actorRows.add(SpendUsageReportDto.ActorRow.builder()
                    .provider(actorProvider.get(e.getKey()))
                    .actor(e.getKey().substring(e.getKey().indexOf('|') + 1))
                    .totalTokens(t[0] + t[1] + t[2] + t[3]).sessions(t[4])
                    .meteredCostCents(c[0]).vendorCostCents(c[1]).build());
        }
        actorRows.sort(Comparator.comparing(SpendUsageReportDto.ActorRow::getMeteredCostCents).reversed());
        if (!settingsService.personLevelEnabled(accountId)) {
            // Names are a monitoring capability the operator has not switched on.
            actorRows = List.of();
        }

        return SpendUsageReportDto.builder()
                .from(start).to(end)
                .totals(SpendUsageReportDto.Totals.builder()
                        .uncachedInputTokens(totals[0]).cacheReadTokens(totals[1]).cacheCreationTokens(totals[2])
                        .outputTokens(totals[3]).requests(anyRequests ? totals[4] : null)
                        .vendorCostCents(vendorTotal).meteredCostCents(meteredTotal).build())
                .byModel(modelRows).byDay(dayRows).byActor(actorRows)
                .unpricedModels(new ArrayList<>(unpriced))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public SpendReconcileReportDto reconcile(String accountId, LocalDate from, LocalDate to) {
        LocalDate start;
        LocalDate endInclusive;
        if (from == null || to == null) {
            LocalDate firstOfThisMonth = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
            start = firstOfThisMonth.minusMonths(1);
            endInclusive = firstOfThisMonth.minusDays(1);
        } else {
            start = from;
            endInclusive = to;
        }
        if (endInclusive.isBefore(start)) {
            throw new IllegalArgumentException("to is before from");
        }
        if (start.plusDays(MAX_SPAN_DAYS).isBefore(endInclusive)) {
            throw new IllegalArgumentException("Window is longer than " + MAX_SPAN_DAYS + " days");
        }
        List<VendorUsageBucket> buckets = load(accountId, start, endInclusive.plusDays(1));
        Map<VendorProvider, BigDecimal[]> sums = new EnumMap<>(VendorProvider.class);
        Map<VendorProvider, Boolean> estimateFlag = new EnumMap<>(VendorProvider.class);
        for (VendorUsageBucket b : buckets) {
            BigDecimal[] s = sums.computeIfAbsent(b.getProvider(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            if (b.getSource() == VendorUsageSource.USAGE_API) {
                VendorCostEstimator.Estimate est = estimator.estimate(b.getModel(),
                        b.getUncachedInputTokens(), b.getCacheReadTokens(), b.getCacheCreationTokens(), b.getOutputTokens());
                s[0] = s[0].add(est.cents());
                if (!est.priced() || !est.cacheRatesKnown()) {
                    estimateFlag.put(b.getProvider(), true);
                }
            } else if (b.getSource() == VendorUsageSource.COST_API && b.getVendorCostCents() != null) {
                s[1] = s[1].add(b.getVendorCostCents());
            }
        }
        Map<VendorProvider, BigDecimal> invoiced = new EnumMap<>(VendorProvider.class);
        Map<VendorProvider, Integer> invoiceCount = new EnumMap<>(VendorProvider.class);
        for (VendorInvoice inv : invoiceRepository.findOverlapping(UUID.fromString(accountId), start, endInclusive)) {
            // Only invoices that sit inside the window count; a partial overlap would need pro-rating we do not pretend to do.
            if (!inv.getPeriodStart().isBefore(start) && !inv.getPeriodEnd().isAfter(endInclusive)) {
                invoiced.merge(inv.getProvider(), inv.getTotalCents(), BigDecimal::add);
                invoiceCount.merge(inv.getProvider(), 1, Integer::sum);
                sums.computeIfAbsent(inv.getProvider(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            }
        }
        List<SpendReconcileReportDto.ReconcileRow> rows = new ArrayList<>();
        for (Map.Entry<VendorProvider, BigDecimal[]> e : sums.entrySet()) {
            BigDecimal metered = e.getValue()[0];
            BigDecimal vendor = e.getValue()[1];
            BigDecimal inv = invoiced.get(e.getKey());
            rows.add(SpendReconcileReportDto.ReconcileRow.builder()
                    .provider(e.getKey())
                    .meteredCents(metered)
                    .meteredIsEstimate(estimateFlag.getOrDefault(e.getKey(), false))
                    .vendorReportedCents(vendor)
                    .invoicedCents(inv)
                    .invoiceCount(invoiceCount.getOrDefault(e.getKey(), 0))
                    .meteredVsVendorCents(metered.subtract(vendor))
                    .vendorVsInvoiceCents(inv == null ? null : vendor.subtract(inv))
                    .build());
        }
        return SpendReconcileReportDto.builder().from(start).to(endInclusive).rows(rows).build();
    }

    private List<VendorUsageBucket> load(String accountId, LocalDate from, LocalDate toExclusive) {
        return bucketRepository.findAllByAccountIdAndBucketStartGreaterThanEqualAndBucketStartLessThan(
                UUID.fromString(accountId),
                from.atStartOfDay(ZoneOffset.UTC).toInstant(),
                toExclusive.atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    private static void add(long[] acc, VendorUsageBucket b) {
        acc[0] += b.getUncachedInputTokens();
        acc[1] += b.getCacheReadTokens();
        acc[2] += b.getCacheCreationTokens();
        acc[3] += b.getOutputTokens();
        acc[4] += b.getRequests() == null ? 0 : b.getRequests();
    }
}
