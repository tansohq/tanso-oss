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

import com.tansoflow.tansocore.entity.ModelPricing;
import com.tansoflow.tansocore.entity.VendorUsageBucket;
import com.tansoflow.tansocore.model.spend.PriceBookModelDto;
import com.tansoflow.tansocore.model.spend.SpendRouteSimulationDto;
import com.tansoflow.tansocore.model.spend.SpendSavingsReportDto;
import com.tansoflow.tansocore.model.spend.request.SpendRouteSimulationRequest;
import com.tansoflow.tansocore.model.spend.type.VendorProvider;
import com.tansoflow.tansocore.model.spend.type.VendorUsageSource;
import com.tansoflow.tansocore.repository.ModelPricingRepository;
import com.tansoflow.tansocore.repository.VendorUsageBucketRepository;
import com.tansoflow.tansocore.service.internal.spend.SpendSavingsService;
import com.tansoflow.tansocore.util.ModelPricingResolver;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.modules.build.enabled", havingValue = "true", matchIfMissing = true)
public class SpendSavingsServiceImpl implements SpendSavingsService {
    static final int DEFAULT_WINDOW_DAYS = 30;
    static final int MAX_SPAN_DAYS = 366;
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    private final VendorUsageBucketRepository bucketRepository;
    private final ModelPricingRepository modelPricingRepository;
    private final ModelPricingResolver pricingResolver;

