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
import com.tansoflow.tansocore.model.spend.SpendRouteSimulationDto;
import com.tansoflow.tansocore.model.spend.SpendSavingsReportDto;
import com.tansoflow.tansocore.model.spend.request.SpendRouteSimulationRequest;
import com.tansoflow.tansocore.model.spend.type.VendorProvider;
import com.tansoflow.tansocore.model.spend.type.VendorUsageSource;
import com.tansoflow.tansocore.repository.ModelPricingRepository;
import com.tansoflow.tansocore.repository.VendorUsageBucketRepository;
import com.tansoflow.tansocore.util.ModelPricingResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpendSavingsServiceImplTest {
    @Mock private VendorUsageBucketRepository bucketRepository;
    @Mock private ModelPricingRepository modelPricingRepository;
    @Mock private ModelPricingResolver resolver;

    private final UUID account = UUID.randomUUID();
    private SpendSavingsServiceImpl service;
    private ModelPricing sonnet;
    private ModelPricing haiku;
    private ModelPricing gpt;

    @BeforeEach
    void setUp() {
        service = new SpendSavingsServiceImpl(bucketRepository, modelPricingRepository, resolver);
        sonnet = pricing("claude-sonnet-4-5", "3", "15", "0.3", "3.75");
        haiku = pricing("claude-haiku-4-5", "1", "5", "0.1", "1.25");
        gpt = pricing("gpt-4o", "2.5", "10", null, null);
        lenient().when(resolver.resolve("claude-sonnet-4-5")).thenReturn(new ModelPricingResolver.ResolvedPricing(sonnet, false));
        lenient().when(resolver.resolve("claude-haiku-4-5")).thenReturn(new ModelPricingResolver.ResolvedPricing(haiku, false));
        lenient().when(resolver.resolve("gpt-4o")).thenReturn(new ModelPricingResolver.ResolvedPricing(gpt, false));
        lenient().when(resolver.resolve("mystery-9")).thenReturn(null);
        lenient().when(bucketRepository.findAllByAccountIdAndBucketStartGreaterThanEqualAndBucketStartLessThan(eq(account), any(), any()))
                .thenReturn(List.of(
                        bucket("claude-sonnet-4-5", "backend", 1_000_000, 4_000_000, 500_000, 200_000, 100L),
                        bucket("claude-sonnet-4-5", "support", 1_000_000, 0, 0, 100_000, 20L),
                        bucket("gpt-4o", "backend", 2_000_000, 1_000_000, 0, 300_000, null),
                        bucket("mystery-9", null, 10, 0, 0, 5, null),
                        cost("claude-sonnet-4-5")));
    }

    private static ModelPricing pricing(String model, String in, String out, String read, String write) {
        ModelPricing p = new ModelPricing();
        p.setModel(model);
        p.setProvider("anthropic");
        p.setInputCostPerMillion(new BigDecimal(in));
        p.setOutputCostPerMillion(new BigDecimal(out));
        p.setCacheReadCostPerMillion(read == null ? null : new BigDecimal(read));
        p.setCacheWriteCostPerMillion(write == null ? null : new BigDecimal(write));
        return p;
    }

    private VendorUsageBucket bucket(String model, String workspace, long uncached, long read, long write, long output, Long requests) {
        VendorUsageBucket b = new VendorUsageBucket();
        b.setAccountId(account);
        b.setProvider(model.startsWith("gpt") ? VendorProvider.OPENAI : VendorProvider.ANTHROPIC);
        b.setSource(VendorUsageSource.USAGE_API);
        b.setBucketStart(Instant.parse("2026-08-20T00:00:00Z"));
        b.setBucketEnd(Instant.parse("2026-08-21T00:00:00Z"));
        b.setModel(model);
        b.setWorkspaceId(workspace);
        b.setUncachedInputTokens(uncached);
        b.setCacheReadTokens(read);
        b.setCacheCreationTokens(write);
        b.setOutputTokens(output);
        b.setRequests(requests);
        return b;
    }

    private VendorUsageBucket cost(String model) {
        VendorUsageBucket b = bucket(model, null, 0, 0, 0, 0, null);
        b.setSource(VendorUsageSource.COST_API);
        b.setVendorCostCents(new BigDecimal("99999"));
        return b;
    }

    @Test
    void cacheSavingsAreTheInputSideBillAgainstTheSameTokensUncached() {
        SpendSavingsReportDto r = service.savings(account.toString(), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 26));
        SpendSavingsReportDto.SavingsRow s = r.getByModel().stream().filter(x -> "claude-sonnet-4-5".equals(x.getModel())).findFirst().orElseThrow();
        // 2M uncached @ $3 = $6.00; 4M read @ $0.30 = $1.20; 0.5M write @ $3.75 = $1.875 → $9.075 billed
        assertEquals(0, new BigDecimal("907.5").compareTo(s.getInputCostCents()), s.getInputCostCents().toPlainString());
        // 6.5M all @ $3 = $19.50 with no cache
        assertEquals(0, new BigDecimal("1950").compareTo(s.getNoCacheCostCents()));
        assertEquals(0, new BigDecimal("1042.5").compareTo(s.getSavedCents()));
        assertEquals(0, new BigDecimal("0.6154").compareTo(s.getCacheShare()), s.getCacheShare().toPlainString());
        assertTrue(s.isCacheRatesKnown());

        SpendSavingsReportDto.SavingsRow g = r.getByModel().stream().filter(x -> "gpt-4o".equals(x.getModel())).findFirst().orElseThrow();
        assertFalse(g.isCacheRatesKnown(), "no cache rates → assumed input rate");
        assertEquals(0, BigDecimal.ZERO.compareTo(g.getSavedCents()));

        assertEquals(List.of("mystery-9"), r.getUnpricedModels());
        assertEquals("claude-sonnet-4-5", r.getByModel().get(0).getModel(), "biggest saving first");
        assertEquals(0, new BigDecimal("1042.5").compareTo(r.getTotals().getSavedCents()));
        assertFalse(r.getTotals().isCacheRatesKnown());
    }

    @Test
    void simulationRepricesTheSameTokensAtTheTargetAndScopesByWorkspace() {
        SpendRouteSimulationRequest req = new SpendRouteSimulationRequest();
        req.setFrom(LocalDate.of(2026, 8, 1));
        req.setTo(LocalDate.of(2026, 8, 26));
        req.setFromModel("claude-sonnet-4-5");
        req.setToModel("claude-haiku-4-5");
        req.setWorkspaceId("backend");
        SpendRouteSimulationDto d = service.simulate(account.toString(), req);
        assertEquals(1_000_000, d.getUncachedInputTokens());
        assertEquals(4_000_000, d.getCacheReadTokens());
        assertEquals(100L, d.getRequests());
        // sonnet: 1M@3 + 4M@0.3 + 0.5M@3.75 + 0.2M@15 = 3 + 1.2 + 1.875 + 3 = $9.075
        assertEquals(0, new BigDecimal("907.5").compareTo(d.getCurrentCents()));
        // haiku: 1 + 0.4 + 0.625 + 1 = $3.025
        assertEquals(0, new BigDecimal("302.5").compareTo(d.getSimulatedCents()));
        assertEquals(0, new BigDecimal("-605").compareTo(d.getDeltaCents()));
        assertTrue(d.getCaveats().stream().anyMatch(c -> c.contains("Quality")));
        assertTrue(d.getCaveats().stream().noneMatch(c -> c.contains("No cache rates")));

        req.setWorkspaceId(null);
        req.setToModel("gpt-4o");
        d = service.simulate(account.toString(), req);
        assertEquals(2_000_000, d.getUncachedInputTokens(), "both workspaces");
        assertTrue(d.getCaveats().stream().anyMatch(c -> c.contains("No cache rates for gpt-4o")));

        req.setToModel("mystery-9");
        assertThrows(IllegalArgumentException.class, () -> service.simulate(account.toString(), req));
    }

    @Test
    void emptyTrafficSaysSo() {
        SpendRouteSimulationRequest req = new SpendRouteSimulationRequest();
        req.setFrom(LocalDate.of(2026, 8, 1));
        req.setTo(LocalDate.of(2026, 8, 26));
        req.setFromModel("gpt-4o");
        req.setToModel("claude-haiku-4-5");
        req.setWorkspaceId("nowhere");
        SpendRouteSimulationDto d = service.simulate(account.toString(), req);
        assertEquals(0, BigDecimal.ZERO.compareTo(d.getCurrentCents()));
        assertNull(d.getRequests());
        assertTrue(d.getCaveats().stream().anyMatch(c -> c.contains("No gpt-4o traffic")));
    }
}
