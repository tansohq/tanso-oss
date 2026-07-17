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
import com.tansoflow.tansocore.entity.CreditGrant;
import com.tansoflow.tansocore.entity.CreditModel;
import com.tansoflow.tansocore.entity.CreditPool;
import com.tansoflow.tansocore.entity.CreditPoolSubscription;
import com.tansoflow.tansocore.entity.CreditTransaction;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.entity.PlanCreditAllocation;
import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.mapper.credit.CreditMapper;
import com.tansoflow.tansocore.model.credit.CreditGrantDto;
import com.tansoflow.tansocore.model.credit.request.CreditDeductionRequest;
import com.tansoflow.tansocore.model.credit.type.CreditTransactionType;
import com.tansoflow.tansocore.model.credit.type.GrantType;
import com.tansoflow.tansocore.model.exception.CreditLimitExceededException;
import com.tansoflow.tansocore.repository.AccountRepository;
import com.tansoflow.tansocore.repository.CreditGrantRepository;
import com.tansoflow.tansocore.repository.CreditModelRepository;
import com.tansoflow.tansocore.repository.CreditPoolRepository;
import com.tansoflow.tansocore.repository.CreditPoolSubscriptionRepository;
import com.tansoflow.tansocore.repository.CreditTransactionRepository;
import com.tansoflow.tansocore.repository.CustomerRepository;
import com.tansoflow.tansocore.repository.PlanCreditAllocationRepository;
import com.tansoflow.tansocore.repository.PlanRepository;
import com.tansoflow.tansocore.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditServiceImplTest {

    @Mock
    private CreditPoolRepository creditPoolRepository;

    @Mock
    private CreditPoolSubscriptionRepository creditPoolSubscriptionRepository;

    @Mock
    private CreditGrantRepository creditGrantRepository;

    @Mock
    private CreditTransactionRepository creditTransactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private CreditModelRepository creditModelRepository;

    @Mock
    private PlanCreditAllocationRepository planCreditAllocationRepository;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private CreditMapper creditMapper;

    @InjectMocks
    private CreditServiceImpl creditService;

    private Account account;
    private Customer customer;
    private Plan plan;
    private Subscription subscription;
    private CreditModel creditModel;
    private PlanCreditAllocation creditAllocation;

    @BeforeEach
    void setUp() {
        account = new Account();
        account.setId(UUID.randomUUID());

        customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setAccount(account);

        plan = new Plan();
        plan.setId(UUID.randomUUID());
        plan.setAccount(account);
        plan.setCurrency("USD");
        plan.setIntervalMonths(1);

        subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setCustomer(customer);
        subscription.setPlan(plan);
        subscription.setAccount(account);
        subscription.setIsActive(true);
        subscription.setCurrentPeriodStart(Instant.now().minus(30, ChronoUnit.DAYS));
        subscription.setCurrentPeriodEnd(Instant.now());

        creditModel = new CreditModel();
        creditModel.setId(UUID.randomUUID());
        creditModel.setAccount(account);
        creditModel.setName("API Credits");
        creditModel.setDenomination("api_credits");
        creditModel.setHardLimit(true);
        creditModel.setRolloverPolicy("NONE");

        creditAllocation = new PlanCreditAllocation();
        creditAllocation.setId(UUID.randomUUID());
        creditAllocation.setPlan(plan);
        creditAllocation.setCreditModel(creditModel);
        creditAllocation.setAccount(account);
        creditAllocation.setCreditAmount(new BigDecimal("1000"));
    }

    // ── processCreditGrantsForSubscription tests ──────────────────────────────

    @Test
    void processCreditGrants_happyPath_createsPoolLinkAndGrant() {
        when(planCreditAllocationRepository.findByPlanIdAndDeletedAtIsNull(plan.getId()))
                .thenReturn(List.of(creditAllocation));
        when(creditPoolRepository.findByCustomerIdAndAccountIdAndDenomination(
                customer.getId(), account.getId(), "api_credits"))
                .thenReturn(Optional.empty());

        // Pool creation
        when(creditPoolRepository.saveAndFlush(any(CreditPool.class))).thenAnswer(i -> {
            CreditPool p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            p.setBalance(BigDecimal.ZERO);
            p.setTotalGranted(BigDecimal.ZERO);
            p.setTotalConsumed(BigDecimal.ZERO);
            p.setTotalExpired(BigDecimal.ZERO);
            p.setTotalReversed(BigDecimal.ZERO);
            return p;
        });

        when(creditPoolSubscriptionRepository.existsByCreditPoolIdAndSubscriptionIdAndDeletedAtIsNull(
                any(UUID.class), eq(subscription.getId())))
                .thenReturn(false);
        when(creditPoolSubscriptionRepository.saveAndFlush(any(CreditPoolSubscription.class)))
                .thenAnswer(i -> i.getArgument(0));

        when(creditGrantRepository.existsByAccountIdAndIdempotencyKeyAndDeletedAtIsNull(
                eq(account.getId()), any(String.class)))
                .thenReturn(false);

        when(subscriptionRepository.findById(subscription.getId()))
                .thenReturn(Optional.of(subscription));

        when(creditPoolRepository.findById(any(UUID.class))).thenAnswer(i -> {
            CreditPool p = new CreditPool();
            p.setId(i.getArgument(0));
            p.setAccount(account);
            p.setBalance(BigDecimal.ZERO);
            p.setTotalGranted(BigDecimal.ZERO);
            p.setTotalConsumed(BigDecimal.ZERO);
            p.setTotalExpired(BigDecimal.ZERO);
            p.setTotalReversed(BigDecimal.ZERO);
            p.setDenomination("api_credits");
            p.setVersion(0L);
            return Optional.of(p);
        });

        // For the grantCredits → retrievePool call
        when(creditPoolRepository.findByIdAndAccountId(any(UUID.class), eq(account.getId())))
                .thenAnswer(i -> {
                    CreditPool p = new CreditPool();
                    p.setId(i.getArgument(0));
                    p.setAccount(account);
                    p.setBalance(BigDecimal.ZERO);
                    p.setTotalGranted(BigDecimal.ZERO);
                    p.setTotalConsumed(BigDecimal.ZERO);
                    p.setTotalExpired(BigDecimal.ZERO);
                    p.setTotalReversed(BigDecimal.ZERO);
                    p.setDenomination("api_credits");
                    return Optional.of(p);
                });

        // Atomic balance update succeeds
        when(creditPoolRepository.updatePoolBalanceAtomically(any(UUID.class), any(BigDecimal.class), anyString(), anyLong()))
                .thenReturn(1);

        when(creditGrantRepository.saveAndFlush(any(CreditGrant.class))).thenAnswer(i -> {
            CreditGrant g = i.getArgument(0);
            g.setId(UUID.randomUUID());
            return g;
        });
        when(creditTransactionRepository.saveAndFlush(any(CreditTransaction.class)))
                .thenAnswer(i -> i.getArgument(0));
        when(creditMapper.creditGrantToDto(any())).thenReturn(new CreditGrantDto());

        // Execute
        creditService.processCreditGrantsForSubscription(subscription);

        // Verify pool created with correct denomination and hardLimit
        ArgumentCaptor<CreditPool> poolCaptor = ArgumentCaptor.forClass(CreditPool.class);
        verify(creditPoolRepository).saveAndFlush(poolCaptor.capture());
        CreditPool createdPool = poolCaptor.getValue();
        assertEquals("api_credits", createdPool.getDenomination());
        assertTrue(createdPool.getHardLimit());
        assertEquals("NONE", createdPool.getRolloverPolicy());

        // Verify link created
        verify(creditPoolSubscriptionRepository).saveAndFlush(any(CreditPoolSubscription.class));

        // Verify grant created with correct amount and type
        ArgumentCaptor<CreditGrant> grantCaptor = ArgumentCaptor.forClass(CreditGrant.class);
        verify(creditGrantRepository).saveAndFlush(grantCaptor.capture());
        CreditGrant createdGrant = grantCaptor.getValue();
        assertEquals(0, new BigDecimal("1000").compareTo(createdGrant.getAmount()));
        assertEquals(GrantType.PLAN_INCLUDED.name(), createdGrant.getGrantType());

        // Verify atomic balance update was used
        verify(creditPoolRepository).updatePoolBalanceAtomically(any(UUID.class), any(BigDecimal.class), eq("GRANT"), eq(0L));

        // Verify balance transaction created
        verify(creditTransactionRepository).saveAndFlush(any(CreditTransaction.class));
    }

    @Test
    void processCreditGrants_existingPool_reusesPool() {
        CreditPool existingPool = new CreditPool();
        existingPool.setId(UUID.randomUUID());
        existingPool.setAccount(account);
        existingPool.setCustomer(customer);
        existingPool.setDenomination("api_credits");
        existingPool.setBalance(new BigDecimal("500"));
        existingPool.setTotalGranted(new BigDecimal("500"));
        existingPool.setTotalConsumed(BigDecimal.ZERO);
        existingPool.setTotalExpired(BigDecimal.ZERO);
        existingPool.setTotalReversed(BigDecimal.ZERO);
        existingPool.setVersion(1L);

        when(planCreditAllocationRepository.findByPlanIdAndDeletedAtIsNull(plan.getId()))
                .thenReturn(List.of(creditAllocation));
        when(creditPoolRepository.findByCustomerIdAndAccountIdAndDenomination(
                customer.getId(), account.getId(), "api_credits"))
                .thenReturn(Optional.of(existingPool));
        when(creditPoolSubscriptionRepository.existsByCreditPoolIdAndSubscriptionIdAndDeletedAtIsNull(
                existingPool.getId(), subscription.getId()))
                .thenReturn(false);
        when(creditPoolSubscriptionRepository.saveAndFlush(any(CreditPoolSubscription.class)))
                .thenAnswer(i -> i.getArgument(0));
        when(creditGrantRepository.existsByAccountIdAndIdempotencyKeyAndDeletedAtIsNull(
                eq(account.getId()), any(String.class)))
                .thenReturn(false);
        when(subscriptionRepository.findById(subscription.getId()))
                .thenReturn(Optional.of(subscription));
        when(creditPoolRepository.findByIdAndAccountId(existingPool.getId(), account.getId()))
                .thenReturn(Optional.of(existingPool));
        when(creditPoolRepository.findById(existingPool.getId()))
                .thenReturn(Optional.of(existingPool));
        when(creditPoolRepository.updatePoolBalanceAtomically(eq(existingPool.getId()), any(BigDecimal.class), eq("GRANT"), eq(1L)))
                .thenReturn(1);
        when(creditGrantRepository.saveAndFlush(any(CreditGrant.class))).thenAnswer(i -> {
            CreditGrant g = i.getArgument(0);
            g.setId(UUID.randomUUID());
            return g;
        });
        when(creditTransactionRepository.saveAndFlush(any(CreditTransaction.class)))
                .thenAnswer(i -> i.getArgument(0));
        when(creditMapper.creditGrantToDto(any())).thenReturn(new CreditGrantDto());

        creditService.processCreditGrantsForSubscription(subscription);

        // Pool saveAndFlush should NOT be called (no pool creation, atomic update used for balance)
        verify(creditPoolRepository, never()).saveAndFlush(any(CreditPool.class));
        // Verify atomic update was used
        verify(creditPoolRepository).updatePoolBalanceAtomically(eq(existingPool.getId()), any(BigDecimal.class), eq("GRANT"), eq(1L));
    }

    @Test
    void processCreditGrants_existingLink_skipsLinkCreation() {
        CreditPool existingPool = new CreditPool();
        existingPool.setId(UUID.randomUUID());
        existingPool.setAccount(account);
        existingPool.setDenomination("api_credits");
        existingPool.setBalance(BigDecimal.ZERO);
        existingPool.setTotalGranted(BigDecimal.ZERO);
        existingPool.setTotalConsumed(BigDecimal.ZERO);
        existingPool.setTotalExpired(BigDecimal.ZERO);
        existingPool.setTotalReversed(BigDecimal.ZERO);
        existingPool.setVersion(0L);

        when(planCreditAllocationRepository.findByPlanIdAndDeletedAtIsNull(plan.getId()))
                .thenReturn(List.of(creditAllocation));
        when(creditPoolRepository.findByCustomerIdAndAccountIdAndDenomination(
                customer.getId(), account.getId(), "api_credits"))
                .thenReturn(Optional.of(existingPool));
        when(creditPoolSubscriptionRepository.existsByCreditPoolIdAndSubscriptionIdAndDeletedAtIsNull(
                existingPool.getId(), subscription.getId()))
                .thenReturn(true);
        when(creditGrantRepository.existsByAccountIdAndIdempotencyKeyAndDeletedAtIsNull(
                eq(account.getId()), any(String.class)))
                .thenReturn(false);
        when(subscriptionRepository.findById(subscription.getId()))
                .thenReturn(Optional.of(subscription));
        when(creditPoolRepository.findByIdAndAccountId(existingPool.getId(), account.getId()))
                .thenReturn(Optional.of(existingPool));
        when(creditPoolRepository.findById(existingPool.getId()))
                .thenReturn(Optional.of(existingPool));
        when(creditPoolRepository.updatePoolBalanceAtomically(eq(existingPool.getId()), any(BigDecimal.class), eq("GRANT"), eq(0L)))
                .thenReturn(1);
        when(creditGrantRepository.saveAndFlush(any(CreditGrant.class))).thenAnswer(i -> {
            CreditGrant g = i.getArgument(0);
            g.setId(UUID.randomUUID());
            return g;
        });
        when(creditTransactionRepository.saveAndFlush(any(CreditTransaction.class)))
                .thenAnswer(i -> i.getArgument(0));
        when(creditMapper.creditGrantToDto(any())).thenReturn(new CreditGrantDto());

        creditService.processCreditGrantsForSubscription(subscription);

        verify(creditPoolSubscriptionRepository, never()).saveAndFlush(any(CreditPoolSubscription.class));
    }

    @Test
    void processCreditGrants_idempotencyHit_skipsGrant() {
        CreditPool existingPool = new CreditPool();
        existingPool.setId(UUID.randomUUID());
        existingPool.setAccount(account);
        existingPool.setDenomination("api_credits");

        when(planCreditAllocationRepository.findByPlanIdAndDeletedAtIsNull(plan.getId()))
                .thenReturn(List.of(creditAllocation));
        when(creditPoolRepository.findByCustomerIdAndAccountIdAndDenomination(
                customer.getId(), account.getId(), "api_credits"))
                .thenReturn(Optional.of(existingPool));
        when(creditPoolSubscriptionRepository.existsByCreditPoolIdAndSubscriptionIdAndDeletedAtIsNull(
                existingPool.getId(), subscription.getId()))
                .thenReturn(true);
        when(creditGrantRepository.existsByAccountIdAndIdempotencyKeyAndDeletedAtIsNull(
                eq(account.getId()), any(String.class)))
                .thenReturn(true);

        creditService.processCreditGrantsForSubscription(subscription);

        verify(creditGrantRepository, never()).saveAndFlush(any(CreditGrant.class));
    }

    @Test
    void processCreditGrants_noAllocations_doesNothing() {
        when(planCreditAllocationRepository.findByPlanIdAndDeletedAtIsNull(plan.getId()))
                .thenReturn(List.of());

        creditService.processCreditGrantsForSubscription(subscription);

        verify(creditPoolRepository, never()).saveAndFlush(any());
        verify(creditPoolSubscriptionRepository, never()).saveAndFlush(any());
        verify(creditGrantRepository, never()).saveAndFlush(any());
    }

    @Test
    void processCreditGrants_zeroAmount_skipped() {
        PlanCreditAllocation zeroAllocation = new PlanCreditAllocation();
        zeroAllocation.setId(UUID.randomUUID());
        zeroAllocation.setPlan(plan);
        zeroAllocation.setCreditModel(creditModel);
        zeroAllocation.setAccount(account);
        zeroAllocation.setCreditAmount(BigDecimal.ZERO);

        when(planCreditAllocationRepository.findByPlanIdAndDeletedAtIsNull(plan.getId()))
                .thenReturn(List.of(zeroAllocation));

        creditService.processCreditGrantsForSubscription(subscription);

        verify(creditPoolRepository, never()).saveAndFlush(any());
    }

    @Test
    void processCreditGrants_withGrantExpiry_setsExpiresAt() {
        PlanCreditAllocation expiryAllocation = new PlanCreditAllocation();
        expiryAllocation.setId(UUID.randomUUID());
        expiryAllocation.setPlan(plan);
        expiryAllocation.setCreditModel(creditModel);
        expiryAllocation.setAccount(account);
        expiryAllocation.setCreditAmount(new BigDecimal("500"));
        expiryAllocation.setGrantExpiresMonths(3);

        CreditPool existingPool = new CreditPool();
        existingPool.setId(UUID.randomUUID());
        existingPool.setAccount(account);
        existingPool.setDenomination("api_credits");
        existingPool.setBalance(BigDecimal.ZERO);
        existingPool.setTotalGranted(BigDecimal.ZERO);
        existingPool.setTotalConsumed(BigDecimal.ZERO);
        existingPool.setTotalExpired(BigDecimal.ZERO);
        existingPool.setTotalReversed(BigDecimal.ZERO);
        existingPool.setVersion(0L);

        when(planCreditAllocationRepository.findByPlanIdAndDeletedAtIsNull(plan.getId()))
                .thenReturn(List.of(expiryAllocation));
        when(creditPoolRepository.findByCustomerIdAndAccountIdAndDenomination(
                customer.getId(), account.getId(), "api_credits"))
                .thenReturn(Optional.of(existingPool));
        when(creditPoolSubscriptionRepository.existsByCreditPoolIdAndSubscriptionIdAndDeletedAtIsNull(
                existingPool.getId(), subscription.getId()))
                .thenReturn(true);
        when(creditGrantRepository.existsByAccountIdAndIdempotencyKeyAndDeletedAtIsNull(
                eq(account.getId()), any(String.class)))
                .thenReturn(false);
        when(subscriptionRepository.findById(subscription.getId()))
                .thenReturn(Optional.of(subscription));
        when(creditPoolRepository.findByIdAndAccountId(existingPool.getId(), account.getId()))
                .thenReturn(Optional.of(existingPool));
        when(creditPoolRepository.findById(existingPool.getId()))
                .thenReturn(Optional.of(existingPool));
        when(creditPoolRepository.updatePoolBalanceAtomically(eq(existingPool.getId()), any(BigDecimal.class), eq("GRANT"), eq(0L)))
                .thenReturn(1);
        when(creditGrantRepository.saveAndFlush(any(CreditGrant.class))).thenAnswer(i -> {
            CreditGrant g = i.getArgument(0);
            g.setId(UUID.randomUUID());
            return g;
        });
        when(creditTransactionRepository.saveAndFlush(any(CreditTransaction.class)))
                .thenAnswer(i -> i.getArgument(0));
        when(creditMapper.creditGrantToDto(any())).thenReturn(new CreditGrantDto());

        Instant before = Instant.now();
        creditService.processCreditGrantsForSubscription(subscription);

        ArgumentCaptor<CreditGrant> grantCaptor = ArgumentCaptor.forClass(CreditGrant.class);
        verify(creditGrantRepository).saveAndFlush(grantCaptor.capture());
        CreditGrant grant = grantCaptor.getValue();
        assertNotNull(grant.getExpiresAt());
        // Should be approximately 3 months from now
        Instant expected = before.atOffset(java.time.ZoneOffset.UTC).plusMonths(3).toInstant();
        long diffSeconds = Math.abs(grant.getExpiresAt().getEpochSecond() - expected.getEpochSecond());
        assertTrue(diffSeconds < 60, "expiresAt should be ~3 months from now, diff was " + diffSeconds + "s");
    }

    // ── clawBackPlanIncludedCredits tests ─────────────────────────────────────

    @Test
    void clawBackPlanIncludedCredits_voidsAndDeducts() {
        CreditPool pool = new CreditPool();
        pool.setId(UUID.randomUUID());
        pool.setAccount(account);
        pool.setDenomination("api_credits");
        pool.setBalance(new BigDecimal("500"));
        pool.setTotalGranted(new BigDecimal("1000"));
        pool.setTotalConsumed(new BigDecimal("500"));
        pool.setTotalExpired(BigDecimal.ZERO);
        pool.setTotalReversed(BigDecimal.ZERO);
        pool.setHardLimit(false);
        pool.setVersion(2L);

        CreditPoolSubscription link = new CreditPoolSubscription();
        link.setCreditPool(pool);
        link.setSubscription(subscription);

        CreditGrant activeGrant = new CreditGrant();
        activeGrant.setId(UUID.randomUUID());
        activeGrant.setCreditPool(pool);
        activeGrant.setRemaining(new BigDecimal("500"));

        when(creditPoolSubscriptionRepository.findBySubscriptionId(subscription.getId()))
                .thenReturn(List.of(link));
        when(creditGrantRepository.findActivePlanIncludedGrants(pool.getId(), subscription.getId()))
                .thenReturn(List.of(activeGrant));
        when(creditPoolRepository.findById(pool.getId())).thenReturn(Optional.of(pool));
        when(creditPoolRepository.updatePoolBalanceAtomically(eq(pool.getId()), any(BigDecimal.class), eq("DEDUCTION"), eq(2L)))
                .thenReturn(1);
        when(creditTransactionRepository.saveAndFlush(any(CreditTransaction.class)))
                .thenAnswer(i -> i.getArgument(0));

        creditService.clawBackPlanIncludedCredits(subscription.getId(), account.getId());

        // Grant should be voided
        assertNotNull(activeGrant.getVoidedAt());
        assertEquals(BigDecimal.ZERO, activeGrant.getRemaining());
        verify(creditGrantRepository).save(activeGrant);

        // Pool balance should be reduced via atomic update
        verify(creditPoolRepository).updatePoolBalanceAtomically(eq(pool.getId()), any(BigDecimal.class), eq("DEDUCTION"), eq(2L));

        // Transaction should be recorded
        ArgumentCaptor<CreditTransaction> txCaptor = ArgumentCaptor.forClass(CreditTransaction.class);
        verify(creditTransactionRepository).saveAndFlush(txCaptor.capture());
        CreditTransaction tx = txCaptor.getValue();
        assertEquals(CreditTransactionType.DEDUCTION.name(), tx.getTransactionType());
    }

    // ── applyRolloverPolicy tests ─────────────────────────────────────────────

    @Test
    void applyRolloverPolicy_none_expiresAll() {
        CreditPool pool = new CreditPool();
        pool.setId(UUID.randomUUID());
        pool.setAccount(account);
        pool.setRolloverPolicy("NONE");
        pool.setBalance(new BigDecimal("800"));
        pool.setTotalGranted(new BigDecimal("1000"));
        pool.setTotalConsumed(new BigDecimal("200"));
        pool.setTotalExpired(BigDecimal.ZERO);
        pool.setTotalReversed(BigDecimal.ZERO);
        pool.setHardLimit(false);
        pool.setVersion(3L);

        when(creditPoolRepository.findById(pool.getId())).thenReturn(Optional.of(pool));
        when(creditGrantRepository.findActiveGrantsByPoolIdOrderByCreatedAsc(pool.getId()))
                .thenReturn(List.of());
        when(creditPoolRepository.updatePoolBalanceAtomically(eq(pool.getId()), any(BigDecimal.class), eq("EXPIRATION"), eq(3L)))
                .thenReturn(1);
        when(creditTransactionRepository.saveAndFlush(any(CreditTransaction.class)))
                .thenAnswer(i -> i.getArgument(0));

        creditService.applyRolloverPolicy(pool.getId());

        ArgumentCaptor<CreditTransaction> txCaptor = ArgumentCaptor.forClass(CreditTransaction.class);
        verify(creditTransactionRepository).saveAndFlush(txCaptor.capture());
        CreditTransaction tx = txCaptor.getValue();
        assertEquals(CreditTransactionType.EXPIRATION.name(), tx.getTransactionType());
        assertEquals(0, new BigDecimal("-800").compareTo(tx.getAmount()));
    }

    @Test
    void applyRolloverPolicy_full_noAction() {
        CreditPool pool = new CreditPool();
        pool.setId(UUID.randomUUID());
        pool.setAccount(account);
        pool.setRolloverPolicy("FULL");
        pool.setBalance(new BigDecimal("800"));

        when(creditPoolRepository.findById(pool.getId())).thenReturn(Optional.of(pool));

        creditService.applyRolloverPolicy(pool.getId());

        verify(creditTransactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void applyRolloverPolicy_capped_expiresExcess() {
        CreditPool pool = new CreditPool();
        pool.setId(UUID.randomUUID());
        pool.setAccount(account);
        pool.setRolloverPolicy("CAPPED");
        pool.setRolloverCap(new BigDecimal("500"));
        pool.setBalance(new BigDecimal("800"));
        pool.setTotalGranted(new BigDecimal("1000"));
        pool.setTotalConsumed(new BigDecimal("200"));
        pool.setTotalExpired(BigDecimal.ZERO);
        pool.setTotalReversed(BigDecimal.ZERO);
        pool.setHardLimit(false);
        pool.setVersion(4L);

        when(creditPoolRepository.findById(pool.getId())).thenReturn(Optional.of(pool));
        when(creditPoolRepository.updatePoolBalanceAtomically(eq(pool.getId()), any(BigDecimal.class), eq("EXPIRATION"), eq(4L)))
                .thenReturn(1);
        when(creditTransactionRepository.saveAndFlush(any(CreditTransaction.class)))
                .thenAnswer(i -> i.getArgument(0));

        creditService.applyRolloverPolicy(pool.getId());

        ArgumentCaptor<CreditTransaction> txCaptor = ArgumentCaptor.forClass(CreditTransaction.class);
        verify(creditTransactionRepository).saveAndFlush(txCaptor.capture());
        CreditTransaction tx = txCaptor.getValue();
        assertEquals(CreditTransactionType.EXPIRATION.name(), tx.getTransactionType());
        // 800 - 500 = 300 excess → amount = -300
        assertEquals(0, new BigDecimal("-300").compareTo(tx.getAmount()));
    }

    // ── checkHardLimitForSubscription tests ───────────────────────────────────

    @Test
    void checkHardLimit_nullDenomination_returnsTrue() {
        boolean result = creditService.checkHardLimitForSubscription(
                subscription.getId(), account.getId(), null, BigDecimal.TEN);
        assertTrue(result);
    }

    @Test
    void checkHardLimit_sufficientCredits_returnsTrue() {
        CreditPool pool = new CreditPool();
        pool.setId(UUID.randomUUID());
        pool.setHardLimit(true);
        pool.setBalance(new BigDecimal("100"));

        CreditPoolSubscription link = new CreditPoolSubscription();
        link.setCreditPool(pool);

        when(creditPoolSubscriptionRepository.findBySubscriptionIdAndAccountIdAndDenominationOrderByDrawPriority(
                subscription.getId(), account.getId(), "api_credits"))
                .thenReturn(List.of(link));

        boolean result = creditService.checkHardLimitForSubscription(
                subscription.getId(), account.getId(), "api_credits", BigDecimal.TEN);
        assertTrue(result);
    }

    @Test
    void checkHardLimit_insufficientCredits_returnsFalse() {
        CreditPool pool = new CreditPool();
        pool.setId(UUID.randomUUID());
        pool.setHardLimit(true);
        pool.setBalance(new BigDecimal("5"));

        CreditPoolSubscription link = new CreditPoolSubscription();
        link.setCreditPool(pool);

        when(creditPoolSubscriptionRepository.findBySubscriptionIdAndAccountIdAndDenominationOrderByDrawPriority(
                subscription.getId(), account.getId(), "api_credits"))
                .thenReturn(List.of(link));

        boolean result = creditService.checkHardLimitForSubscription(
                subscription.getId(), account.getId(), "api_credits", BigDecimal.TEN);
        assertFalse(result);
    }

    @Test
    void checkHardLimit_multiPoolSufficientSum_returnsTrue() {
        CreditPool pool1 = new CreditPool();
        pool1.setId(UUID.randomUUID());
        pool1.setHardLimit(true);
        pool1.setBalance(new BigDecimal("5"));

        CreditPool pool2 = new CreditPool();
        pool2.setId(UUID.randomUUID());
        pool2.setHardLimit(false);
        pool2.setBalance(new BigDecimal("10"));

        CreditPoolSubscription link1 = new CreditPoolSubscription();
        link1.setCreditPool(pool1);
        CreditPoolSubscription link2 = new CreditPoolSubscription();
        link2.setCreditPool(pool2);

        when(creditPoolSubscriptionRepository.findBySubscriptionIdAndAccountIdAndDenominationOrderByDrawPriority(
                subscription.getId(), account.getId(), "api_credits"))
                .thenReturn(List.of(link1, link2));

        // Combined balance = 15, required = 10 → passes
        boolean result = creditService.checkHardLimitForSubscription(
                subscription.getId(), account.getId(), "api_credits", BigDecimal.TEN);
        assertTrue(result);
    }

    @Test
    void checkHardLimit_drawLimitReducesAvailableCredits() {
        CreditPool pool = new CreditPool();
        pool.setId(UUID.randomUUID());
        pool.setHardLimit(true);
        pool.setBalance(new BigDecimal("100"));

        CreditPoolSubscription link = new CreditPoolSubscription();
        link.setCreditPool(pool);
        link.setDrawLimit(new BigDecimal("5"));
        link.setTotalDrawn(BigDecimal.ZERO);

        when(creditPoolSubscriptionRepository.findBySubscriptionIdAndAccountIdAndDenominationOrderByDrawPriority(
                subscription.getId(), account.getId(), "api_credits"))
                .thenReturn(List.of(link));

        boolean result = creditService.checkHardLimitForSubscription(
                subscription.getId(), account.getId(), "api_credits", BigDecimal.TEN);

        assertFalse(result);
    }

    @Test
    void deductCredits_hardLimitExceeded_ThrowsDomainException() {
        CreditPool pool = new CreditPool();
        pool.setId(UUID.randomUUID());
        pool.setAccount(account);
        pool.setHardLimit(true);
        pool.setBalance(new BigDecimal("5"));

        CreditDeductionRequest request = new CreditDeductionRequest();
        request.setCreditPoolId(pool.getId().toString());
        request.setAmount(BigDecimal.TEN);

        when(creditPoolRepository.findByIdAndAccountId(pool.getId(), account.getId()))
                .thenReturn(Optional.of(pool));

        assertThrows(CreditLimitExceededException.class,
                () -> creditService.deductCredits(request, account.getId().toString()));
        verify(creditTransactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void deductCredits_concurrentHardLimitDepletion_ThrowsAfterOptimisticLockRetry() {
        CreditPool initialPool = new CreditPool();
        initialPool.setId(UUID.randomUUID());
        initialPool.setAccount(account);
        initialPool.setHardLimit(true);
        initialPool.setBalance(BigDecimal.TEN);
        initialPool.setVersion(1L);

        CreditPool concurrentlyDepletedPool = new CreditPool();
        concurrentlyDepletedPool.setId(initialPool.getId());
        concurrentlyDepletedPool.setAccount(account);
        concurrentlyDepletedPool.setHardLimit(true);
        concurrentlyDepletedPool.setBalance(new BigDecimal("5"));
        concurrentlyDepletedPool.setVersion(2L);

        CreditDeductionRequest request = new CreditDeductionRequest();
        request.setCreditPoolId(initialPool.getId().toString());
        request.setAmount(BigDecimal.TEN);

        when(creditPoolRepository.findByIdAndAccountId(initialPool.getId(), account.getId()))
                .thenReturn(Optional.of(initialPool));
        when(creditGrantRepository.findActiveGrantsByPoolIdOrderByCreatedAsc(initialPool.getId()))
                .thenReturn(List.of());
        when(creditPoolRepository.findById(initialPool.getId()))
                .thenReturn(Optional.of(initialPool), Optional.of(concurrentlyDepletedPool));
        when(creditPoolRepository.updatePoolBalanceAtomically(
                initialPool.getId(), BigDecimal.TEN.negate(), CreditTransactionType.DEDUCTION.name(), 1L))
                .thenReturn(0);

        assertThrows(CreditLimitExceededException.class,
                () -> creditService.deductCredits(request, account.getId().toString()));

        verify(creditPoolRepository).updatePoolBalanceAtomically(
                initialPool.getId(), BigDecimal.TEN.negate(), CreditTransactionType.DEDUCTION.name(), 1L);
        verify(creditTransactionRepository, never()).saveAndFlush(any());
    }

    // ── reverseTransaction restores grant remaining ──────────────────────────

    @Test
    void reverseTransaction_deduction_restoresGrantRemaining() {
        UUID txId = UUID.randomUUID();

        CreditPool pool = new CreditPool();
        pool.setId(UUID.randomUUID());
        pool.setAccount(account);
        pool.setBalance(new BigDecimal("500"));
        pool.setVersion(5L);

        CreditGrant grant = new CreditGrant();
        grant.setId(UUID.randomUUID());
        grant.setCreditPool(pool);
        grant.setRemaining(BigDecimal.ZERO);
        grant.setVoidedAt(Instant.now());

        // CreditTransaction has class-level @Setter(NONE) so use a mock for the id
        CreditTransaction originalTx = Mockito.mock(CreditTransaction.class);
        when(originalTx.getId()).thenReturn(txId);
        when(originalTx.getCreditPool()).thenReturn(pool);
        when(originalTx.getAccount()).thenReturn(account);
        when(originalTx.getTransactionType()).thenReturn("DEDUCTION");
        when(originalTx.getAmount()).thenReturn(new BigDecimal("-100"));
        when(originalTx.getCreditGrant()).thenReturn(grant);
        when(originalTx.getSubscriptionId()).thenReturn(null);
        when(originalTx.getCustomerId()).thenReturn(null);
        when(originalTx.getEventId()).thenReturn(null);

        when(creditTransactionRepository.findById(txId))
                .thenReturn(Optional.of(originalTx));
        when(creditPoolRepository.findById(pool.getId()))
                .thenReturn(Optional.of(pool));
        when(creditPoolRepository.updatePoolBalanceAtomically(eq(pool.getId()), any(BigDecimal.class), eq("REVERSAL"), eq(5L)))
                .thenReturn(1);
        when(creditTransactionRepository.saveAndFlush(any(CreditTransaction.class)))
                .thenAnswer(i -> i.getArgument(0));
        when(creditMapper.creditTransactionToDto(any())).thenReturn(null);

        creditService.reverseTransaction(txId.toString(), "Test reversal", account.getId().toString());

        // Grant remaining should be restored
        assertEquals(0, new BigDecimal("100").compareTo(grant.getRemaining()));
        assertNull(grant.getVoidedAt());
        verify(creditGrantRepository).save(grant);
    }

    // ── OCC retry behavior ───────────────────────────────────────────────────

    @Test
    void occRetry_failsAfterMaxAttempts_throwsException() {
        CreditPool pool = new CreditPool();
        pool.setId(UUID.randomUUID());
        pool.setAccount(account);
        pool.setBalance(new BigDecimal("100"));
        pool.setVersion(0L);

        when(creditPoolRepository.findByIdAndAccountId(pool.getId(), account.getId()))
                .thenReturn(Optional.of(pool));

        when(creditPoolRepository.findById(pool.getId()))
                .thenReturn(Optional.of(pool));
        // Always fail the atomic update
        when(creditPoolRepository.updatePoolBalanceAtomically(eq(pool.getId()), any(BigDecimal.class), anyString(), anyLong()))
                .thenReturn(0);

        when(creditGrantRepository.saveAndFlush(any(CreditGrant.class))).thenAnswer(i -> {
            CreditGrant g = i.getArgument(0);
            g.setId(UUID.randomUUID());
            return g;
        });

        var request = new com.tansoflow.tansocore.model.credit.request.CreditGrantRequest();
        request.setCreditPoolId(pool.getId().toString());
        request.setAmount(new BigDecimal("50"));
        request.setGrantType("MANUAL");

        assertThrows(IllegalStateException.class, () ->
                creditService.grantCredits(request, account.getId().toString()));
    }

    // ── applyRolloverPoliciesForSubscription tests ───────────────────────────

    @Test
    void applyRolloverPoliciesForSubscription_appliesRolloverToEachPool() {
        CreditPool pool1 = new CreditPool();
        pool1.setId(UUID.randomUUID());
        pool1.setAccount(account);
        pool1.setRolloverPolicy("FULL");
        pool1.setBalance(new BigDecimal("100"));

        CreditPool pool2 = new CreditPool();
        pool2.setId(UUID.randomUUID());
        pool2.setAccount(account);
        pool2.setRolloverPolicy("FULL");
        pool2.setBalance(new BigDecimal("200"));

        CreditPoolSubscription link1 = new CreditPoolSubscription();
        link1.setCreditPool(pool1);
        CreditPoolSubscription link2 = new CreditPoolSubscription();
        link2.setCreditPool(pool2);

        when(creditPoolSubscriptionRepository.findBySubscriptionId(subscription.getId()))
                .thenReturn(List.of(link1, link2));
        when(creditPoolRepository.findById(pool1.getId())).thenReturn(Optional.of(pool1));
        when(creditPoolRepository.findById(pool2.getId())).thenReturn(Optional.of(pool2));

        creditService.applyRolloverPoliciesForSubscription(subscription.getId());

        // FULL rollover = no transactions, just verify both pools were read
        verify(creditPoolRepository).findById(pool1.getId());
        verify(creditPoolRepository).findById(pool2.getId());
    }
}
