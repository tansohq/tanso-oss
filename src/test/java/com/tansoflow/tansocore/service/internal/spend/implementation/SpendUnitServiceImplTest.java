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

import com.tansoflow.tansocore.entity.SpendUnit;
import com.tansoflow.tansocore.model.spend.SpendUnitDto;
import com.tansoflow.tansocore.model.spend.request.SpendUnitRequest;
import com.tansoflow.tansocore.model.spend.type.SpendUnitType;
import com.tansoflow.tansocore.repository.SpendAttributionRuleRepository;
import com.tansoflow.tansocore.repository.SpendBudgetRepository;
import com.tansoflow.tansocore.repository.SpendUnitRepository;
import com.tansoflow.tansocore.service.internal.spend.SpendSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpendUnitServiceImplTest {

    @Mock private SpendUnitRepository unitRepository;
    @Mock private SpendAttributionRuleRepository ruleRepository;
    @Mock private SpendBudgetRepository budgetRepository;
    @Mock private SpendSettingsService settingsService;

    private SpendUnitServiceImpl service;
    private final UUID accountId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SpendUnitServiceImpl(unitRepository, ruleRepository, budgetRepository, settingsService);
        lenient().when(unitRepository.save(any())).thenAnswer(inv -> { SpendUnit u = inv.getArgument(0); if (u.getId() == null) u.setId(UUID.randomUUID()); return u; });
    }

    @Test
    void personUnitsNeedPersonLevelOn() {
        when(settingsService.personLevelEnabled(accountId.toString())).thenReturn(false);
        SpendUnitRequest req = new SpendUnitRequest();
        req.setType(SpendUnitType.PERSON);
        req.setName("Alice");
        req.setEmail("Alice@Acme.test");
        assertThrows(IllegalArgumentException.class, () -> service.createUnit(accountId.toString(), req));

        when(settingsService.personLevelEnabled(accountId.toString())).thenReturn(true);
        SpendUnitDto dto = service.createUnit(accountId.toString(), req);
        assertEquals("alice@acme.test", dto.getEmail());
        assertNotNull(dto.getId());
    }

    @Test
    void deleteReparentsChildrenAndDropsRulesAndBudget() {
        SpendUnit project = new SpendUnit(); project.setId(UUID.randomUUID()); project.setAccountId(accountId); project.setType(SpendUnitType.PROJECT); project.setName("P");
        SpendUnit team = new SpendUnit(); team.setId(UUID.randomUUID()); team.setAccountId(accountId); team.setType(SpendUnitType.TEAM); team.setName("T"); team.setParentId(project.getId());
        SpendUnit child = new SpendUnit(); child.setId(UUID.randomUUID()); child.setAccountId(accountId); child.setType(SpendUnitType.TEAM); child.setName("C"); child.setParentId(team.getId());
        when(unitRepository.findByIdAndAccountId(team.getId(), accountId)).thenReturn(Optional.of(team));
        when(unitRepository.findAllByAccountIdOrderByNameAsc(accountId)).thenReturn(List.of(child, project, team));
        when(budgetRepository.findBySpendUnitIdAndAccountId(team.getId(), accountId)).thenReturn(Optional.empty());

        service.deleteUnit(accountId.toString(), team.getId().toString());

        assertEquals(project.getId(), child.getParentId());
        assertNotNull(team.getDeletedAt());
        verify(ruleRepository).deleteBySpendUnitId(team.getId());
    }

    @Test
    void reparentingIntoADescendantIsRefused() {
        SpendUnit platform = new SpendUnit(); platform.setId(UUID.randomUUID()); platform.setAccountId(accountId); platform.setType(SpendUnitType.PROJECT); platform.setName("Platform");
        SpendUnit backend = new SpendUnit(); backend.setId(UUID.randomUUID()); backend.setAccountId(accountId); backend.setType(SpendUnitType.TEAM); backend.setName("Backend"); backend.setParentId(platform.getId());
        when(unitRepository.findByIdAndAccountId(platform.getId(), accountId)).thenReturn(Optional.of(platform));
        when(unitRepository.findByIdAndAccountId(backend.getId(), accountId)).thenReturn(Optional.of(backend));
        SpendUnitRequest req = new SpendUnitRequest();
        req.setType(SpendUnitType.PROJECT);
        req.setName("Platform");
        req.setParentId(backend.getId().toString());
        assertThrows(IllegalArgumentException.class, () -> service.updateUnit(accountId.toString(), platform.getId().toString(), req));
        assertEquals(null, platform.getParentId());
    }

    @Test
    void duplicateRulesAreRefused() {
        SpendUnit team = new SpendUnit(); team.setId(UUID.randomUUID()); team.setAccountId(accountId); team.setType(SpendUnitType.TEAM); team.setName("T");
        when(unitRepository.findByIdAndAccountId(team.getId(), accountId)).thenReturn(Optional.of(team));
        com.tansoflow.tansocore.entity.SpendAttributionRule existing = new com.tansoflow.tansocore.entity.SpendAttributionRule();
        existing.setSpendUnitId(team.getId());
        existing.setProvider(com.tansoflow.tansocore.model.spend.type.VendorProvider.ANTHROPIC);
        existing.setMatchKind(com.tansoflow.tansocore.model.spend.type.AttributionMatchKind.WORKSPACE_ID);
        existing.setMatchValue("wrkspc_1");
        when(ruleRepository.findAllByAccountIdOrderByPriorityAscCreatedAtAsc(accountId)).thenReturn(List.of(existing));
        com.tansoflow.tansocore.model.spend.request.SpendAttributionRuleRequest req = new com.tansoflow.tansocore.model.spend.request.SpendAttributionRuleRequest();
        req.setSpendUnitId(team.getId().toString());
        req.setProvider(com.tansoflow.tansocore.model.spend.type.VendorProvider.ANTHROPIC);
        req.setMatchKind(com.tansoflow.tansocore.model.spend.type.AttributionMatchKind.WORKSPACE_ID);
        req.setMatchValue(" WRKSPC_1 ");
        assertThrows(IllegalArgumentException.class, () -> service.createRule(accountId.toString(), req));
    }

    @Test
    void aUnitCannotParentItself() {
        SpendUnit team = new SpendUnit(); team.setId(UUID.randomUUID()); team.setAccountId(accountId); team.setType(SpendUnitType.TEAM); team.setName("T");
        when(unitRepository.findByIdAndAccountId(team.getId(), accountId)).thenReturn(Optional.of(team));
        SpendUnitRequest req = new SpendUnitRequest();
        req.setType(SpendUnitType.TEAM);
        req.setName("T");
        req.setParentId(team.getId().toString());
        assertThrows(IllegalArgumentException.class, () -> service.updateUnit(accountId.toString(), team.getId().toString(), req));
        req.setParentId("");
        assertNull(service.updateUnit(accountId.toString(), team.getId().toString(), req).getParentId());
    }
}
