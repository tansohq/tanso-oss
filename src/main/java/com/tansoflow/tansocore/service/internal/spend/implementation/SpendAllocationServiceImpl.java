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
import com.tansoflow.tansocore.entity.VendorUsageBucket;
import com.tansoflow.tansocore.model.spend.SpendAllocationReportDto;
import com.tansoflow.tansocore.model.spend.type.SpendUnitType;
import com.tansoflow.tansocore.model.spend.type.VendorUsageSource;
import com.tansoflow.tansocore.repository.SpendAttributionRuleRepository;
import com.tansoflow.tansocore.repository.SpendUnitRepository;
import com.tansoflow.tansocore.repository.VendorUsageBucketRepository;
import com.tansoflow.tansocore.service.internal.spend.SpendAllocationService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rules are applied here, at report time, never materialised: editing a rule
 * re-allocates history, and there is nothing to keep in sync.
 *
 * Metered usage rows (USAGE_API) are matched to the first rule, by priority,
 * whose provider and dimension fit. Claude Code rows only ever reach PERSON
 * units by actor and are kept separate from the roll-up: the same traffic
 * already reaches the person's team through the team's key rules.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.modules.build.enabled", havingValue = "true", matchIfMissing = true)
public class SpendAllocationServiceImpl implements SpendAllocationService {
    private final VendorUsageBucketRepository bucketRepository;
    private final SpendUnitRepository unitRepository;
    private final SpendAttributionRuleRepository ruleRepository;
    private final SpendSettingsService settingsService;
    private final VendorCostEstimator estimator;

    @Override
    @Transactional(readOnly = true)
    public SpendAllocationReportDto allocate(String accountId, LocalDate from, LocalDate to) {
        UUID account = UUID.fromString(accountId);
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
        boolean personLevel = settingsService.personLevelEnabled(accountId);
        List<SpendUnit> units = unitRepository.findAllByAccountIdOrderByNameAsc(account);
        Map<UUID, SpendUnit> unitById = new HashMap<>();
        for (SpendUnit u : units) {
            unitById.put(u.getId(), u);
        }
        List<SpendAttributionRule> rules = new ArrayList<>();
        for (SpendAttributionRule r : ruleRepository.findAllByAccountIdOrderByPriorityAscCreatedAtAsc(account)) {
            SpendUnit target = unitById.get(r.getSpendUnitId());
            if (target != null && (personLevel || target.getType() != SpendUnitType.PERSON)) {
                rules.add(r);
            }
        }

        Map<UUID, BigDecimal> own = new HashMap<>();
        Map<UUID, BigDecimal> personEstimate = new HashMap<>();
        BigDecimal unattributed = BigDecimal.ZERO;
        BigDecimal totalMetered = BigDecimal.ZERO;
        List<VendorUsageBucket> buckets = bucketRepository.findAllByAccountIdAndBucketStartGreaterThanEqualAndBucketStartLessThan(
                account, from.atStartOfDay(ZoneOffset.UTC).toInstant(), to.atStartOfDay(ZoneOffset.UTC).toInstant());
        for (VendorUsageBucket b : buckets) {
            if (b.getSource() == VendorUsageSource.USAGE_API) {
                BigDecimal cents = estimator.estimate(b.getModel(), b.getUncachedInputTokens(), b.getCacheReadTokens(),
                        b.getCacheCreationTokens(), b.getOutputTokens()).cents();
                totalMetered = totalMetered.add(cents);
                SpendAttributionRule match = firstMatch(rules, b);
                if (match == null) {
                    unattributed = unattributed.add(cents);
                } else {
                    own.merge(match.getSpendUnitId(), cents, BigDecimal::add);
                }
            } else if (b.getSource() == VendorUsageSource.CLAUDE_CODE_API && personLevel && b.getActorId() != null) {
                SpendAttributionRule match = firstMatch(rules, b);
                if (match != null && unitById.get(match.getSpendUnitId()).getType() == SpendUnitType.PERSON) {
                    BigDecimal cents = b.getVendorCostCents() != null ? b.getVendorCostCents()
                            : estimator.estimate(b.getModel(), b.getUncachedInputTokens(), b.getCacheReadTokens(),
                            b.getCacheCreationTokens(), b.getOutputTokens()).cents();
                    personEstimate.merge(match.getSpendUnitId(), cents, BigDecimal::add);
                }
            }
        }

        // Roll own cents up every ancestor chain.
        Map<UUID, BigDecimal> total = new HashMap<>();
        for (SpendUnit u : units) {
            BigDecimal mine = own.getOrDefault(u.getId(), BigDecimal.ZERO);
            UUID cursor = u.getId();
            java.util.Set<UUID> seen = new java.util.HashSet<>();
            while (cursor != null && seen.add(cursor)) {   // a loop in the tree counts each unit once, never per lap
                total.merge(cursor, mine, BigDecimal::add);
                SpendUnit parent = unitById.get(cursor);
                cursor = parent == null ? null : parent.getParentId();
            }
        }

        List<SpendAllocationReportDto.Row> rows = new ArrayList<>();
        for (SpendUnit u : units) {
            BigDecimal t = total.getOrDefault(u.getId(), BigDecimal.ZERO);
            BigDecimal pe = u.getType() == SpendUnitType.PERSON ? personEstimate.getOrDefault(u.getId(), BigDecimal.ZERO) : null;
            rows.add(SpendAllocationReportDto.Row.builder()
                    .unitId(u.getId().toString()).name(u.getName()).type(u.getType())
                    .parentId(u.getParentId() == null ? null : u.getParentId().toString())
                    .ownCents(own.getOrDefault(u.getId(), BigDecimal.ZERO))
                    .totalCents(t)
                    .personEstimateCents(pe)
                    .spendCents(pe == null ? t : t.add(pe))
                    .build());
        }
        return SpendAllocationReportDto.builder()
                .from(from).to(to).rows(rows)
                .unattributedCents(unattributed).totalMeteredCents(totalMetered)
                .personLevelEnabled(personLevel)
                .build();
    }

    static SpendAttributionRule firstMatch(List<SpendAttributionRule> rules, VendorUsageBucket b) {
        for (SpendAttributionRule r : rules) {
            if (r.getProvider() != b.getProvider()) {
                continue;
            }
            String value = switch (r.getMatchKind()) {
                case WORKSPACE_ID -> b.getWorkspaceId();
                case API_KEY_ID -> b.getVendorApiKeyId();
                case ACTOR -> b.getActorId();
            };
            if (value != null && value.equalsIgnoreCase(r.getMatchValue())) {
                return r;
            }
        }
        return null;
    }
}
