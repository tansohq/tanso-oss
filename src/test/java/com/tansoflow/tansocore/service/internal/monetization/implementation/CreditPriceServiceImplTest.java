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
import com.tansoflow.tansocore.entity.CreditModel;
import com.tansoflow.tansocore.entity.CreditPrice;
import com.tansoflow.tansocore.model.credit.CreditPriceDto;
import com.tansoflow.tansocore.model.credit.request.PublishCreditPricesRequest;
import com.tansoflow.tansocore.model.exception.TariffConflictException;
import com.tansoflow.tansocore.repository.AccountRepository;
import com.tansoflow.tansocore.repository.CreditModelRepository;
import com.tansoflow.tansocore.repository.CreditPriceRepository;
import com.tansoflow.tansocore.service.internal.monetization.CreditPriceService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditPriceServiceImplTest {

    @Mock
    private CreditPriceRepository creditPriceRepository;
    @Mock
    private CreditModelRepository creditModelRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private EntityManager entityManager;
    @Mock
    private Query lockQuery;

    @InjectMocks
    private CreditPriceServiceImpl creditPriceService;

    private UUID accountId;
    private Account account;
    private CreditModel creditModel;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();

        account = new Account();
        account.setId(accountId);

        creditModel = new CreditModel();
        creditModel.setId(UUID.randomUUID());
        creditModel.setDenomination("tokens");

        // @PersistenceContext field isn't part of the Lombok constructor, so @InjectMocks
        // doesn't wire it via constructor injection — set it explicitly.
        ReflectionTestUtils.setField(creditPriceService, "entityManager", entityManager);
    }

    private void stubAdvisoryLock() {
        lenient().when(entityManager.createNativeQuery(anyString())).thenReturn(lockQuery);
        lenient().when(lockQuery.setParameter(anyString(), any())).thenReturn(lockQuery);
        lenient().when(lockQuery.getSingleResult()).thenReturn(true);
    }

    // ── resolvePrice ─────────────────────────────────────────────────────

    @Test
    void resolvePrice_rowExists_returnsPrice() {
        Instant at = Instant.now();
        CreditPrice row = new CreditPrice();
        row.setId(UUID.randomUUID());
        row.setPricePerCredit(new BigDecimal("0.10"));
        row.setCurrency("USD");

        when(creditPriceRepository
                .findTopByAccountIdAndDenominationAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        accountId, "tokens", at))
                .thenReturn(Optional.of(row));

        Optional<CreditPriceService.ResolvedPrice> result = creditPriceService.resolvePrice(accountId, "tokens", at);

        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("0.10"), result.get().pricePerCredit());
        assertEquals("USD", result.get().currency());
        assertEquals(row.getId(), result.get().priceId());
    }

    @Test
    void resolvePrice_noRow_returnsEmpty() {
        Instant at = Instant.now();
        when(creditPriceRepository
                .findTopByAccountIdAndDenominationAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        accountId, "tokens", at))
                .thenReturn(Optional.empty());

        assertTrue(creditPriceService.resolvePrice(accountId, "tokens", at).isEmpty());
    }

    // ── getCurrentPrices ─────────────────────────────────────────────────

    @Test
    void getCurrentPrices_picksLatestEffectivePerDenomination_skipsScheduled() {
        CreditPrice newTokens = new CreditPrice();
        newTokens.setId(UUID.randomUUID());
        newTokens.setDenomination("tokens");
        newTokens.setCurrency("USD");
        newTokens.setPricePerCredit(new BigDecimal("0.12"));
        newTokens.setEffectiveFrom(Instant.now().minus(1, ChronoUnit.DAYS));

        CreditPrice oldTokens = new CreditPrice();
        oldTokens.setId(UUID.randomUUID());
        oldTokens.setDenomination("tokens");
        oldTokens.setCurrency("USD");
        oldTokens.setPricePerCredit(new BigDecimal("0.10"));
        oldTokens.setEffectiveFrom(Instant.now().minus(30, ChronoUnit.DAYS));

        CreditPrice scheduledCredits = new CreditPrice();
        scheduledCredits.setId(UUID.randomUUID());
        scheduledCredits.setDenomination("api_credits");
        scheduledCredits.setCurrency("USD");
        scheduledCredits.setPricePerCredit(new BigDecimal("1"));
        scheduledCredits.setEffectiveFrom(Instant.now().plus(1, ChronoUnit.DAYS));

        // Repository order: denomination ASC, effectiveFrom DESC
        when(creditPriceRepository.findByAccountIdOrderByDenominationAscEffectiveFromDesc(accountId))
                .thenReturn(List.of(scheduledCredits, newTokens, oldTokens));

        List<CreditPriceDto> result = creditPriceService.getCurrentPrices(accountId.toString());

        assertEquals(1, result.size());
        assertEquals("tokens", result.get(0).getDenomination());
        assertEquals(new BigDecimal("0.12"), result.get(0).getPricePerCredit());
    }

    // ── publishPrices ────────────────────────────────────────────────────

    private PublishCreditPricesRequest.Entry entry(String denomination, String currency, String price) {
        PublishCreditPricesRequest.Entry e = new PublishCreditPricesRequest.Entry();
        e.setDenomination(denomination);
        e.setCurrency(currency);
        e.setPricePerCredit(new BigDecimal(price));
        return e;
    }

    @Test
    void publishPrices_happyPath_createsRow() {
        stubAdvisoryLock();
        Instant future = Instant.now().plus(1, ChronoUnit.HOURS);

        PublishCreditPricesRequest request = new PublishCreditPricesRequest();
        request.setEffectiveFrom(future);
        request.setEntries(List.of(entry("tokens", "USD", "0.12")));

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(creditModelRepository.findByAccountIdAndDenomination(accountId, "tokens"))
                .thenReturn(Optional.of(creditModel));
        when(creditPriceRepository.existsByAccountIdAndEffectiveFrom(accountId, future)).thenReturn(false);
        when(creditPriceRepository.saveAllAndFlush(any())).thenAnswer(invocation -> {
            List<CreditPrice> saved = invocation.getArgument(0);
            saved.forEach(p -> p.setId(UUID.randomUUID()));
            return saved;
        });

        List<CreditPriceDto> result = creditPriceService.publishPrices(request, accountId.toString(), UUID.randomUUID());

        assertEquals(1, result.size());
        assertEquals("tokens", result.get(0).getDenomination());
        assertEquals("USD", result.get(0).getCurrency());
        assertEquals(new BigDecimal("0.12"), result.get(0).getPricePerCredit());
        verify(creditPriceRepository).saveAllAndFlush(any());
        verify(entityManager).createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:acct))");
    }

    @Test
    void publishPrices_nullCurrency_defaultsToUsd() {
        stubAdvisoryLock();
        Instant future = Instant.now().plus(1, ChronoUnit.HOURS);

        PublishCreditPricesRequest request = new PublishCreditPricesRequest();
        request.setEffectiveFrom(future);
        request.setEntries(List.of(entry("tokens", null, "0.12")));

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(creditModelRepository.findByAccountIdAndDenomination(accountId, "tokens"))
                .thenReturn(Optional.of(creditModel));
        when(creditPriceRepository.existsByAccountIdAndEffectiveFrom(accountId, future)).thenReturn(false);
        when(creditPriceRepository.saveAllAndFlush(any())).thenAnswer(invocation -> {
            List<CreditPrice> saved = invocation.getArgument(0);
            saved.forEach(p -> p.setId(UUID.randomUUID()));
            return saved;
        });

        List<CreditPriceDto> result = creditPriceService.publishPrices(request, accountId.toString(), UUID.randomUUID());

        assertEquals("USD", result.get(0).getCurrency());
    }

    @Test
    void publishPrices_pastEffectiveFrom_rejected() {
        PublishCreditPricesRequest request = new PublishCreditPricesRequest();
        request.setEffectiveFrom(Instant.now().minus(1, ChronoUnit.HOURS));
        request.setEntries(List.of(entry("tokens", "USD", "0.12")));

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        assertThrows(IllegalArgumentException.class,
                () -> creditPriceService.publishPrices(request, accountId.toString(), UUID.randomUUID()));
        verify(creditPriceRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void publishPrices_unknownDenomination_rejected() {
        stubAdvisoryLock();
        PublishCreditPricesRequest request = new PublishCreditPricesRequest();
        request.setEffectiveFrom(Instant.now().plus(1, ChronoUnit.HOURS));
        request.setEntries(List.of(entry("nonexistent", "USD", "0.12")));

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(creditModelRepository.findByAccountIdAndDenomination(accountId, "nonexistent"))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> creditPriceService.publishPrices(request, accountId.toString(), UUID.randomUUID()));
        verify(creditPriceRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void publishPrices_duplicateDenominationInBatch_rejected() {
        stubAdvisoryLock();
        PublishCreditPricesRequest request = new PublishCreditPricesRequest();
        request.setEffectiveFrom(Instant.now().plus(1, ChronoUnit.HOURS));
        request.setEntries(List.of(
                entry("tokens", "USD", "0.10"),
                entry("tokens", "USD", "0.20")));

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(creditModelRepository.findByAccountIdAndDenomination(accountId, "tokens"))
                .thenReturn(Optional.of(creditModel));

        assertThrows(IllegalArgumentException.class,
                () -> creditPriceService.publishPrices(request, accountId.toString(), UUID.randomUUID()));
    }

    @Test
    void publishPrices_zeroPrice_rejected() {
        stubAdvisoryLock();
        PublishCreditPricesRequest request = new PublishCreditPricesRequest();
        request.setEffectiveFrom(Instant.now().plus(1, ChronoUnit.HOURS));
        request.setEntries(List.of(entry("tokens", "USD", "0")));

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(creditModelRepository.findByAccountIdAndDenomination(accountId, "tokens"))
                .thenReturn(Optional.of(creditModel));

        assertThrows(IllegalArgumentException.class,
                () -> creditPriceService.publishPrices(request, accountId.toString(), UUID.randomUUID()));
    }

    @Test
    void publishPrices_exceedsMaxPrice_rejected() {
        stubAdvisoryLock();
        PublishCreditPricesRequest request = new PublishCreditPricesRequest();
        request.setEffectiveFrom(Instant.now().plus(1, ChronoUnit.HOURS));
        request.setEntries(List.of(entry("tokens", "USD", "1000001")));

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(creditModelRepository.findByAccountIdAndDenomination(accountId, "tokens"))
                .thenReturn(Optional.of(creditModel));

        assertThrows(IllegalArgumentException.class,
                () -> creditPriceService.publishPrices(request, accountId.toString(), UUID.randomUUID()));
    }

    @Test
    void publishPrices_tooManyDecimals_rejected() {
        stubAdvisoryLock();
        PublishCreditPricesRequest request = new PublishCreditPricesRequest();
        request.setEffectiveFrom(Instant.now().plus(1, ChronoUnit.HOURS));
        request.setEntries(List.of(entry("tokens", "USD", "0.1234567")));

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(creditModelRepository.findByAccountIdAndDenomination(accountId, "tokens"))
                .thenReturn(Optional.of(creditModel));

        assertThrows(IllegalArgumentException.class,
                () -> creditPriceService.publishPrices(request, accountId.toString(), UUID.randomUUID()));
    }

    @Test
    void publishPrices_invalidCurrency_rejected() {
        stubAdvisoryLock();
        PublishCreditPricesRequest request = new PublishCreditPricesRequest();
        request.setEffectiveFrom(Instant.now().plus(1, ChronoUnit.HOURS));
        request.setEntries(List.of(entry("tokens", "DOLLARS", "0.12")));

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(creditModelRepository.findByAccountIdAndDenomination(accountId, "tokens"))
                .thenReturn(Optional.of(creditModel));

        assertThrows(IllegalArgumentException.class,
                () -> creditPriceService.publishPrices(request, accountId.toString(), UUID.randomUUID()));
    }

    @Test
    void publishPrices_idempotentReplay_returnsExistingRows() {
        stubAdvisoryLock();
        Instant future = Instant.now().plus(1, ChronoUnit.HOURS);

        PublishCreditPricesRequest request = new PublishCreditPricesRequest();
        request.setEffectiveFrom(future);
        request.setEntries(List.of(entry("tokens", "USD", "0.12")));

        CreditPrice existingRow = new CreditPrice();
        existingRow.setId(UUID.randomUUID());
        existingRow.setDenomination("tokens");
        existingRow.setCurrency("USD");
        existingRow.setPricePerCredit(new BigDecimal("0.12"));
        existingRow.setEffectiveFrom(future);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(creditModelRepository.findByAccountIdAndDenomination(accountId, "tokens"))
                .thenReturn(Optional.of(creditModel));
        when(creditPriceRepository.existsByAccountIdAndEffectiveFrom(accountId, future)).thenReturn(true);
        when(creditPriceRepository.findByAccountIdOrderByDenominationAscEffectiveFromDesc(accountId))
                .thenReturn(List.of(existingRow));

        List<CreditPriceDto> result = creditPriceService.publishPrices(request, accountId.toString(), UUID.randomUUID());

        assertEquals(1, result.size());
        assertEquals(existingRow.getId().toString(), result.get(0).getId());
        verify(creditPriceRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void publishPrices_conflictingBatchAtSameEffectiveFrom_throws() {
        stubAdvisoryLock();
        Instant future = Instant.now().plus(1, ChronoUnit.HOURS);

        PublishCreditPricesRequest request = new PublishCreditPricesRequest();
        request.setEffectiveFrom(future);
        request.setEntries(List.of(entry("tokens", "USD", "0.12")));

        CreditPrice existingRow = new CreditPrice();
        existingRow.setId(UUID.randomUUID());
        existingRow.setDenomination("tokens");
        existingRow.setCurrency("USD");
        existingRow.setPricePerCredit(new BigDecimal("999")); // different price — conflict
        existingRow.setEffectiveFrom(future);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(creditModelRepository.findByAccountIdAndDenomination(accountId, "tokens"))
                .thenReturn(Optional.of(creditModel));
        when(creditPriceRepository.existsByAccountIdAndEffectiveFrom(accountId, future)).thenReturn(true);
        when(creditPriceRepository.findByAccountIdOrderByDenominationAscEffectiveFromDesc(accountId))
                .thenReturn(List.of(existingRow));

        assertThrows(TariffConflictException.class,
                () -> creditPriceService.publishPrices(request, accountId.toString(), UUID.randomUUID()));
    }

    // ── deleteScheduledPrice ─────────────────────────────────────────────

    @Test
    void deleteScheduledPrice_futureRow_deletes() {
        UUID priceId = UUID.randomUUID();
        CreditPrice row = new CreditPrice();
        row.setId(priceId);
        row.setEffectiveFrom(Instant.now().plus(1, ChronoUnit.HOURS));

        when(creditPriceRepository.findByIdAndAccountId(priceId, accountId)).thenReturn(Optional.of(row));

        creditPriceService.deleteScheduledPrice(priceId.toString(), accountId.toString());

        verify(creditPriceRepository).delete(row);
    }

    @Test
    void deleteScheduledPrice_effectiveRow_rejected() {
        UUID priceId = UUID.randomUUID();
        CreditPrice row = new CreditPrice();
        row.setId(priceId);
        row.setEffectiveFrom(Instant.now().minus(1, ChronoUnit.HOURS));

        when(creditPriceRepository.findByIdAndAccountId(priceId, accountId)).thenReturn(Optional.of(row));

        assertThrows(IllegalArgumentException.class,
                () -> creditPriceService.deleteScheduledPrice(priceId.toString(), accountId.toString()));
        verify(creditPriceRepository, never()).delete(any());
    }
}
