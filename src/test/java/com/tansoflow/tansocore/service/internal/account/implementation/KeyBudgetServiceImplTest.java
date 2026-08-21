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
package com.tansoflow.tansocore.service.internal.account.implementation;

import com.tansoflow.tansocore.entity.AccountApiKey;
import com.tansoflow.tansocore.entity.ApiKeySpendRecord;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.model.apikey.KeyBudgetDto;
import com.tansoflow.tansocore.model.apikey.request.UpdateKeyBudgetRequest;
import com.tansoflow.tansocore.model.apikey.type.BudgetPeriod;
import com.tansoflow.tansocore.model.apikey.type.SpendKind;
import com.tansoflow.tansocore.model.exception.BudgetExceededException;
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.repository.AccountApiKeyRepository;
import com.tansoflow.tansocore.repository.ApiKeySpendRecordRepository;
import com.tansoflow.tansocore.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeyBudgetServiceImplTest {

    @Mock
    private AccountApiKeyRepository accountApiKeyRepository;
    @Mock
    private ApiKeySpendRecordRepository spendRecordRepository;
    @Mock
    private CustomerRepository customerRepository;

    private KeyBudgetServiceImpl service;

    private final UUID accountId = UUID.randomUUID();
    private final UUID keyId = UUID.randomUUID();
    private AccountApiKey key;
    private Customer customer;

    @BeforeEach
    void setUp() {
        service = new KeyBudgetServiceImpl(accountApiKeyRepository, spendRecordRepository, customerRepository);

        customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setExternalClientCustomerId("cust_1");

        key = new AccountApiKey();
        key.setId(keyId);
        key.setCustomer(customer);
        key.setBudgetPeriod(BudgetPeriod.MONTH);
        key.setBudgetStartedAt(Instant.now().minus(Duration.ofDays(2)));
        key.setBudgetCredits(new BigDecimal("1000"));
        key.setBudgetAmount(new BigDecimal("200.00"));
    }

    @Test
    void spendUnderTheLimitIsAllowed() {
        when(accountApiKeyRepository.findById(keyId)).thenReturn(Optional.of(key));
        when(spendRecordRepository.sumSince(eq(keyId), eq(SpendKind.CREDITS), any()))
                .thenReturn(new BigDecimal("400"));

        service.assertWithinBudget(keyId, SpendKind.CREDITS, new BigDecimal("100"));
    }

    @Test
    void spendThatWouldCrossTheLimitIsRejectedWithTheNumbers() {
        when(accountApiKeyRepository.findById(keyId)).thenReturn(Optional.of(key));
        when(spendRecordRepository.sumSince(eq(keyId), eq(SpendKind.CREDITS), any()))
                .thenReturn(new BigDecimal("950"));

        assertThatThrownBy(() -> service.assertWithinBudget(keyId, SpendKind.CREDITS, new BigDecimal("100")))
                .isInstanceOf(BudgetExceededException.class)
                .satisfies(thrown -> {
                    BudgetExceededException e = (BudgetExceededException) thrown;
                    assertThat(e.getLimit()).isEqualByComparingTo("1000");
                    assertThat(e.getSpent()).isEqualByComparingTo("950");
                    assertThat(e.getRemaining()).isEqualByComparingTo("50");
                    assertThat(e.getResetsAt()).isNotNull();
                });
    }

    @Test
    void spendExactlyAtTheLimitIsAllowed() {
        when(accountApiKeyRepository.findById(keyId)).thenReturn(Optional.of(key));
        when(spendRecordRepository.sumSince(eq(keyId), eq(SpendKind.CREDITS), any()))
                .thenReturn(new BigDecimal("900"));

        service.assertWithinBudget(keyId, SpendKind.CREDITS, new BigDecimal("100"));
    }

    @Test
    void theTwoAxesAreIndependent() {
        when(accountApiKeyRepository.findById(keyId)).thenReturn(Optional.of(key));
        lenient().when(spendRecordRepository.sumSince(eq(keyId), eq(SpendKind.CREDITS), any()))
                .thenReturn(new BigDecimal("999"));
        when(spendRecordRepository.sumSince(eq(keyId), eq(SpendKind.MONEY), any()))
                .thenReturn(new BigDecimal("10.00"));

        // Credits are nearly exhausted; a money spend is still fine.
        service.assertWithinBudget(keyId, SpendKind.MONEY, new BigDecimal("50.00"));
    }

    @Test
    void anUnsetAxisIsUnlimited() {
        key.setBudgetCredits(null);
        when(accountApiKeyRepository.findById(keyId)).thenReturn(Optional.of(key));

        service.assertWithinBudget(keyId, SpendKind.CREDITS, new BigDecimal("999999"));

        verify(spendRecordRepository, never()).sumSince(any(), any(), any());
    }

    @Test
    void jwtCallersHaveNoKeyAndAreNotBudgeted() {
        service.assertWithinBudget(null, SpendKind.CREDITS, new BigDecimal("999999"));

        verify(accountApiKeyRepository, never()).findById(any());
    }

    @Test
    void monthlyWindowTilesForwardFromTheDayTheBudgetWasSet() {
        Instant anchor = Instant.now().minus(Duration.ofDays(65));
        key.setBudgetStartedAt(anchor);
        when(accountApiKeyRepository.findById(keyId)).thenReturn(Optional.of(key));
        when(spendRecordRepository.sumSince(eq(keyId), eq(SpendKind.CREDITS), any())).thenReturn(BigDecimal.ZERO);

        service.assertWithinBudget(keyId, SpendKind.CREDITS, BigDecimal.ONE);

        ArgumentCaptor<Instant> windowStart = ArgumentCaptor.forClass(Instant.class);
        verify(spendRecordRepository).sumSince(eq(keyId), eq(SpendKind.CREDITS), windowStart.capture());
        // Two 30-day windows have elapsed, so the current one opened at anchor + 60d.
        assertThat(windowStart.getValue()).isEqualTo(anchor.plus(Duration.ofDays(60)));
    }

    @Test
    void aTotalBudgetNeverResets() {
        key.setBudgetPeriod(BudgetPeriod.TOTAL);
        Instant anchor = Instant.now().minus(Duration.ofDays(400));
        key.setBudgetStartedAt(anchor);
        when(spendRecordRepository.sumSince(any(), any(), any())).thenReturn(new BigDecimal("100"));

        KeyBudgetDto dto = service.describe(key);

        assertThat(dto.getWindowStart()).isEqualTo(anchor);
        assertThat(dto.getResetsAt()).isNull();
    }

    @Test
    void describeReportsRemainingOnBothAxes() {
        when(spendRecordRepository.sumSince(eq(keyId), eq(SpendKind.CREDITS), any()))
                .thenReturn(new BigDecimal("250"));
        when(spendRecordRepository.sumSince(eq(keyId), eq(SpendKind.MONEY), any()))
                .thenReturn(new BigDecimal("75.00"));

        KeyBudgetDto dto = service.describe(key);

        assertThat(dto.getCreditsRemaining()).isEqualByComparingTo("750");
        assertThat(dto.getAmountRemaining()).isEqualByComparingTo("125.00");
        assertThat(dto.getResetsAt()).isNotNull();
    }

    @Test
    void remainingIsClampedAtZeroWhenSpendOverranTheLimit() {
        when(spendRecordRepository.sumSince(eq(keyId), eq(SpendKind.CREDITS), any()))
                .thenReturn(new BigDecimal("1200"));
        when(spendRecordRepository.sumSince(eq(keyId), eq(SpendKind.MONEY), any()))
                .thenReturn(BigDecimal.ZERO);

        assertThat(service.describe(key).getCreditsRemaining()).isEqualByComparingTo("0");
    }

    @Test
    void spendIsRecordedAgainstTheKey() {
        service.recordSpend(accountId, keyId, SpendKind.CREDITS, new BigDecimal("12.5"), "event_1", "event:1");

        ArgumentCaptor<ApiKeySpendRecord> saved = ArgumentCaptor.forClass(ApiKeySpendRecord.class);
        verify(spendRecordRepository).save(saved.capture());
        assertThat(saved.getValue().getApiKeyId()).isEqualTo(keyId);
        assertThat(saved.getValue().getKind()).isEqualTo(SpendKind.CREDITS);
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("12.5");
    }

    @Test
    void replayedSpendIsNotCountedTwice() {
        when(spendRecordRepository.existsByAccountIdAndIdempotencyKey(accountId, "event:1")).thenReturn(true);

        service.recordSpend(accountId, keyId, SpendKind.CREDITS, new BigDecimal("12.5"), "event_1", "event:1");

        verify(spendRecordRepository, never()).save(any());
    }

    @Test
    void spendWithNoKeyIsIgnored() {
        service.recordSpend(accountId, null, SpendKind.MONEY, new BigDecimal("5"), null, null);

        verify(spendRecordRepository, never()).save(any());
    }

    @Test
    void changingThePeriodRestartsTheWindow() {
        Instant oldAnchor = Instant.now().minus(Duration.ofDays(10));
        key.setBudgetStartedAt(oldAnchor);
        stubKeyLookup();
        when(spendRecordRepository.sumSince(any(), any(), any())).thenReturn(BigDecimal.ZERO);

        UpdateKeyBudgetRequest request = new UpdateKeyBudgetRequest();
        request.setPeriod(BudgetPeriod.DAY);
        request.setCreditLimit(new BigDecimal("500"));

        service.setBudget(accountId.toString(), "cust_1", keyId.toString(), request);

        assertThat(key.getBudgetStartedAt()).isAfter(oldAnchor);
        assertThat(key.getBudgetPeriod()).isEqualTo(BudgetPeriod.DAY);
        assertThat(key.getBudgetAmount()).isNull();
    }

    @Test
    void raisingALimitWithinTheSamePeriodKeepsTheWindow() {
        Instant anchor = Instant.now().minus(Duration.ofDays(10));
        key.setBudgetStartedAt(anchor);
        stubKeyLookup();
        when(spendRecordRepository.sumSince(any(), any(), any())).thenReturn(BigDecimal.ZERO);

        UpdateKeyBudgetRequest request = new UpdateKeyBudgetRequest();
        request.setPeriod(BudgetPeriod.MONTH);
        request.setCreditLimit(new BigDecimal("5000"));

        service.setBudget(accountId.toString(), "cust_1", keyId.toString(), request);

        assertThat(key.getBudgetStartedAt()).isEqualTo(anchor);
        assertThat(key.getBudgetCredits()).isEqualByComparingTo("5000");
    }

    @Test
    void negativeLimitsAreRejected() {
        UpdateKeyBudgetRequest request = new UpdateKeyBudgetRequest();
        request.setPeriod(BudgetPeriod.MONTH);
        request.setCreditLimit(new BigDecimal("-1"));

        assertThatThrownBy(() -> service.setBudget(accountId.toString(), "cust_1", keyId.toString(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("creditLimit");
    }

    @Test
    void aKeyBelongingToAnotherCustomerIsNotFound() {
        Customer other = new Customer();
        other.setId(UUID.randomUUID());
        key.setCustomer(other);
        when(customerRepository.getCustomerByReferenceIdAndAccountId("cust_1", accountId))
                .thenReturn(Optional.of(customer));
        when(accountApiKeyRepository.findByIdAndAccountId(keyId, accountId)).thenReturn(Optional.of(key));

        assertThatThrownBy(() -> service.getBudget(accountId.toString(), "cust_1", keyId.toString()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void clearingRemovesBothAxesAndTheAnchor() {
        stubKeyLookup();

        service.clearBudget(accountId.toString(), "cust_1", keyId.toString());

        assertThat(key.getBudgetCredits()).isNull();
        assertThat(key.getBudgetAmount()).isNull();
        assertThat(key.getBudgetPeriod()).isNull();
        assertThat(key.getBudgetStartedAt()).isNull();
    }

    private void stubKeyLookup() {
        when(customerRepository.getCustomerByReferenceIdAndAccountId("cust_1", accountId))
                .thenReturn(Optional.of(customer));
        when(accountApiKeyRepository.findByIdAndAccountId(keyId, accountId)).thenReturn(Optional.of(key));
    }

    // ─── Spend alerts ───

    @Test
    void aKeyReportsHowFarThroughItsTightestLimitItIs() {
        key.setBudgetAlertThreshold(80);
        // 900/1000 credits is 90%; $10/$200 is 5%. The credit axis is the one
        // that will refuse the next call, so it is the one reported.
        when(spendRecordRepository.sumSince(eq(keyId), eq(SpendKind.CREDITS), any()))
                .thenReturn(new BigDecimal("900"));
        when(spendRecordRepository.sumSince(eq(keyId), eq(SpendKind.MONEY), any()))
                .thenReturn(new BigDecimal("10.00"));

        KeyBudgetDto dto = service.describe(key);

        assertThat(dto.getPercentUsed()).isEqualTo(90);
        assertThat(dto.getAlertThreshold()).isEqualTo(80);
    }

    @Test
    void spendThatCrossesTheThresholdIsStamped() {
        key.setBudgetAlertThreshold(80);
        when(accountApiKeyRepository.findById(keyId)).thenReturn(Optional.of(key));
        when(spendRecordRepository.sumSince(eq(keyId), eq(SpendKind.CREDITS), any()))
                .thenReturn(new BigDecimal("850"));
        when(spendRecordRepository.sumSince(eq(keyId), eq(SpendKind.MONEY), any()))
                .thenReturn(BigDecimal.ZERO);

        service.recordSpend(accountId, keyId, SpendKind.CREDITS, new BigDecimal("50"), "e", "idem-cross");

        assertThat(key.getBudgetAlertAt()).isNotNull();
    }

    @Test
    void spendBelowTheThresholdIsNotStamped() {
        key.setBudgetAlertThreshold(80);
        when(accountApiKeyRepository.findById(keyId)).thenReturn(Optional.of(key));
        when(spendRecordRepository.sumSince(eq(keyId), eq(SpendKind.CREDITS), any()))
                .thenReturn(new BigDecimal("100"));
        when(spendRecordRepository.sumSince(eq(keyId), eq(SpendKind.MONEY), any()))
                .thenReturn(BigDecimal.ZERO);

        service.recordSpend(accountId, keyId, SpendKind.CREDITS, new BigDecimal("10"), "e", "idem-under");

        assertThat(key.getBudgetAlertAt()).isNull();
    }

    @Test
    void aKeyWithNoThresholdNeverAlerts() {
        key.setBudgetAlertThreshold(null);
        when(accountApiKeyRepository.findById(keyId)).thenReturn(Optional.of(key));

        service.recordSpend(accountId, keyId, SpendKind.CREDITS, new BigDecimal("999"), "e", "idem-none");

        assertThat(key.getBudgetAlertAt()).isNull();
        verify(spendRecordRepository, never()).sumSince(any(), any(), any());
    }

    @Test
    void aStampFromAnEarlierWindowIsNotCurrentNews() {
        key.setBudgetAlertThreshold(80);
        key.setBudgetStartedAt(Instant.now().minus(Duration.ofDays(2)));
        key.setBudgetPeriod(BudgetPeriod.DAY);
        // Crossed yesterday, in a window that has since rolled over.
        key.setBudgetAlertAt(Instant.now().minus(Duration.ofDays(1).plusHours(2)));
        when(spendRecordRepository.sumSince(any(), any(), any())).thenReturn(BigDecimal.ZERO);

        KeyBudgetDto dto = service.describe(key);

        assertThat(dto.isAlerting()).isFalse();
        assertThat(dto.getAlertingSince()).isNull();
    }

    @Test
    void changingALimitDoesNotSilentlyTurnAlertingOff() {
        key.setBudgetAlertThreshold(65);
        stubKeyLookup();
        when(spendRecordRepository.sumSince(any(), any(), any())).thenReturn(BigDecimal.ZERO);

        UpdateKeyBudgetRequest request = new UpdateKeyBudgetRequest();
        request.setPeriod(BudgetPeriod.MONTH);
        request.setCreditLimit(new BigDecimal("2000"));
        // No threshold named — the operator is changing a limit, not the alerting.

        service.setBudget(accountId.toString(), "cust_1", keyId.toString(), request);

        assertThat(key.getBudgetAlertThreshold()).isEqualTo(65);
    }

    @Test
    void aBudgetSetForTheFirstTimeAlertsAtEighty() {
        key.setBudgetPeriod(null);
        key.setBudgetAlertThreshold(null);
        stubKeyLookup();
        when(spendRecordRepository.sumSince(any(), any(), any())).thenReturn(BigDecimal.ZERO);

        UpdateKeyBudgetRequest request = new UpdateKeyBudgetRequest();
        request.setPeriod(BudgetPeriod.MONTH);
        request.setCreditLimit(new BigDecimal("100"));

        service.setBudget(accountId.toString(), "cust_1", keyId.toString(), request);

        assertThat(key.getBudgetAlertThreshold()).isEqualTo(80);
    }

    @Test
    void zeroTurnsAlertingOff() {
        key.setBudgetAlertThreshold(80);
        stubKeyLookup();
        when(spendRecordRepository.sumSince(any(), any(), any())).thenReturn(BigDecimal.ZERO);

        UpdateKeyBudgetRequest request = new UpdateKeyBudgetRequest();
        request.setPeriod(BudgetPeriod.MONTH);
        request.setCreditLimit(new BigDecimal("100"));
        request.setAlertThreshold(0);

        service.setBudget(accountId.toString(), "cust_1", keyId.toString(), request);

        assertThat(key.getBudgetAlertThreshold()).isNull();
    }

    @Test
    void anOutOfRangeThresholdIsRejected() {
        UpdateKeyBudgetRequest request = new UpdateKeyBudgetRequest();
        request.setPeriod(BudgetPeriod.MONTH);
        request.setAlertThreshold(140);

        assertThatThrownBy(() -> service.setBudget(accountId.toString(), "cust_1", keyId.toString(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alertThreshold");
    }
}