    @Override
    @Transactional(readOnly = true)
    public SpendSavingsReportDto savings(String accountId, LocalDate from, LocalDate to) {
        LocalDate[] w = window(from, to);
        Map<String, long[]> tokens = new LinkedHashMap<>();
        Map<String, VendorProvider> providers = new LinkedHashMap<>();
        for (VendorUsageBucket b : load(accountId, w[0], w[1])) {
            String key = b.getProvider() + "|" + b.getModel();
            long[] t = tokens.computeIfAbsent(key, k -> new long[4]);
            t[0] += b.getUncachedInputTokens();
            t[1] += b.getCacheReadTokens();
            t[2] += b.getCacheCreationTokens();
            t[3] += b.getOutputTokens();
            providers.putIfAbsent(key, b.getProvider());
        }
        List<SpendSavingsReportDto.SavingsRow> rows = new ArrayList<>();
        TreeSet<String> unpriced = new TreeSet<>();
        long[] total = new long[4];
        BigDecimal totalInput = BigDecimal.ZERO;
        BigDecimal totalNoCache = BigDecimal.ZERO;
        boolean allRatesKnown = true;
        for (Map.Entry<String, long[]> e : tokens.entrySet()) {
            String model = e.getKey().endsWith("|null") ? null : e.getKey().substring(e.getKey().indexOf('|') + 1);
            long[] t = e.getValue();
            ModelPricingResolver.ResolvedPricing resolved = model == null ? null : pricingResolver.resolve(model);
            SpendSavingsReportDto.SavingsRow.SavingsRowBuilder row = SpendSavingsReportDto.SavingsRow.builder()
                    .provider(providers.get(e.getKey())).model(model)
                    .uncachedInputTokens(t[0]).cacheReadTokens(t[1]).cacheCreationTokens(t[2]).outputTokens(t[3])
                    .cacheShare(share(t));
            if (resolved == null) {
                unpriced.add(model == null ? "(no model)" : model);
                rows.add(row.priced(false).cacheRatesKnown(false)
                        .inputCostCents(BigDecimal.ZERO).noCacheCostCents(BigDecimal.ZERO).savedCents(BigDecimal.ZERO).build());
                continue;
            }
            ModelPricing p = resolved.pricing();
            boolean known = p.getCacheReadCostPerMillion() != null && p.getCacheWriteCostPerMillion() != null;
            BigDecimal input = inputSideCents(p, t[0], t[1], t[2]);
            BigDecimal noCache = cents(t[0] + t[1] + t[2], p.getInputCostPerMillion());
            rows.add(row.priced(true).cacheRatesKnown(known)
                    .inputCostCents(input).noCacheCostCents(noCache).savedCents(noCache.subtract(input)).build());
            for (int i = 0; i < 4; i++) {
                total[i] += t[i];
            }
            totalInput = totalInput.add(input);
            totalNoCache = totalNoCache.add(noCache);
            allRatesKnown &= known;
        }
        rows.sort(Comparator.comparing(SpendSavingsReportDto.SavingsRow::getSavedCents).reversed());
        return SpendSavingsReportDto.builder()
                .from(w[0]).to(w[1])
                .totals(SpendSavingsReportDto.SavingsRow.builder()
                        .uncachedInputTokens(total[0]).cacheReadTokens(total[1]).cacheCreationTokens(total[2]).outputTokens(total[3])
                        .cacheShare(share(total))
                        .inputCostCents(totalInput).noCacheCostCents(totalNoCache).savedCents(totalNoCache.subtract(totalInput))
                        .priced(true).cacheRatesKnown(allRatesKnown).build())
                .byModel(rows)
                .unpricedModels(new ArrayList<>(unpriced))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public SpendRouteSimulationDto simulate(String accountId, SpendRouteSimulationRequest request) {
        LocalDate[] w = window(request.getFrom(), request.getTo());
        ModelPricingResolver.ResolvedPricing target = pricingResolver.resolve(request.getToModel().trim());
        if (target == null) {
            throw new IllegalArgumentException("No price for " + request.getToModel() + " in the price book — add it under model pricing first");
        }
        String scope = request.getWorkspaceId() == null || request.getWorkspaceId().isBlank() ? null : request.getWorkspaceId().trim();
        long[] t = new long[4];
        long requests = 0;
        boolean anyRequests = false;
        for (VendorUsageBucket b : load(accountId, w[0], w[1])) {
            if (!request.getFromModel().trim().equalsIgnoreCase(b.getModel())) {
                continue;
            }
            if (scope != null && !scope.equals(b.getWorkspaceId())) {
                continue;
            }
            t[0] += b.getUncachedInputTokens();
            t[1] += b.getCacheReadTokens();
            t[2] += b.getCacheCreationTokens();
            t[3] += b.getOutputTokens();
            if (b.getRequests() != null) {
                anyRequests = true;
                requests += b.getRequests();
            }
        }
        List<String> caveats = new ArrayList<>();
        ModelPricingResolver.ResolvedPricing source = pricingResolver.resolve(request.getFromModel().trim());
        BigDecimal current;
        if (source == null) {
            current = BigDecimal.ZERO;
            caveats.add(request.getFromModel() + " is not in the price book, so the current cost reads as zero.");
        } else {
            current = allCents(source.pricing(), t);
            if (source.fuzzyMatched()) {
                caveats.add("Current cost priced by fuzzy match on " + source.pricing().getModel() + ".");
            }
        }
        ModelPricing tp = target.pricing();
        BigDecimal simulated = allCents(tp, t);
        if (target.fuzzyMatched()) {
            caveats.add("Target priced by fuzzy match on " + tp.getModel() + ".");
        }
        if (t[1] + t[2] > 0 && (tp.getCacheReadCostPerMillion() == null || tp.getCacheWriteCostPerMillion() == null)) {
            caveats.add("No cache rates for " + tp.getModel() + ": cached tokens are priced at its input rate, which overstates the target's cost.");
        }
        if (t[0] + t[1] + t[2] + t[3] == 0) {
            caveats.add("No " + request.getFromModel() + " traffic in the window" + (scope == null ? "." : " for workspace " + scope + "."));
        }
        caveats.add("Token counts are carried over as-is; a different tokenizer would change them.");
        caveats.add("Quality and latency are not modelled — this is what the bill would have been, not whether the answers would have been as good.");
        return SpendRouteSimulationDto.builder()
                .from(w[0]).to(w[1])
                .fromModel(request.getFromModel().trim()).toModel(tp.getModel()).workspaceId(scope)
                .uncachedInputTokens(t[0]).cacheReadTokens(t[1]).cacheCreationTokens(t[2]).outputTokens(t[3])
                .requests(anyRequests ? requests : null)
                .currentCents(current).simulatedCents(simulated).deltaCents(simulated.subtract(current))
                .caveats(caveats)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PriceBookModelDto> models() {
        List<PriceBookModelDto> out = new ArrayList<>();
        for (ModelPricing p : modelPricingRepository.findAll()) {
            out.add(PriceBookModelDto.builder().provider(p.getProvider()).model(p.getModel())
                    .inputCostPerMillion(p.getInputCostPerMillion()).outputCostPerMillion(p.getOutputCostPerMillion())
                    .cacheReadCostPerMillion(p.getCacheReadCostPerMillion()).cacheWriteCostPerMillion(p.getCacheWriteCostPerMillion())
                    .build());
        }
        out.sort(Comparator.comparing(PriceBookModelDto::getProvider).thenComparing(PriceBookModelDto::getModel));
        return out;
    }

    private List<VendorUsageBucket> load(String accountId, LocalDate from, LocalDate to) {
        return bucketRepository.findAllByAccountIdAndBucketStartGreaterThanEqualAndBucketStartLessThan(
                        UUID.fromString(accountId), from.atStartOfDay(ZoneOffset.UTC).toInstant(), to.atStartOfDay(ZoneOffset.UTC).toInstant())
                .stream().filter(b -> b.getSource() == VendorUsageSource.USAGE_API).toList();
    }

    private static LocalDate[] window(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC).plusDays(1);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_WINDOW_DAYS);
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("from must be before to");
        }
        if (start.plusDays(MAX_SPAN_DAYS).isBefore(end)) {
            throw new IllegalArgumentException("Window is longer than " + MAX_SPAN_DAYS + " days");
        }
        return new LocalDate[]{start, end};
    }

    static BigDecimal share(long[] t) {
        long input = t[0] + t[1] + t[2];
        return input == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(t[1]).divide(BigDecimal.valueOf(input), 4, RoundingMode.HALF_UP);
    }

    static BigDecimal inputSideCents(ModelPricing p, long uncached, long cacheRead, long cacheWrite) {
        BigDecimal in = p.getInputCostPerMillion();
        return cents(uncached, in)
                .add(cents(cacheRead, p.getCacheReadCostPerMillion() != null ? p.getCacheReadCostPerMillion() : in))
                .add(cents(cacheWrite, p.getCacheWriteCostPerMillion() != null ? p.getCacheWriteCostPerMillion() : in));
    }

    static BigDecimal allCents(ModelPricing p, long[] t) {
        return inputSideCents(p, t[0], t[1], t[2]).add(cents(t[3], p.getOutputCostPerMillion()));
    }

    static BigDecimal cents(long tokens, BigDecimal ratePerMillionDollars) {
        if (tokens == 0 || ratePerMillionDollars == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(tokens).multiply(ratePerMillionDollars).multiply(BigDecimal.valueOf(100)).divide(MILLION, 6, RoundingMode.HALF_UP);
    }
}
