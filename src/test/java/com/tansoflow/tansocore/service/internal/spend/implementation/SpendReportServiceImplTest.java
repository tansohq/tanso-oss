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
import com.tansoflow.tansocore.util.VendorCostEstimator;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpendReportServiceImplTest {

    @Mock
    private VendorUsageBucketRepository bucketRepository;
    @Mock
    private VendorInvoiceRepository invoiceRepository;
    @Mock
    private VendorCostEstimator estimator;

    private SpendReportServiceImpl service;
    private final UUID accountId = UUID.randomUUID();
    private final Instant day1 = Instant.parse("2026-07-01T00:00:00Z");
    private final Instant day2 = Instant.parse("2026-07-02T00:00:00Z");

    @BeforeEach
    void setUp() {
        service = new SpendReportServiceImpl(bucketRepository, invoiceRepository, estimator);
        // sonnet: 1 cent per 1000 tokens of any class, cache rates known; mystery: unpriced
        lenient().when(estimator.estimate(eq("claude-sonnet-4-5"), anyLong(), anyLong(), anyLong(), anyLong()))
                .thenAnswer(inv -> new VendorCostEstimator.Estimate(
                        BigDecimal.valueOf((inv.<Long>getArgument(1) + inv.<Long>getArgument(2) + inv.<Long>getArgument(3) + inv.<Long>getArgument(4)) / 1000L), true, true));
        lenient().when(estimator.estimate(eq("mystery"), anyLong(), anyLong(), anyLong(), anyLong()))
                .thenReturn(VendorCostEstimator.Estimate.UNPRICED);
    }

    private VendorUsageBucket bucket(VendorUsageSource source, Instant start, String model, String actor,
                                     long in, long cacheRead, long cacheWrite, long out, Long requests, String vendorCents) {
        VendorUsageBucket b = new VendorUsageBucket();
        b.setAccountId(accountId);
        b.setConnectionId(UUID.randomUUID());
        b.setProvider(VendorProvider.ANTHROPIC);
        b.setSource(source);
        b.setBucketStart(start);
        b.setBucketEnd(start.plusSeconds(86400));
        b.setModel(model);
        b.setActorId(actor);
        b.setUncachedInputTokens(in);
        b.setCacheReadTokens(cacheRead);
        b.setCacheCreationTokens(cacheWrite);
        b.setOutputTokens(out);
        b.setRequests(requests);
        b.setVendorCostCents(vendorCents == null ? null : new BigDecimal(vendorCents));
        return b;
    }

    @Test
    void usageSeparatesTokensCostsAndActorsAndFlagsUnpricedModels() {
        when(bucketRepository.findAllByAccountIdAndBucketStartGreaterThanEqualAndBucketStartLessThan(eq(accountId), any(), any()))
                .thenReturn(List.of(
                        bucket(VendorUsageSource.USAGE_API, day1, "claude-sonnet-4-5", null, 10_000, 2_000, 1_000, 3_000, 7L, null),
                        bucket(VendorUsageSource.USAGE_API, day2, "claude-sonnet-4-5", null, 4_000, 0, 0, 1_000, 3L, null),
                        bucket(VendorUsageSource.USAGE_API, day2, "mystery", null, 500, 0, 0, 500, 1L, null),
                        bucket(VendorUsageSource.COST_API, day1, "claude-sonnet-4-5", null, 0, 0, 0, 0, null, "18.5"),
                        bucket(VendorUsageSource.COST_API, day2, null, null, 0, 0, 0, 0, null, "6"),
                        bucket(VendorUsageSource.CLAUDE_CODE_API, day1, "claude-sonnet-4-5", "dev@acme.test", 8_000, 0, 0, 2_000, 4L, "12")));

        SpendUsageReportDto r = service.usage(accountId.toString(), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3));

        // totals: USAGE_API only — Claude Code rows are the same traffic seen per person
        assertEquals(14_500, r.getTotals().getUncachedInputTokens());
        assertEquals(4_500, r.getTotals().getOutputTokens());
        assertEquals(11, r.getTotals().getRequests());
        assertEquals(0, new BigDecimal("21").compareTo(r.getTotals().getMeteredCostCents())); // 16 + 5 + 0
        assertEquals(0, new BigDecimal("24.5").compareTo(r.getTotals().getVendorCostCents()));
        assertEquals(List.of("mystery"), r.getUnpricedModels());

        SpendUsageReportDto.ModelRow sonnet = r.getByModel().get(0);
        assertEquals("claude-sonnet-4-5", sonnet.getModel());
        assertEquals(0, new BigDecimal("21").compareTo(sonnet.getMeteredCostCents()));
        assertEquals(0, new BigDecimal("18.5").compareTo(sonnet.getVendorCostCents()));
        assertTrue(sonnet.isPriced());
        assertFalse(r.getByModel().get(1).isPriced());
        assertNull(r.getByModel().get(1).getVendorCostCents());

        assertEquals(2, r.getByDay().size());
        assertEquals(LocalDate.of(2026, 7, 1), r.getByDay().get(0).getDate());
        assertEquals(16_000, r.getByDay().get(0).getTotalTokens());
        assertEquals(0, new BigDecimal("18.5").compareTo(r.getByDay().get(0).getVendorCostCents()));

        assertEquals(1, r.getByActor().size());
        SpendUsageReportDto.ActorRow dev = r.getByActor().get(0);
        assertEquals("dev@acme.test", dev.getActor());
        assertEquals(10_000, dev.getTotalTokens());
        assertEquals(4, dev.getSessions());
        assertEquals(0, new BigDecimal("12").compareTo(dev.getVendorCostCents()));
        assertEquals(0, new BigDecimal("10").compareTo(dev.getMeteredCostCents()));
    }

    @Test
    void reconcileComparesThreeFiguresPerProviderAndOnlyCountsInvoicesInsideTheWindow() {
        when(bucketRepository.findAllByAccountIdAndBucketStartGreaterThanEqualAndBucketStartLessThan(eq(accountId), any(), any()))
                .thenReturn(List.of(
                        bucket(VendorUsageSource.USAGE_API, day1, "claude-sonnet-4-5", null, 100_000, 0, 0, 0, null, null),
                        bucket(VendorUsageSource.COST_API, day1, null, null, 0, 0, 0, 0, null, "95")));
        VendorInvoice inside = new VendorInvoice();
        inside.setProvider(VendorProvider.ANTHROPIC);
        inside.setPeriodStart(LocalDate.of(2026, 7, 1));
        inside.setPeriodEnd(LocalDate.of(2026, 7, 31));
        inside.setTotalCents(new BigDecimal("90"));
        VendorInvoice straddling = new VendorInvoice();
        straddling.setProvider(VendorProvider.ANTHROPIC);
        straddling.setPeriodStart(LocalDate.of(2026, 6, 15));
        straddling.setPeriodEnd(LocalDate.of(2026, 7, 14));
        straddling.setTotalCents(new BigDecimal("999"));
        VendorInvoice openai = new VendorInvoice();
        openai.setProvider(VendorProvider.OPENAI);
        openai.setPeriodStart(LocalDate.of(2026, 7, 1));
        openai.setPeriodEnd(LocalDate.of(2026, 7, 31));
        openai.setTotalCents(new BigDecimal("40"));
        when(invoiceRepository.findOverlapping(accountId, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)))
                .thenReturn(List.of(inside, straddling, openai));

        SpendReconcileReportDto r = service.reconcile(accountId.toString(), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        assertEquals(2, r.getRows().size());
        SpendReconcileReportDto.Row anthropic = r.getRows().stream().filter(x -> x.getProvider() == VendorProvider.ANTHROPIC).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("100").compareTo(anthropic.getMeteredCents()));
        assertEquals(0, new BigDecimal("95").compareTo(anthropic.getVendorReportedCents()));
        assertEquals(0, new BigDecimal("90").compareTo(anthropic.getInvoicedCents()));
        assertEquals(1, anthropic.getInvoiceCount());
        assertEquals(0, new BigDecimal("5").compareTo(anthropic.getMeteredVsVendorCents()));
        assertEquals(0, new BigDecimal("5").compareTo(anthropic.getVendorVsInvoiceCents()));
        assertFalse(anthropic.isMeteredIsEstimate());

        SpendReconcileReportDto.Row oai = r.getRows().stream().filter(x -> x.getProvider() == VendorProvider.OPENAI).findFirst().orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(oai.getMeteredCents()));
        assertEquals(0, new BigDecimal("40").compareTo(oai.getInvoicedCents()));
        assertEquals(0, new BigDecimal("-40").compareTo(oai.getVendorVsInvoiceCents()));
    }

    @Test
    void reconcileFlagsAnEstimateWhenAModelIsUnpriced() {
        when(bucketRepository.findAllByAccountIdAndBucketStartGreaterThanEqualAndBucketStartLessThan(eq(accountId), any(), any()))
                .thenReturn(List.of(bucket(VendorUsageSource.USAGE_API, day1, "mystery", null, 10, 0, 0, 0, null, null)));
        when(invoiceRepository.findOverlapping(eq(accountId), any(), any())).thenReturn(List.of());
        SpendReconcileReportDto r = service.reconcile(accountId.toString(), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        assertTrue(r.getRows().get(0).isMeteredIsEstimate());
        assertNull(r.getRows().get(0).getInvoicedCents());
        assertNull(r.getRows().get(0).getVendorVsInvoiceCents());
    }
}
