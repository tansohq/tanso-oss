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
package com.tansoflow.tansocore.service.internal.monetization.implementation;

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.CreditFeatureWeight;
import com.tansoflow.tansocore.entity.Feature;
import com.tansoflow.tansocore.entity.PlanFeatureRule;
import com.tansoflow.tansocore.model.credit.CreditFeatureWeightDto;
import com.tansoflow.tansocore.model.credit.request.PublishCreditWeightsRequest;
import com.tansoflow.tansocore.model.credit.type.WeightMatch;
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.repository.AccountRepository;
import com.tansoflow.tansocore.repository.CreditFeatureWeightRepository;
import com.tansoflow.tansocore.repository.EventRepository;
import com.tansoflow.tansocore.repository.FeatureRepository;
import com.tansoflow.tansocore.repository.PlanFeatureRuleRepository;
import com.tansoflow.tansocore.service.internal.monetization.CreditWeightService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CreditWeightServiceImpl implements CreditWeightService {

    private static final BigDecimal MAX_WEIGHT = new BigDecimal("1000000");

    private final CreditFeatureWeightRepository creditFeatureWeightRepository;
    private final FeatureRepository featureRepository;
    private final AccountRepository accountRepository;
    private final PlanFeatureRuleRepository planFeatureRuleRepository;
    private final EventRepository eventRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public ResolvedWeight resolveWeight(UUID accountId, UUID featureId, String model, Instant at) {
        String normalizedModel = normalizeModel(model);

        if (normalizedModel != null) {
            Optional<CreditFeatureWeight> modelRow = creditFeatureWeightRepository
                    .findTopByAccountIdAndFeatureIdAndModelAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                            accountId, featureId, normalizedModel, at);
            if (modelRow.isPresent()) {
                return new ResolvedWeight(modelRow.get().getCreditsPerUnit(), modelRow.get().getId(), WeightMatch.MODEL);
            }
        }

        Optional<CreditFeatureWeight> defaultRow = creditFeatureWeightRepository
                .findTopByAccountIdAndFeatureIdAndModelIsNullAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        accountId, featureId, at);
        if (defaultRow.isPresent()) {
            return new ResolvedWeight(defaultRow.get().getCreditsPerUnit(), defaultRow.get().getId(), WeightMatch.FEATURE_DEFAULT);
        }

        return ResolvedWeight.IDENTITY;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CreditFeatureWeightDto> getWeights(String accountId) {
        return creditFeatureWeightRepository
                .findByAccountIdOrderByFeatureIdAscModelAscEffectiveFromDesc(UUID.fromString(accountId))
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CreditFeatureWeightDto> getHistory(String accountId, String featureId, String model) {
        return creditFeatureWeightRepository
                .findHistory(UUID.fromString(accountId), UUID.fromString(featureId), normalizeModel(model))
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public List<CreditFeatureWeightDto> publishWeights(PublishCreditWeightsRequest request, String accountId, UUID publishedBy) {
        UUID accountUuid = UUID.fromString(accountId);
        Account account = accountRepository.findById(accountUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));

        if (request.getEffectiveFrom().isBefore(Instant.now())) {
            throw new IllegalArgumentException(
                    "effectiveFrom must not be in the past — the settled ledger is never repriced");
        }

        // Serialize publishes per account so two concurrent tariffs can't interleave into a half-A-half-B state
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:acct))")
                .setParameter("acct", accountId)
                .getSingleResult();

        List<CreditFeatureWeight> rows = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        for (int i = 0; i < request.getEntries().size(); i++) {
            PublishCreditWeightsRequest.Entry entry = request.getEntries().get(i);
            String normalizedModel = normalizeModel(entry.getModel());

            BigDecimal weight = entry.getCreditsPerUnit();
            if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Entry " + i + ": creditsPerUnit must be positive");
            }
            if (weight.compareTo(MAX_WEIGHT) > 0) {
                throw new IllegalArgumentException("Entry " + i + ": creditsPerUnit exceeds maximum " + MAX_WEIGHT);
            }
            if (weight.stripTrailingZeros().scale() > 6) {
                throw new IllegalArgumentException("Entry " + i + ": creditsPerUnit allows at most 6 decimals");
            }

            UUID featureId;
            try {
                featureId = UUID.fromString(entry.getFeatureId());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Entry " + i + ": featureId is not a valid UUID");
            }
            // Never trust a raw UUID from a request body — verify tenant ownership
            Feature feature = featureRepository.findByIdAndAccount(featureId, account)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Entry " + rows.size() + ": feature not found for this account"));

            if (!seenKeys.add(featureId + "|" + normalizedModel)) {
                throw new IllegalArgumentException("Entry " + i + ": duplicate (feature, model) in batch");
            }

            validateSingleDenomination(feature, i);

            CreditFeatureWeight row = new CreditFeatureWeight();
            row.setAccount(account);
            row.setFeature(feature);
            row.setModel(normalizedModel);
            row.setCreditsPerUnit(weight);
            row.setEffectiveFrom(request.getEffectiveFrom());
            row.setCreatedBy(publishedBy);
            rows.add(row);
        }

        // Idempotent replay: identical batch at the same effectiveFrom returns the existing rows.
        // Anything else already published at that instant is a conflict, not a merge.
        if (creditFeatureWeightRepository.existsByAccountIdAndEffectiveFrom(accountUuid, request.getEffectiveFrom())) {
            List<CreditFeatureWeight> existing = creditFeatureWeightRepository
                    .findByAccountIdOrderByFeatureIdAscModelAscEffectiveFromDesc(accountUuid)
                    .stream()
                    .filter(w -> w.getEffectiveFrom().equals(request.getEffectiveFrom()))
                    .toList();
            if (sameBatch(existing, rows)) {
                log.info("Idempotent tariff replay for account {} at {}", accountId, request.getEffectiveFrom());
                return existing.stream().map(this::toDto).toList();
            }
            throw new IllegalStateException(
                    "A different tariff is already published at " + request.getEffectiveFrom() + " — pick another effective time");
        }

        creditFeatureWeightRepository.saveAllAndFlush(rows);
        log.info("Published tariff of {} weights for account {} effective {}", rows.size(), accountId, request.getEffectiveFrom());
        return rows.stream().map(this::toDto).toList();
    }

    @Override
    @Transactional
    public void deleteScheduledWeight(String weightId, String accountId) {
        CreditFeatureWeight row = creditFeatureWeightRepository
                .findByIdAndAccountId(UUID.fromString(weightId), UUID.fromString(accountId))
                .orElseThrow(() -> new ResourceNotFoundException("Weight row not found: " + weightId));

        if (!row.getEffectiveFrom().isAfter(Instant.now())) {
            throw new IllegalArgumentException(
                    "Only scheduled rows (effectiveFrom in the future) can be deleted — effective rows are the settled tariff");
        }
        creditFeatureWeightRepository.delete(row);
        log.info("Deleted scheduled weight row {} (was effective {})", weightId, row.getEffectiveFrom());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> getObservedUnitCosts(String accountId, Instant since) {
        Map<String, BigDecimal> result = new HashMap<>();
        for (Object[] rowArr : eventRepository.sumCostAndUsageByFeatureAndModel(UUID.fromString(accountId), since)) {
            UUID featureId = (UUID) rowArr[0];
            String model = (String) rowArr[1];
            BigDecimal totalCost = (BigDecimal) rowArr[2];
            BigDecimal totalUnits = (BigDecimal) rowArr[3];
            if (totalUnits == null || totalUnits.compareTo(BigDecimal.ZERO) == 0 || totalCost == null) {
                continue;
            }
            result.put(featureId + "|" + (model != null ? model : ""),
                    totalCost.divide(totalUnits, 6, java.math.RoundingMode.HALF_UP));
        }
        return result;
    }

    /**
     * Burn is per-denomination but the weight table has no denomination column,
     * so a feature whose plan rules burn more than one denomination would share
     * one ambiguous weight row. Rejected until the table grows a credit_model_id.
     */
    private void validateSingleDenomination(Feature feature, int entryIndex) {
        Set<String> denominations = new HashSet<>();
        for (PlanFeatureRule rule : planFeatureRuleRepository.findPlanFeatureRulesByFeatureId(feature.getId())) {
            if (rule.getCreditModel() != null && rule.getCreditModel().getDenomination() != null) {
                denominations.add(rule.getCreditModel().getDenomination());
            }
        }
        if (denominations.size() > 1) {
            throw new IllegalArgumentException("Entry " + entryIndex + ": feature '" + feature.getKey()
                    + "' burns multiple credit denominations (" + String.join(", ", denominations)
                    + ") — a single weight row would be ambiguous");
        }
    }

    private boolean sameBatch(List<CreditFeatureWeight> existing, List<CreditFeatureWeight> proposed) {
        if (existing.size() != proposed.size()) return false;
        Set<String> existingKeys = new HashSet<>();
        for (CreditFeatureWeight w : existing) {
            existingKeys.add(w.getFeature().getId() + "|" + w.getModel() + "|" + w.getCreditsPerUnit().stripTrailingZeros());
        }
        for (CreditFeatureWeight w : proposed) {
            if (!existingKeys.contains(
                    w.getFeature().getId() + "|" + w.getModel() + "|" + w.getCreditsPerUnit().stripTrailingZeros())) {
                return false;
            }
        }
        return true;
    }

    static String normalizeModel(String model) {
        if (model == null) return null;
        String trimmed = model.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private CreditFeatureWeightDto toDto(CreditFeatureWeight row) {
        CreditFeatureWeightDto dto = new CreditFeatureWeightDto();
        dto.setId(row.getId().toString());
        dto.setFeatureId(row.getFeature().getId().toString());
        dto.setFeatureKey(row.getFeature().getKey());
        dto.setModel(row.getModel());
        dto.setCreditsPerUnit(row.getCreditsPerUnit());
        dto.setEffectiveFrom(row.getEffectiveFrom());
        dto.setCreatedBy(row.getCreatedBy() != null ? row.getCreatedBy().toString() : null);
        dto.setCreatedAt(row.getCreatedAt());
        return dto;
    }
}
