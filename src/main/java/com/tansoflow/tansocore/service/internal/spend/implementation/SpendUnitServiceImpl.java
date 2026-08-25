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

import com.tansoflow.tansocore.entity.SpendAttributionRule;
import com.tansoflow.tansocore.entity.SpendUnit;
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.model.spend.SpendAttributionRuleDto;
import com.tansoflow.tansocore.model.spend.SpendUnitDto;
import com.tansoflow.tansocore.model.spend.request.SpendAttributionRuleRequest;
import com.tansoflow.tansocore.model.spend.request.SpendUnitRequest;
import com.tansoflow.tansocore.model.spend.type.SpendUnitType;
import com.tansoflow.tansocore.repository.SpendAttributionRuleRepository;
import com.tansoflow.tansocore.repository.SpendBudgetRepository;
import com.tansoflow.tansocore.repository.SpendUnitRepository;
import com.tansoflow.tansocore.service.internal.spend.SpendSettingsService;
import com.tansoflow.tansocore.service.internal.spend.SpendUnitService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.modules.build.enabled", havingValue = "true", matchIfMissing = true)
public class SpendUnitServiceImpl implements SpendUnitService {
    private final SpendUnitRepository unitRepository;
    private final SpendAttributionRuleRepository ruleRepository;
    private final SpendBudgetRepository budgetRepository;
    private final SpendSettingsService settingsService;

    @Override
    @Transactional(readOnly = true)
    public List<SpendUnitDto> listUnits(String accountId) {
        return unitRepository.findAllByAccountIdOrderByNameAsc(UUID.fromString(accountId))
                .stream().map(SpendUnitServiceImpl::toDto).toList();
    }

    @Override
    @Transactional
    public SpendUnitDto createUnit(String accountId, SpendUnitRequest request) {
        UUID account = UUID.fromString(accountId);
        SpendUnit unit = new SpendUnit();
        unit.setAccountId(account);
        apply(account, unit, request);
        return toDto(unitRepository.save(unit));
    }

    @Override
    @Transactional
    public SpendUnitDto updateUnit(String accountId, String unitId, SpendUnitRequest request) {
        UUID account = UUID.fromString(accountId);
        SpendUnit unit = requireUnit(account, unitId);
        if (request.getParentId() != null && request.getParentId().equals(unit.getId().toString())) {
            throw new IllegalArgumentException("A unit cannot be its own parent");
        }
        apply(account, unit, request);
        return toDto(unitRepository.save(unit));
    }

    @Override
    @Transactional
    public void deleteUnit(String accountId, String unitId) {
        UUID account = UUID.fromString(accountId);
        SpendUnit unit = requireUnit(account, unitId);
        for (SpendUnit child : unitRepository.findAllByAccountIdOrderByNameAsc(account)) {
            if (unit.getId().equals(child.getParentId())) {
                UUID lifted = unit.getParentId();
                child.setParentId(lifted != null && lifted.equals(child.getId()) ? null : lifted);
                unitRepository.save(child);
            }
        }
        ruleRepository.deleteBySpendUnitId(unit.getId());
        budgetRepository.findBySpendUnitIdAndAccountId(unit.getId(), account).ifPresent(budgetRepository::delete);
        unit.setDeletedAt(Instant.now());
        unitRepository.save(unit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SpendAttributionRuleDto> listRules(String accountId) {
        return ruleRepository.findAllByAccountIdOrderByPriorityAscCreatedAtAsc(UUID.fromString(accountId))
                .stream().map(SpendUnitServiceImpl::toDto).toList();
    }

    @Override
    @Transactional
    public SpendAttributionRuleDto createRule(String accountId, SpendAttributionRuleRequest request) {
        UUID account = UUID.fromString(accountId);
        SpendUnit unit = requireUnit(account, request.getSpendUnitId());
        String value = request.getMatchValue().trim();
        for (SpendAttributionRule existing : ruleRepository.findAllByAccountIdOrderByPriorityAscCreatedAtAsc(account)) {
            if (existing.getSpendUnitId().equals(unit.getId()) && existing.getProvider() == request.getProvider()
                    && existing.getMatchKind() == request.getMatchKind() && existing.getMatchValue().equalsIgnoreCase(value)) {
                throw new IllegalArgumentException("That rule already exists on " + unit.getName());
            }
        }
        SpendAttributionRule rule = new SpendAttributionRule();
        rule.setAccountId(account);
        rule.setSpendUnitId(unit.getId());
        rule.setProvider(request.getProvider());
        rule.setMatchKind(request.getMatchKind());
        rule.setMatchValue(value);
        rule.setPriority(request.getPriority() != null ? request.getPriority() : 100);
        return toDto(ruleRepository.save(rule));
    }

    @Override
    @Transactional
    public void deleteRule(String accountId, String ruleId) {
        SpendAttributionRule rule = ruleRepository.findByIdAndAccountId(UUID.fromString(ruleId), UUID.fromString(accountId))
                .orElseThrow(() -> new ResourceNotFoundException("Attribution rule not found: " + ruleId));
        ruleRepository.delete(rule);
    }

    private void apply(UUID account, SpendUnit unit, SpendUnitRequest request) {
        if (request.getType() == SpendUnitType.PERSON && !settingsService.personLevelEnabled(account.toString())) {
            throw new IllegalArgumentException(
                    "Person-level attribution is off for this account. Turn it on under Spend settings (after telling staff) to add people.");
        }
        unit.setType(request.getType());
        unit.setName(request.getName().trim());
        String email = request.getEmail() == null || request.getEmail().isBlank() ? null : request.getEmail().trim().toLowerCase(Locale.ROOT);
        unit.setEmail(email);
        if (request.getParentId() == null || request.getParentId().isBlank()) {
            unit.setParentId(null);
        } else {
            SpendUnit parent = requireUnit(account, request.getParentId());
            // Walk up from the proposed parent; meeting this unit would close a loop,
            // and the roll-up would then count the same cents on every lap.
            UUID cursor = parent.getId();
            java.util.Set<UUID> seen = new java.util.HashSet<>();
            while (cursor != null && seen.add(cursor)) {
                if (unit.getId() != null && cursor.equals(unit.getId())) {
                    throw new IllegalArgumentException("That would make " + unit.getName() + " an ancestor of itself");
                }
                SpendUnit next = unitRepository.findByIdAndAccountId(cursor, account).orElse(null);
                cursor = next == null ? null : next.getParentId();
            }
            unit.setParentId(parent.getId());
        }
    }

    private SpendUnit requireUnit(UUID account, String unitId) {
        return unitRepository.findByIdAndAccountId(UUID.fromString(unitId), account)
                .orElseThrow(() -> new ResourceNotFoundException("Spend unit not found: " + unitId));
    }

    static SpendUnitDto toDto(SpendUnit u) {
        return SpendUnitDto.builder()
                .id(u.getId().toString()).type(u.getType()).name(u.getName()).email(u.getEmail())
                .parentId(u.getParentId() == null ? null : u.getParentId().toString())
                .createdAt(u.getCreatedAt()).build();
    }

    static SpendAttributionRuleDto toDto(SpendAttributionRule r) {
        return SpendAttributionRuleDto.builder()
                .id(r.getId().toString()).spendUnitId(r.getSpendUnitId().toString())
                .provider(r.getProvider()).matchKind(r.getMatchKind()).matchValue(r.getMatchValue())
                .priority(r.getPriority()).build();
    }
}
