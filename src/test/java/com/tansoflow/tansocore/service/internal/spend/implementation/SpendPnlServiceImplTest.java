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
import com.tansoflow.tansocore.model.spend.SpendAllocationReportDto;
import com.tansoflow.tansocore.model.spend.SpendOutcomeReportDto;
import com.tansoflow.tansocore.model.spend.SpendPnlReportDto;
import com.tansoflow.tansocore.model.spend.type.SpendUnitType;
import com.tansoflow.tansocore.repository.EventRepository;
import com.tansoflow.tansocore.repository.FeatureRepository;
import com.tansoflow.tansocore.repository.SpendUnitRepository;
import com.tansoflow.tansocore.service.internal.spend.OutcomeService;
import com.tansoflow.tansocore.service.internal.spend.SpendAllocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpendPnlServiceImplTest {
    @Mock private SpendUnitRepository unitRepository;
    @Mock private FeatureRepository featureRepository;
    @Mock private EventRepository eventRepository;
    @Mock private SpendAllocationService allocationService;
    @Mock private OutcomeService outcomeService;

    private final UUID account = UUID.randomUUID();
    private final UUID featureId = UUID.randomUUID();
    private SpendPnlServiceImpl service;
    private SpendUnit search;
    private SpendUnit orphan;

    @BeforeEach
    void setUp() {
        service = new SpendPnlServiceImpl(unitRepository, featureRepository, eventRepository, allocationService, outcomeService);
        search = unit("AI search", SpendUnitType.PROJECT, featureId);
        orphan = unit("Migration", SpendUnitType.PROJECT, null);
        SpendUnit team = unit("Backend", SpendUnitType.TEAM, null);
        lenient().when(unitRepository.findAllByAccountIdOrderByNameAsc(account)).thenReturn(List.of(search, team, orphan));
        Feature f = new Feature();
        f.setKey("ai_search");
        f.setName("AI Search");
        lenient().when(featureRepository.findByIdAndAccountId(featureId, account)).thenReturn(Optional.of(f));
        // revenue and cost arrive in dollars
        lenient().when(eventRepository.sumRevenueAndCostByFeature(eq(account), eq(List.of(featureId)), any(), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{featureId, new BigDecimal("1250.00"), new BigDecimal("310.50")}));
        lenient().when(allocationService.allocate(eq(account.toString()), any(), any())).thenReturn(SpendAllocationReportDto.builder().rows(List.of(
                SpendAllocationReportDto.AllocationRow.builder().unitId(search.getId().toString()).name("AI search").totalCents(new BigDecimal("42000")).build(),
                SpendAllocationReportDto.AllocationRow.builder().unitId(orphan.getId().toString()).name("Migration").totalCents(new BigDecimal("9000")).build()
        )).build());
        lenient().when(outcomeService.report(eq(account.toString()), any(), any())).thenReturn(SpendOutcomeReportDto.builder().rows(List.of(
                SpendOutcomeReportDto.OutcomeRow.builder().unitId(search.getId().toString()).outcomes(7).build()
        )).build());
    }

    private SpendUnit unit(String name, SpendUnitType type, UUID feature) {
        SpendUnit u = new SpendUnit();
        u.setId(UUID.randomUUID());
        u.setAccountId(account);
        u.setName(name);
        u.setType(type);
        u.setFeatureId(feature);
        return u;
    }

    @Test
    void projectBuildCostSitsNextToItsFeaturesServeSideNumbers() {
        SpendPnlReportDto r = service.report(account.toString(), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 26));
        assertEquals(1, r.getRows().size());
        SpendPnlReportDto.PnlRow row = r.getRows().get(0);
        assertEquals("AI search", row.getName());
        assertEquals("ai_search", row.getFeatureKey());
        assertEquals(0, new BigDecimal("42000").compareTo(row.getBuildCents()));
        assertEquals(0, new BigDecimal("125000").compareTo(row.getRevenueCents()), "dollars → cents");
        assertEquals(0, new BigDecimal("31050").compareTo(row.getServeCostCents()));
        assertEquals(0, new BigDecimal("93950").compareTo(row.getServeMarginCents()));
        assertEquals(0, new BigDecimal("51950").compareTo(row.getNetCents()));
        assertEquals(7, row.getOutcomes());
        assertEquals(0, new BigDecimal("6000").compareTo(row.getBuildPerOutcomeCents()));
        assertEquals(List.of("Migration"), r.getUnlinkedProjects(), "a project without a feature is named, not silently dropped");
        assertEquals(0, new BigDecimal("42000").compareTo(r.getTotalBuildCents()), "unlinked build cost is not in the P&L total");
        assertEquals(0, new BigDecimal("51950").compareTo(r.getTotalNetCents()));
    }

    @Test
    void featureWithNoEventsReadsAsZeroRevenueNotMissing() {
        when(eventRepository.sumRevenueAndCostByFeature(eq(account), any(), any(), any(), any())).thenReturn(List.of());
        when(outcomeService.report(eq(account.toString()), any(), any())).thenReturn(SpendOutcomeReportDto.builder().rows(List.of()).build());
        SpendPnlReportDto.PnlRow row = service.report(account.toString(), null, null).getRows().get(0);
        assertEquals(0, BigDecimal.ZERO.compareTo(row.getRevenueCents()));
        assertEquals(0, new BigDecimal("-42000").compareTo(row.getNetCents()));
        assertNull(row.getBuildPerOutcomeCents());
    }
}
