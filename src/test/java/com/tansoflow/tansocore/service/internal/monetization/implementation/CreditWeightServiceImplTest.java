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
import com.tansoflow.tansocore.entity.CreditModel;
import com.tansoflow.tansocore.entity.Feature;
import com.tansoflow.tansocore.entity.PlanFeatureRule;
import com.tansoflow.tansocore.model.credit.CreditFeatureWeightDto;
import com.tansoflow.tansocore.model.credit.request.PublishCreditWeightsRequest;
import com.tansoflow.tansocore.model.credit.type.WeightMatch;
import com.tansoflow.tansocore.model.exception.TariffConflictException;
import com.tansoflow.tansocore.repository.AccountRepository;
import com.tansoflow.tansocore.repository.CreditFeatureWeightRepository;
import com.tansoflow.tansocore.repository.EventRepository;
import com.tansoflow.tansocore.repository.FeatureRepository;
import com.tansoflow.tansocore.repository.PlanFeatureRuleRepository;
import com.tansoflow.tansocore.service.internal.monetization.CreditWeightService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditWeightServiceImplTest {

    @Mock
    private CreditFeatureWeightRepository creditFeatureWeightRepository;
    @Mock
    private FeatureRepository featureRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private PlanFeatureRuleRepository planFeatureRuleRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private EntityManager entityManager;
    @Mock
    private Query lockQuery;

    @InjectMocks
    private CreditWeightServiceImpl creditWeightService;

    private UUID accountId;
    private UUID featureId;
    private Account account;
    private Feature feature;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        featureId = UUID.randomUUID();

        account = new Account();
        account.setId(accountId);

        feature = new Feature();
        feature.setId(featureId);
        feature.setKey("ai.chat");

        // @PersistenceContext field isn't part of the Lombok constructor, so @InjectMocks
        // doesn't wire it via constructor injection — set it explicitly.
        ReflectionTestUtils.setField(creditWeightService, "entityManager", entityManager);
    }

    private void stubAdvisoryLock() {
        lenient().when(entityManager.createNativeQuery(anyString())).thenReturn(lockQuery);
        lenient().when(lockQuery.setParameter(anyString(), any())).thenReturn(lockQuery);
        lenient().when(lockQuery.getSingleResult()).thenReturn(true);
    }

    // ── resolveWeight tiers ──────────────────────────────────────────────

    @Test
    void resolveWeight_modelTierMatches_returnsModelWeight() {
        Instant at = Instant.now();
        CreditFeatureWeight row = new CreditFeatureWeight();
        row.setId(UUID.randomUUID());
        row.setCreditsPerUnit(new BigDecimal("5"));

        when(creditFeatureWeightRepository
                .findTopByAccountIdAndFeatureIdAndModelAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        accountId, featureId, "gpt-4.1", at))
                .thenReturn(Optional.of(row));

        CreditWeightService.ResolvedWeight result = creditWeightService.resolveWeight(accountId, featureId, "gpt-4.1", at);

        assertEquals(new BigDecimal("5"), result.weight());
        assertEquals(row.getId(), result.weightId());
        assertEquals(WeightMatch.MODEL, result.match());
        verify(creditFeatureWeightRepository, never())
                .findTopByAccountIdAndFeatureIdAndModelIsNullAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(any(), any(), any());
    }

    @Test
    void resolveWeight_noModelTierRow_fallsBackToFeatureDefault() {
        Instant at = Instant.now();
        CreditFeatureWeight defaultRow = new CreditFeatureWeight();
        defaultRow.setId(UUID.randomUUID());
        defaultRow.setCreditsPerUnit(new BigDecimal("2"));

        when(creditFeatureWeightRepository
                .findTopByAccountIdAndFeatureIdAndModelAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        accountId, featureId, "gpt-4.1", at))
                .thenReturn(Optional.empty());
        when(creditFeatureWeightRepository
                .findTopByAccountIdAndFeatureIdAndModelIsNullAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        accountId, featureId, at))
                .thenReturn(Optional.of(defaultRow));

        CreditWeightService.ResolvedWeight result = creditWeightService.resolveWeight(accountId, featureId, "gpt-4.1", at);

        assertEquals(new BigDecimal("2"), result.weight());
        assertEquals(WeightMatch.FEATURE_DEFAULT, result.match());
    }

    @Test
    void resolveWeight_noRowsAtAll_returnsIdentityDefault() {
        Instant at = Instant.now();
        when(creditFeatureWeightRepository
                .findTopByAccountIdAndFeatureIdAndModelIsNullAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        accountId, featureId, at))
                .thenReturn(Optional.empty());

        CreditWeightService.ResolvedWeight result = creditWeightService.resolveWeight(accountId, featureId, null, at);

        assertEquals(BigDecimal.ONE, result.weight());
        assertNull(result.weightId());
        assertEquals(WeightMatch.NONE, result.match());
    }

    @Test
    void resolveWeight_nullModel_skipsModelTierLookup() {
        Instant at = Instant.now();
        when(creditFeatureWeightRepository
                .findTopByAccountIdAndFeatureIdAndModelIsNullAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        accountId, featureId, at))
                .thenReturn(Optional.empty());

        creditWeightService.resolveWeight(accountId, featureId, null, at);

        verify(creditFeatureWeightRepository, never())
                .findTopByAccountIdAndFeatureIdAndModelAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(any(), any(), any(), any());
    }

    @Test
    void resolveWeight_blankModel_normalizedToNull() {
        Instant at = Instant.now();
        when(creditFeatureWeightRepository
                .findTopByAccountIdAndFeatureIdAndModelIsNullAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        accountId, featureId, at))
                .thenReturn(Optional.empty());

        creditWeightService.resolveWeight(accountId, featureId, "   ", at);

        verify(creditFeatureWeightRepository, never())
                .findTopByAccountIdAndFeatureIdAndModelAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(any(), any(), any(), any());
    }

    // ── publishWeights ───────────────────────────────────────────────────

    private PublishCreditWeightsRequest.Entry entry(String featureId, String model, String weight) {
        PublishCreditWeightsRequest.Entry e = new PublishCreditWeightsRequest.Entry();
        e.setFeatureId(featureId);
        e.setModel(model);
        e.setCreditsPerUnit(new BigDecimal(weight));
        return e;
    }

    @Test
    void publishWeights_happyPath_createsRow() {
        stubAdvisoryLock();
        Instant future = Instant.now().plus(1, ChronoUnit.HOURS);

        PublishCreditWeightsRequest request = new PublishCreditWeightsRequest();
        request.setEffectiveFrom(future);
        request.setEntries(List.of(entry(featureId.toString(), "gpt-4.1", "5")));

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(featureRepository.findByIdAndAccount(featureId, account)).thenReturn(Optional.of(feature));
        when(planFeatureRuleRepository.findPlanFeatureRulesByFeatureId(featureId)).thenReturn(List.of());
        when(creditFeatureWeightRepository.existsByAccountIdAndEffectiveFrom(accountId, future)).thenReturn(false);
        when(creditFeatureWeightRepository.saveAllAndFlush(any())).thenAnswer(invocation -> {
            List<CreditFeatureWeight> saved = invocation.getArgument(0);
            saved.forEach(w -> w.setId(UUID.randomUUID()));
            return saved;
        });

        List<CreditFeatureWeightDto> result = creditWeightService.publishWeights(request, accountId.toString(), UUID.randomUUID());

        assertEquals(1, result.size());
        assertEquals("gpt-4.1", result.get(0).getModel());
        assertEquals(new BigDecimal("5"), result.get(0).getCreditsPerUnit());
        verify(creditFeatureWeightRepository).saveAllAndFlush(any());
        verify(entityManager).createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:acct))");
    }

    @Test
    void publishWeights_pastEffectiveFrom_rejected() {
        PublishCreditWeightsRequest request = new PublishCreditWeightsRequest();
        request.setEffectiveFrom(Instant.now().minus(1, ChronoUnit.HOURS));
        request.setEntries(List.of(entry(featureId.toString(), null, "1")));

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        assertThrows(IllegalArgumentException.class,
                () -> creditWeightService.publishWeights(request, accountId.toString(), UUID.randomUUID()));
        verify(creditFeatureWeightRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void publishWeights_crossTenantFeature_rejected() {
        stubAdvisoryLock();
        PublishCreditWeightsRequest request = new PublishCreditWeightsRequest();
        request.setEffectiveFrom(Instant.now().plus(1, ChronoUnit.HOURS));
        request.setEntries(List.of(entry(featureId.toString(), null, "1")));

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        // Feature exists but not under this account — findByIdAndAccount returns empty
        when(featureRepository.findByIdAndAccount(featureId, account)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> creditWeightService.publishWeights(request, accountId.toString(), UUID.randomUUID()));
        verify(creditFeatureWeightRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void publishWeights_multipleDenominationsForFeature_rejected() {
        stubAdvisoryLock();
        PublishCreditWeightsRequest request = new PublishCreditWeightsRequest();
        request.setEffectiveFrom(Instant.now().plus(1, ChronoUnit.HOURS));
        request.setEntries(List.of(entry(featureId.toString(), null, "1")));

        CreditModel tokens = new CreditModel();
        tokens.setDenomination("tokens");
        CreditModel apiCredits = new CreditModel();
        apiCredits.setDenomination("api_credits");

        PlanFeatureRule ruleA = new PlanFeatureRule();
        ruleA.setCreditModel(tokens);
        PlanFeatureRule ruleB = new PlanFeatureRule();
        ruleB.setCreditModel(apiCredits);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(featureRepository.findByIdAndAccount(featureId, account)).thenReturn(Optional.of(feature));
        when(planFeatureRuleRepository.findPlanFeatureRulesByFeatureId(featureId)).thenReturn(List.of(ruleA, ruleB));

        assertThrows(IllegalArgumentException.class,
                () -> creditWeightService.publishWeights(request, accountId.toString(), UUID.randomUUID()));
        verify(creditFeatureWeightRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void publishWeights_duplicateEntryInBatch_rejected() {
        stubAdvisoryLock();
        PublishCreditWeightsRequest request = new PublishCreditWeightsRequest();
        request.setEffectiveFrom(Instant.now().plus(1, ChronoUnit.HOURS));
        request.setEntries(List.of(
                entry(featureId.toString(), "gpt-4.1", "1"),
                entry(featureId.toString(), "gpt-4.1", "2")));

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(featureRepository.findByIdAndAccount(featureId, account)).thenReturn(Optional.of(feature));
        when(planFeatureRuleRepository.findPlanFeatureRulesByFeatureId(featureId)).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class,
                () -> creditWeightService.publishWeights(request, accountId.toString(), UUID.randomUUID()));
    }

    @Test
    void publishWeights_zeroWeight_rejected() {
        stubAdvisoryLock();
        PublishCreditWeightsRequest request = new PublishCreditWeightsRequest();
        request.setEffectiveFrom(Instant.now().plus(1, ChronoUnit.HOURS));
        request.setEntries(List.of(entry(featureId.toString(), null, "0")));

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        assertThrows(IllegalArgumentException.class,
                () -> creditWeightService.publishWeights(request, accountId.toString(), UUID.randomUUID()));
    }

    @Test
    void publishWeights_exceedsMaxWeight_rejected() {
        stubAdvisoryLock();
        PublishCreditWeightsRequest request = new PublishCreditWeightsRequest();
        request.setEffectiveFrom(Instant.now().plus(1, ChronoUnit.HOURS));
        request.setEntries(List.of(entry(featureId.toString(), null, "1000001")));

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        assertThrows(IllegalArgumentException.class,
                () -> creditWeightService.publishWeights(request, accountId.toString(), UUID.randomUUID()));
    }

    @Test
    void publishWeights_tooManyDecimals_rejected() {
        stubAdvisoryLock();
        PublishCreditWeightsRequest request = new PublishCreditWeightsRequest();
        request.setEffectiveFrom(Instant.now().plus(1, ChronoUnit.HOURS));
        request.setEntries(List.of(entry(featureId.toString(), null, "1.1234567")));

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        assertThrows(IllegalArgumentException.class,
                () -> creditWeightService.publishWeights(request, accountId.toString(), UUID.randomUUID()));
    }

    @Test
    void publishWeights_idempotentReplay_returnsExistingRows() {
        stubAdvisoryLock();
        Instant future = Instant.now().plus(1, ChronoUnit.HOURS);

        PublishCreditWeightsRequest request = new PublishCreditWeightsRequest();
        request.setEffectiveFrom(future);
        request.setEntries(List.of(entry(featureId.toString(), "gpt-4.1", "5")));

        CreditFeatureWeight existingRow = new CreditFeatureWeight();
        existingRow.setId(UUID.randomUUID());
        existingRow.setFeature(feature);
        existingRow.setModel("gpt-4.1");
        existingRow.setCreditsPerUnit(new BigDecimal("5"));
        existingRow.setEffectiveFrom(future);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(featureRepository.findByIdAndAccount(featureId, account)).thenReturn(Optional.of(feature));
        when(planFeatureRuleRepository.findPlanFeatureRulesByFeatureId(featureId)).thenReturn(List.of());
        when(creditFeatureWeightRepository.existsByAccountIdAndEffectiveFrom(accountId, future)).thenReturn(true);
        when(creditFeatureWeightRepository.findByAccountIdOrderByFeatureIdAscModelAscEffectiveFromDesc(accountId))
                .thenReturn(List.of(existingRow));

        List<CreditFeatureWeightDto> result = creditWeightService.publishWeights(request, accountId.toString(), UUID.randomUUID());

        assertEquals(1, result.size());
        assertEquals(existingRow.getId().toString(), result.get(0).getId());
        verify(creditFeatureWeightRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void publishWeights_conflictingBatchAtSameEffectiveFrom_throws() {
        stubAdvisoryLock();
        Instant future = Instant.now().plus(1, ChronoUnit.HOURS);

        PublishCreditWeightsRequest request = new PublishCreditWeightsRequest();
        request.setEffectiveFrom(future);
        request.setEntries(List.of(entry(featureId.toString(), "gpt-4.1", "5")));

        CreditFeatureWeight existingRow = new CreditFeatureWeight();
        existingRow.setId(UUID.randomUUID());
        existingRow.setFeature(feature);
        existingRow.setModel("gpt-4.1");
        existingRow.setCreditsPerUnit(new BigDecimal("999")); // different weight — conflict
        existingRow.setEffectiveFrom(future);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(featureRepository.findByIdAndAccount(featureId, account)).thenReturn(Optional.of(feature));
        when(planFeatureRuleRepository.findPlanFeatureRulesByFeatureId(featureId)).thenReturn(List.of());
        when(creditFeatureWeightRepository.existsByAccountIdAndEffectiveFrom(accountId, future)).thenReturn(true);
        when(creditFeatureWeightRepository.findByAccountIdOrderByFeatureIdAscModelAscEffectiveFromDesc(accountId))
                .thenReturn(List.of(existingRow));

        assertThrows(TariffConflictException.class,
                () -> creditWeightService.publishWeights(request, accountId.toString(), UUID.randomUUID()));
    }

    // ── deleteScheduledWeight ────────────────────────────────────────────

    @Test
    void deleteScheduledWeight_futureRow_deletes() {
        UUID weightId = UUID.randomUUID();
        CreditFeatureWeight row = new CreditFeatureWeight();
        row.setId(weightId);
        row.setEffectiveFrom(Instant.now().plus(1, ChronoUnit.HOURS));

        when(creditFeatureWeightRepository.findByIdAndAccountId(weightId, accountId)).thenReturn(Optional.of(row));

        creditWeightService.deleteScheduledWeight(weightId.toString(), accountId.toString());

        verify(creditFeatureWeightRepository).delete(row);
    }

    @Test
    void deleteScheduledWeight_effectiveRow_rejected() {
        UUID weightId = UUID.randomUUID();
        CreditFeatureWeight row = new CreditFeatureWeight();
        row.setId(weightId);
        row.setEffectiveFrom(Instant.now().minus(1, ChronoUnit.HOURS));

        when(creditFeatureWeightRepository.findByIdAndAccountId(weightId, accountId)).thenReturn(Optional.of(row));

        assertThrows(IllegalArgumentException.class,
                () -> creditWeightService.deleteScheduledWeight(weightId.toString(), accountId.toString()));
        verify(creditFeatureWeightRepository, never()).delete(any());
    }

    // ── getObservedUnitCosts ─────────────────────────────────────────────

    @Test
    void getObservedUnitCosts_computesAveragePerFeatureModel() {
        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
        List<Object[]> rows = List.<Object[]>of(new Object[]{featureId, "gpt-4.1", new BigDecimal("100"), new BigDecimal("40")});
        when(eventRepository.sumCostAndUsageByFeatureAndModel(accountId, since))
                .thenReturn(rows);

        var result = creditWeightService.getObservedUnitCosts(accountId.toString(), since);

        assertEquals(new BigDecimal("2.500000"), result.get(featureId + "|gpt-4.1"));
    }

    @Test
    void getObservedUnitCosts_zeroUsage_skipped() {
        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
        List<Object[]> rows = List.<Object[]>of(new Object[]{featureId, "gpt-4.1", new BigDecimal("100"), BigDecimal.ZERO});
        when(eventRepository.sumCostAndUsageByFeatureAndModel(accountId, since))
                .thenReturn(rows);

        var result = creditWeightService.getObservedUnitCosts(accountId.toString(), since);

        assertEquals(0, result.size());
    }
}
