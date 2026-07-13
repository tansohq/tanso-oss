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
import com.tansoflow.tansocore.model.credit.CreditModelDto;
import com.tansoflow.tansocore.model.credit.CreditPoolDto;
import com.tansoflow.tansocore.model.credit.CreditTransactionDto;
import com.tansoflow.tansocore.model.credit.PlanCreditAllocationDto;
import com.tansoflow.tansocore.model.credit.request.CreateCreditModelRequest;
import com.tansoflow.tansocore.model.credit.request.CreateCreditPoolRequest;
import com.tansoflow.tansocore.model.credit.request.CreditDeductionRequest;
import com.tansoflow.tansocore.model.credit.request.CreditGrantRequest;
import com.tansoflow.tansocore.model.credit.type.CreditPoolStatus;
import com.tansoflow.tansocore.model.credit.type.CreditTransactionType;
import com.tansoflow.tansocore.model.credit.type.GrantType;
import com.tansoflow.tansocore.model.credit.type.RolloverPolicy;
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
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
import com.tansoflow.tansocore.service.internal.monetization.CreditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CreditServiceImpl implements CreditService {
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final CreditPoolRepository creditPoolRepository;
    private final CreditPoolSubscriptionRepository creditPoolSubscriptionRepository;
    private final CreditGrantRepository creditGrantRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final CreditModelRepository creditModelRepository;
    private final PlanCreditAllocationRepository planCreditAllocationRepository;
    private final PlanRepository planRepository;
    private final CreditMapper creditMapper;

    // ─── Credit Model CRUD ───

    @Override
    @Transactional
    public CreditModelDto createCreditModel(CreateCreditModelRequest request, String accountId) {
        Account account = accountRepository.findById(UUID.fromString(accountId))
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));

        if (creditModelRepository.findByAccountIdAndDenomination(account.getId(), request.getDenomination()).isPresent()) {
            throw new IllegalArgumentException("Credit model with denomination '" + request.getDenomination() + "' already exists");
        }

        CreditModel model = new CreditModel();
        model.setAccount(account);
        model.setName(request.getName());
        model.setDenomination(request.getDenomination());
        model.setDescription(request.getDescription());

        if (request.getHardLimit() != null) {
            model.setHardLimit(request.getHardLimit());
        }
        if (request.getRolloverPolicy() != null) {
            RolloverPolicy.valueOf(request.getRolloverPolicy()); // validate enum
            model.setRolloverPolicy(request.getRolloverPolicy());
        }
        model.setRolloverCap(request.getRolloverCap());

        creditModelRepository.saveAndFlush(model);
        log.info("Created credit model '{}' (denomination: {}) for account {}", model.getName(), model.getDenomination(), accountId);
        return creditMapper.creditModelToDto(model);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CreditModelDto> getCreditModelsByAccount(String accountId) {
        List<CreditModel> models = creditModelRepository.findByAccountId(UUID.fromString(accountId));
        return creditMapper.creditModelListToDtoList(models);
    }

    @Override
    @Transactional(readOnly = true)
    public CreditModelDto getCreditModel(String id, String accountId) {
        CreditModel model = creditModelRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("Credit model not found: " + id));
        if (!model.getAccount().getId().toString().equals(accountId)) {
            throw new ResourceNotFoundException("Credit model not found for account: " + accountId);
        }
        return creditMapper.creditModelToDto(model);
    }

    @Override
    @Transactional
    public void deleteCreditModel(String id, String accountId) {
        CreditModel model = creditModelRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("Credit model not found: " + id));
        if (!model.getAccount().getId().toString().equals(accountId)) {
            throw new ResourceNotFoundException("Credit model not found for account: " + accountId);
        }
        creditModelRepository.delete(model); // soft delete via @SQLDelete
        log.info("Deleted credit model '{}' for account {}", model.getName(), accountId);
    }

    // ─── Credit Model — Plan Allocation ───

    @Override
    @Transactional
    public void addCreditAllocationToPlan(String planId, String creditModelId, BigDecimal creditAmount, Integer grantExpiresMonths, Boolean hardLimit, String accountId) {
        Plan plan = planRepository.findById(UUID.fromString(planId))
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + planId));
        CreditModel creditModel = creditModelRepository.findById(UUID.fromString(creditModelId))
                .orElseThrow(() -> new ResourceNotFoundException("Credit model not found: " + creditModelId));
        Account account = accountRepository.findById(UUID.fromString(accountId))
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));

        if (!plan.getAccount().getId().equals(account.getId())) {
            throw new ResourceNotFoundException("Plan not found for account: " + accountId);
        }
        if (!creditModel.getAccount().getId().equals(account.getId())) {
            throw new ResourceNotFoundException("Credit model not found for account: " + accountId);
        }

        if (planCreditAllocationRepository.findByPlanIdAndCreditModelIdAndDeletedAtIsNull(plan.getId(), creditModel.getId()).isPresent()) {
            throw new IllegalArgumentException("Allocation already exists for this plan and credit model");
        }

        PlanCreditAllocation allocation = new PlanCreditAllocation();
        allocation.setPlan(plan);
        allocation.setCreditModel(creditModel);
        allocation.setAccount(account);
        allocation.setCreditAmount(creditAmount);
        allocation.setGrantExpiresMonths(grantExpiresMonths);
        allocation.setHardLimit(hardLimit);

        planCreditAllocationRepository.saveAndFlush(allocation);
        log.info("Added credit allocation of {} {} to plan {}", creditAmount, creditModel.getDenomination(), planId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanCreditAllocationDto> getCreditAllocationsForPlan(String planId, String accountId) {
        List<PlanCreditAllocation> allocations = planCreditAllocationRepository.findByPlanIdAndDeletedAtIsNull(UUID.fromString(planId));
        return creditMapper.planCreditAllocationListToDtoList(allocations);
    }

    @Override
    @Transactional
    public void removeCreditAllocationFromPlan(String planId, String creditModelId, String accountId) {
        PlanCreditAllocation allocation = planCreditAllocationRepository
                .findByPlanIdAndCreditModelIdAndDeletedAtIsNull(UUID.fromString(planId), UUID.fromString(creditModelId))
                .orElseThrow(() -> new ResourceNotFoundException("Allocation not found for plan " + planId + " and credit model " + creditModelId));
        planCreditAllocationRepository.delete(allocation); // soft delete via @SQLDelete
        log.info("Removed credit allocation from plan {} for credit model {}", planId, creditModelId);
    }

    // ─── Pool CRUD ───

    @Override
    @Transactional
    public CreditPoolDto createCreditPool(CreateCreditPoolRequest request, String accountId) {
        Account account = accountRepository.findById(UUID.fromString(accountId))
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));

        if (creditPoolRepository.existsByAccountIdAndNameAndDeletedAtIsNull(account.getId(), request.getName())) {
            throw new IllegalArgumentException("Credit pool with name '" + request.getName() + "' already exists");
        }

        CreditPool pool = new CreditPool();
        pool.setAccount(account);
        pool.setName(request.getName());
        pool.setDenomination(request.getDenomination());
        pool.setCurrency(request.getCurrency());

        if (request.getCustomerId() != null) {
            Customer customer = customerRepository.findById(UUID.fromString(request.getCustomerId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + request.getCustomerId()));
            pool.setCustomer(customer);
        }

        if (request.getHardLimit() != null) {
            pool.setHardLimit(request.getHardLimit());
        }
        if (request.getRolloverPolicy() != null) {
            RolloverPolicy.valueOf(request.getRolloverPolicy()); // validate enum
            pool.setRolloverPolicy(request.getRolloverPolicy());
        }
        pool.setRolloverCap(request.getRolloverCap());
        if (request.getMetadata() != null) {
            pool.setMetadata(request.getMetadata());
        }

        creditPoolRepository.saveAndFlush(pool);
        log.info("Created credit pool '{}' for account {}", pool.getName(), accountId);
        return creditMapper.creditPoolToDto(pool);
    }

    @Override
    @Transactional(readOnly = true)
    public CreditPoolDto getCreditPool(String poolId, String accountId) {
        CreditPool pool = retrievePool(poolId, accountId);
        return creditMapper.creditPoolToDto(pool);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CreditPoolDto> getCreditPoolsByAccount(String accountId) {
        List<CreditPool> pools = creditPoolRepository.findAllByAccountId(UUID.fromString(accountId));
        return creditMapper.creditPoolListToDtoList(pools);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CreditPoolDto> getCreditPoolsByCustomer(String customerId, String accountId) {
        List<CreditPool> pools = creditPoolRepository.findByCustomerIdAndAccountId(
                UUID.fromString(customerId), UUID.fromString(accountId));
        return creditMapper.creditPoolListToDtoList(pools);
    }

    // ─── Grant operations ───

    @Override
    @Transactional
    public CreditGrantDto grantCredits(CreditGrantRequest request, String accountId) {
        // Idempotency check
        if (request.getIdempotencyKey() != null
                && creditGrantRepository.existsByAccountIdAndIdempotencyKeyAndDeletedAtIsNull(
                UUID.fromString(accountId), request.getIdempotencyKey())) {
            log.warn("Duplicate grant request with idempotency key: {}", request.getIdempotencyKey());
            throw new IllegalArgumentException("Duplicate grant: idempotency key already used");
        }

        CreditPool pool = retrievePool(request.getCreditPoolId(), accountId);
        Account account = pool.getAccount();

        GrantType.valueOf(request.getGrantType()); // validate enum

        CreditGrant grant = new CreditGrant();
        grant.setCreditPool(pool);
        grant.setAccount(account);
        grant.setGrantType(request.getGrantType());
        grant.setAmount(request.getAmount());
        grant.setRemaining(request.getAmount());
        grant.setExpiresAt(request.getExpiresAt());
        grant.setDescription(request.getDescription());
        grant.setIdempotencyKey(request.getIdempotencyKey());
        if (request.getMetadata() != null) {
            grant.setMetadata(request.getMetadata());
        }

        if (request.getSubscriptionId() != null) {
            Subscription sub = subscriptionRepository.findById(UUID.fromString(request.getSubscriptionId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Subscription not found: " + request.getSubscriptionId()));
            grant.setSubscription(sub);
        }

        if (request.getInvoiceId() != null) {
            grant.setInvoice(null); // Will be linked separately if needed
        }

        creditGrantRepository.saveAndFlush(grant);

        // Update pool balance with optimistic locking retry
        updatePoolBalanceWithRetry(pool.getId(), request.getAmount(), CreditTransactionType.GRANT,
                grant, null, null, null, null, request.getDescription(), request.getIdempotencyKey());

        log.info("Granted {} {} to pool {} (grant {})", request.getAmount(), pool.getDenomination(), pool.getId(), grant.getId());
        return creditMapper.creditGrantToDto(grant);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CreditGrantDto> getGrantsByPool(String poolId, String accountId) {
        retrievePool(poolId, accountId); // validate access
        List<CreditGrant> grants = creditGrantRepository.findByCreditPoolId(UUID.fromString(poolId));
        return creditMapper.creditGrantListToDtoList(grants);
    }

    // ─── Deduction ───

    @Override
    @Transactional
    public CreditTransactionDto deductCredits(CreditDeductionRequest request, String accountId) {
        // Idempotency check
        if (request.getIdempotencyKey() != null
                && creditTransactionRepository.existsByAccountIdAndIdempotencyKey(
                UUID.fromString(accountId), request.getIdempotencyKey())) {
            log.warn("Duplicate deduction request with idempotency key: {}", request.getIdempotencyKey());
            throw new IllegalArgumentException("Duplicate deduction: idempotency key already used");
        }

        CreditPool pool = retrievePool(request.getCreditPoolId(), accountId);

        if (CreditPoolStatus.FROZEN.name().equals(pool.getStatus())) {
            throw new IllegalStateException("Cannot deduct from frozen pool: " + pool.getId());
        }

        if (pool.getHardLimit() && pool.getBalance().compareTo(request.getAmount()) < 0) {
            throw new IllegalStateException("Insufficient credits: available=" + pool.getBalance()
                    + ", requested=" + request.getAmount());
        }

        // FIFO deduction from grants
        deductFromGrants(pool.getId(), request.getAmount());

        UUID subscriptionId = request.getSubscriptionId() != null ? UUID.fromString(request.getSubscriptionId()) : null;
        UUID customerId = request.getCustomerId() != null ? UUID.fromString(request.getCustomerId()) : null;

        // Update pool balance
        CreditTransaction tx = updatePoolBalanceWithRetry(pool.getId(), request.getAmount().negate(),
                CreditTransactionType.DEDUCTION, null, subscriptionId, customerId,
                request.getEventId(), null, request.getDescription(), request.getIdempotencyKey());

        log.info("Deducted {} from pool {}", request.getAmount(), pool.getId());
        return creditMapper.creditTransactionToDto(tx);
    }

    // ─── Reversal ───

    @Override
    @Transactional
    public CreditTransactionDto reverseTransaction(String transactionId, String description, String accountId) {
        CreditTransaction original = creditTransactionRepository.findById(UUID.fromString(transactionId))
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));

        if (!original.getAccount().getId().toString().equals(accountId)) {
            throw new ResourceNotFoundException("Transaction not found for account: " + accountId);
        }

        BigDecimal reversalAmount = original.getAmount().negate();

        // If this was a deduction and has a linked grant, restore grant remaining
        if ("DEDUCTION".equals(original.getTransactionType()) && original.getCreditGrant() != null) {
            CreditGrant grant = original.getCreditGrant();
            grant.setRemaining(grant.getRemaining().add(original.getAmount().abs()));
            if (grant.getVoidedAt() != null) {
                grant.setVoidedAt(null);
            }
            creditGrantRepository.save(grant);
        }

        CreditTransaction tx = updatePoolBalanceWithRetry(original.getCreditPool().getId(), reversalAmount,
                CreditTransactionType.REVERSAL, original.getCreditGrant(),
                original.getSubscriptionId(), original.getCustomerId(), original.getEventId(),
                original.getId(), description != null ? description : "Reversal of " + transactionId,
                null);

        log.info("Reversed transaction {} with amount {}", transactionId, reversalAmount);
        return creditMapper.creditTransactionToDto(tx);
    }

    // ─── Pool-subscription linkage ───

    @Override
    @Transactional
    public void linkPoolToSubscription(String poolId, String subscriptionId, String accountId, int drawPriority, BigDecimal drawLimit) {
        CreditPool pool = retrievePool(poolId, accountId);
        Subscription subscription = subscriptionRepository.findById(UUID.fromString(subscriptionId))
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found: " + subscriptionId));

        if (creditPoolSubscriptionRepository.existsByCreditPoolIdAndSubscriptionIdAndDeletedAtIsNull(
                pool.getId(), subscription.getId())) {
            throw new IllegalArgumentException("Pool already linked to subscription");
        }

        CreditPoolSubscription link = new CreditPoolSubscription();
        link.setCreditPool(pool);
        link.setSubscription(subscription);
        link.setAccount(pool.getAccount());
        link.setDrawPriority(drawPriority);
        link.setDrawLimit(drawLimit);

        creditPoolSubscriptionRepository.saveAndFlush(link);
        log.info("Linked pool {} to subscription {}", poolId, subscriptionId);
    }

    @Override
    @Transactional
    public void unlinkPoolFromSubscription(String poolId, String subscriptionId, String accountId) {
        CreditPoolSubscription link = creditPoolSubscriptionRepository
                .findByCreditPoolIdAndSubscriptionIdAndDeletedAtIsNull(
                        UUID.fromString(poolId), UUID.fromString(subscriptionId))
                .orElseThrow(() -> new ResourceNotFoundException("Pool-subscription link not found"));

        if (!link.getAccount().getId().toString().equals(accountId)) {
            throw new ResourceNotFoundException("Link not found for account");
        }

        creditPoolSubscriptionRepository.delete(link); // soft delete via @SQLDelete
        log.info("Unlinked pool {} from subscription {}", poolId, subscriptionId);
    }

    // ─── Lifecycle hooks ───

    @Override
    @Transactional
    public void processCreditGrantsForSubscription(Subscription subscription) {
        UUID planId = subscription.getPlan().getId();
        UUID customerId = subscription.getCustomer().getId();
        UUID accountId = subscription.getAccount().getId();
        UUID subscriptionId = subscription.getId();

        List<PlanCreditAllocation> allocations = planCreditAllocationRepository.findByPlanIdAndDeletedAtIsNull(planId);

        for (PlanCreditAllocation allocation : allocations) {
            CreditModel creditModel = allocation.getCreditModel();
            String denomination = creditModel.getDenomination();
            BigDecimal creditAmount = allocation.getCreditAmount();

            if (denomination == null || creditAmount == null || creditAmount.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Skipping credit allocation for plan {}: missing denomination or amount", planId);
                continue;
            }

            // Find or create pool for this customer + denomination
            CreditPool pool = creditPoolRepository
                    .findByCustomerIdAndAccountIdAndDenomination(customerId, accountId, denomination)
                    .orElseGet(() -> {
                        Account account = subscription.getAccount();
                        Customer customer = subscription.getCustomer();

                        CreditPool newPool = new CreditPool();
                        newPool.setAccount(account);
                        newPool.setCustomer(customer);
                        newPool.setName(denomination + " - " + customerId);
                        newPool.setDenomination(denomination);
                        newPool.setCurrency(subscription.getPlan().getCurrency() != null
                                ? subscription.getPlan().getCurrency() : "USD");
                        Boolean allocationHardLimit = allocation.getHardLimit();
                        newPool.setHardLimit(allocationHardLimit != null ? allocationHardLimit : creditModel.getHardLimit());
                        newPool.setRolloverPolicy(creditModel.getRolloverPolicy() != null
                                ? creditModel.getRolloverPolicy() : "NONE");
                        newPool.setRolloverCap(creditModel.getRolloverCap());
                        newPool.setCreditModel(creditModel);

                        newPool = creditPoolRepository.saveAndFlush(newPool);
                        log.info("Auto-created credit pool '{}' for customer {} denomination {}",
                                newPool.getName(), customerId, denomination);
                        return newPool;
                    });

            // Find or create pool-subscription link
            if (!creditPoolSubscriptionRepository.existsByCreditPoolIdAndSubscriptionIdAndDeletedAtIsNull(
                    pool.getId(), subscriptionId)) {
                CreditPoolSubscription link = new CreditPoolSubscription();
                link.setCreditPool(pool);
                link.setSubscription(subscription);
                link.setAccount(subscription.getAccount());
                link.setDrawPriority(0);

                creditPoolSubscriptionRepository.saveAndFlush(link);
                log.info("Auto-linked pool {} to subscription {}", pool.getId(), subscriptionId);
            }

            // Grant credits with idempotency key based on subscription + period start
            long periodEpochMilli = subscription.getCurrentPeriodStart() != null
                    ? subscription.getCurrentPeriodStart().toEpochMilli()
                    : Instant.now().toEpochMilli();
            String idempotencyKey = "plan_grant_" + subscriptionId + "_" + denomination + "_" + periodEpochMilli;

            if (creditGrantRepository.existsByAccountIdAndIdempotencyKeyAndDeletedAtIsNull(accountId, idempotencyKey)) {
                log.debug("Credit grant already exists for subscription {} period {} denomination {}, skipping",
                        subscriptionId, periodEpochMilli, denomination);
                continue;
            }

            Instant expiresAt = null;
            if (allocation.getGrantExpiresMonths() != null) {
                expiresAt = Instant.now()
                        .atOffset(java.time.ZoneOffset.UTC)
                        .plusMonths(allocation.getGrantExpiresMonths())
                        .toInstant();
            }

            CreditGrantRequest grantRequest = new CreditGrantRequest();
            grantRequest.setCreditPoolId(pool.getId().toString());
            grantRequest.setAmount(creditAmount);
            grantRequest.setGrantType(GrantType.PLAN_INCLUDED.name());
            grantRequest.setSubscriptionId(subscriptionId.toString());
            grantRequest.setDescription("Plan-included " + denomination + " for subscription " + subscriptionId);
            grantRequest.setIdempotencyKey(idempotencyKey);
            grantRequest.setExpiresAt(expiresAt);

            grantCredits(grantRequest, accountId.toString());
            log.info("Granted {} {} to customer {} for subscription {} (plan {})",
                    creditAmount, denomination, customerId, subscriptionId, planId);
        }
    }

    @Override
    @Transactional
    public void clawBackPlanIncludedCredits(UUID subscriptionId, UUID accountId) {
        List<CreditPoolSubscription> links = creditPoolSubscriptionRepository.findBySubscriptionId(subscriptionId);

        for (CreditPoolSubscription link : links) {
            CreditPool pool = link.getCreditPool();
            List<CreditGrant> planGrants = creditGrantRepository.findActivePlanIncludedGrants(
                    pool.getId(), subscriptionId);

            for (CreditGrant grant : planGrants) {
                BigDecimal remainingToClawBack = grant.getRemaining();
                if (remainingToClawBack.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                // Void the grant
                grant.setVoidedAt(Instant.now());
                grant.setRemaining(BigDecimal.ZERO);
                creditGrantRepository.save(grant);

                // Deduct from pool balance
                updatePoolBalanceWithRetry(pool.getId(), remainingToClawBack.negate(),
                        CreditTransactionType.DEDUCTION, grant, subscriptionId, null,
                        null, null, "Claw back on subscription cancellation", null);

                log.info("Clawed back {} credits from grant {} on subscription cancellation",
                        remainingToClawBack, grant.getId());
            }
        }
    }

    @Override
    @Transactional
    public void applyRolloverPolicy(UUID poolId) {
        CreditPool pool = creditPoolRepository.findById(poolId)
                .orElseThrow(() -> new ResourceNotFoundException("Pool not found: " + poolId));

        RolloverPolicy policy = RolloverPolicy.valueOf(pool.getRolloverPolicy());

        switch (policy) {
            case FULL -> // No action needed - balance carries forward
                    log.info("Pool {} has FULL rollover, carrying forward all {} credits", poolId, pool.getBalance());
            case NONE -> {
                BigDecimal toExpire = pool.getBalance();
                if (toExpire.compareTo(BigDecimal.ZERO) > 0) {
                    expireRemainingGrants(pool);
                    updatePoolBalanceWithRetry(poolId, toExpire.negate(), CreditTransactionType.EXPIRATION,
                            null, null, null, null, null,
                            "Period rollover: NONE policy", null);
                    log.info("Expired {} credits from pool {} (NONE policy)", toExpire, poolId);
                }
            }
            case CAPPED -> {
                BigDecimal cap = pool.getRolloverCap() != null ? pool.getRolloverCap() : BigDecimal.ZERO;
                BigDecimal excess = pool.getBalance().subtract(cap);
                if (excess.compareTo(BigDecimal.ZERO) > 0) {
                    updatePoolBalanceWithRetry(poolId, excess.negate(), CreditTransactionType.EXPIRATION,
                            null, null, null, null, null,
                            "Period rollover: CAPPED at " + cap, null);
                    log.info("Expired {} excess credits from pool {} (CAPPED at {})", excess, poolId, cap);
                }
            }
        }
    }

    @Override
    @Transactional
    public void applyRolloverPoliciesForSubscription(UUID subscriptionId) {
        List<CreditPoolSubscription> links = creditPoolSubscriptionRepository.findBySubscriptionId(subscriptionId);
        for (CreditPoolSubscription link : links) {
            try {
                applyRolloverPolicy(link.getCreditPool().getId());
            } catch (Exception e) {
                log.error("Failed to apply rollover for pool {}: {}", link.getCreditPool().getId(), e.getMessage());
            }
        }
    }

    @Override
    @Transactional
    public void processExpiredGrants() {
        List<CreditGrant> expired = creditGrantRepository.findExpiredGrantsWithRemaining(Instant.now());

        for (CreditGrant grant : expired) {
            BigDecimal remaining = grant.getRemaining();
            grant.setRemaining(BigDecimal.ZERO);
            creditGrantRepository.save(grant);

            updatePoolBalanceWithRetry(grant.getCreditPool().getId(), remaining.negate(),
                    CreditTransactionType.EXPIRATION, grant, null, null, null, null,
                    "Grant expired", null);

            log.info("Expired grant {} with {} remaining credits", grant.getId(), remaining);
        }
    }

    // ─── Hard limit check ───

    @Override
    @Transactional(readOnly = true)
    public boolean hasAvailableCredits(UUID poolId, BigDecimal requiredAmount) {
        CreditPool pool = creditPoolRepository.findById(poolId).orElse(null);
        if (pool == null) return false;
        return pool.getBalance().compareTo(requiredAmount) >= 0;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean checkHardLimitForSubscription(UUID subscriptionId, UUID accountId, String denomination, BigDecimal requiredAmount) {
        if (denomination == null) return true; // non-credit feature, no limit

        List<CreditPoolSubscription> links = creditPoolSubscriptionRepository
                .findBySubscriptionIdAndCreditPool_DenominationOrderByDrawPriority(subscriptionId, denomination);

        BigDecimal totalAvailable = BigDecimal.ZERO;
        boolean anyHardLimit = false;
        for (CreditPoolSubscription link : links) {
            CreditPool pool = link.getCreditPool();
            if (pool.getHardLimit()) {
                anyHardLimit = true;
            }
            totalAvailable = totalAvailable.add(pool.getBalance());
        }
        return !anyHardLimit || totalAvailable.compareTo(requiredAmount) >= 0;
    }

    // ─── Ledger queries ───

    @Override
    @Transactional(readOnly = true)
    public List<CreditTransactionDto> getTransactionsByPool(String poolId, String accountId) {
        retrievePool(poolId, accountId); // validate access
        List<CreditTransaction> transactions = creditTransactionRepository.findByCreditPoolId(UUID.fromString(poolId));
        return creditMapper.creditTransactionListToDtoList(transactions);
    }

    // ─── Credit offset for billing ───

    @Override
    @Transactional
    public BigDecimal applyCreditOffset(UUID poolId, BigDecimal totalCharge, UUID subscriptionId, UUID accountId, String description) {
        CreditPool pool = creditPoolRepository.findById(poolId)
                .orElseThrow(() -> new ResourceNotFoundException("Pool not found: " + poolId));

        BigDecimal creditOffset = totalCharge.min(pool.getBalance());

        if (creditOffset.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // FIFO deduction
        deductFromGrants(poolId, creditOffset);

        updatePoolBalanceWithRetry(poolId, creditOffset.negate(), CreditTransactionType.DEDUCTION,
                null, subscriptionId, null, null, null,
                description != null ? description : "Billing credit offset", null);

        log.info("Applied credit offset of {} from pool {} for subscription {}", creditOffset, poolId, subscriptionId);
        return creditOffset;
    }

    // ─── Delta grants for upgrades ───

    @Override
    @Transactional
    public void grantDeltaCredits(Subscription subscription, String denomination, BigDecimal deltaAmount, UUID accountId) {
        UUID customerId = subscription.getCustomer().getId();
        UUID subscriptionId = subscription.getId();

        CreditPool pool = creditPoolRepository
                .findByCustomerIdAndAccountIdAndDenomination(customerId, accountId, denomination)
                .orElse(null);

        if (pool == null) {
            log.warn("No credit pool found for customer {} denomination {} — skipping delta grant", customerId, denomination);
            return;
        }

        String idempotencyKey = "upgrade_delta_" + subscriptionId + "_" + denomination + "_" + Instant.now().toEpochMilli();

        CreditGrantRequest grantRequest = new CreditGrantRequest();
        grantRequest.setCreditPoolId(pool.getId().toString());
        grantRequest.setAmount(deltaAmount);
        grantRequest.setGrantType(GrantType.PLAN_INCLUDED.name());
        grantRequest.setSubscriptionId(subscriptionId.toString());
        grantRequest.setDescription("Upgrade delta: +" + deltaAmount + " " + denomination);
        grantRequest.setIdempotencyKey(idempotencyKey);

        grantCredits(grantRequest, accountId.toString());
        log.info("Granted delta {} {} to customer {} for subscription upgrade", deltaAmount, denomination, customerId);
    }

    // ─── Internal helpers ───

    private CreditPool retrievePool(String poolId, String accountId) {
        return creditPoolRepository.findByIdAndAccountId(UUID.fromString(poolId), UUID.fromString(accountId))
                .orElseThrow(() -> new ResourceNotFoundException("Credit pool not found: " + poolId));
    }

    private void deductFromGrants(UUID poolId, BigDecimal amount) {
        List<CreditGrant> activeGrants = creditGrantRepository.findActiveGrantsByPoolIdOrderByCreatedAsc(poolId);
        BigDecimal remaining = amount;

        for (CreditGrant grant : activeGrants) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal deductFromGrant = remaining.min(grant.getRemaining());
            grant.setRemaining(grant.getRemaining().subtract(deductFromGrant));
            creditGrantRepository.save(grant);

            remaining = remaining.subtract(deductFromGrant);
        }
    }

    private void expireRemainingGrants(CreditPool pool) {
        List<CreditGrant> activeGrants = creditGrantRepository.findActiveGrantsByPoolIdOrderByCreatedAsc(pool.getId());
        for (CreditGrant grant : activeGrants) {
            grant.setRemaining(BigDecimal.ZERO);
            creditGrantRepository.save(grant);
        }
    }

    private CreditTransaction updatePoolBalanceWithRetry(
            UUID poolId, BigDecimal delta, CreditTransactionType txType,
            CreditGrant grant, UUID subscriptionId, UUID customerId,
            UUID eventId, UUID reversedTxId, String description, String idempotencyKey) {

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            CreditPool pool = creditPoolRepository.findById(poolId)
                    .orElseThrow(() -> new ResourceNotFoundException("Pool not found: " + poolId));
            BigDecimal balanceBefore = pool.getBalance();
            long currentVersion = pool.getVersion();

            int updatedRows = creditPoolRepository.updatePoolBalanceAtomically(
                    poolId, delta, txType.name(), currentVersion);

            if (updatedRows == 1) {
                BigDecimal balanceAfter = balanceBefore.add(delta);
                return createTransactionRecord(pool, delta, balanceBefore, balanceAfter,
                        txType, grant, subscriptionId, customerId, eventId, reversedTxId, description, idempotencyKey);
            }

            if (attempt == MAX_RETRY_ATTEMPTS) {
                log.error("Failed to update pool {} after {} attempts due to optimistic locking", poolId, MAX_RETRY_ATTEMPTS);
                throw new IllegalStateException("Concurrent credit operation failed after retries.");
            }
            log.warn("OCC conflict on pool {}, retry {}/{}", poolId, attempt, MAX_RETRY_ATTEMPTS);
            try {
                Thread.sleep((long) (50 * Math.pow(2, attempt - 1)));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted during retry", ie);
            }
        }
        throw new IllegalStateException("Unreachable");
    }

    private CreditTransaction createTransactionRecord(
            CreditPool pool, BigDecimal delta, BigDecimal balanceBefore, BigDecimal balanceAfter,
            CreditTransactionType txType, CreditGrant grant, UUID subscriptionId, UUID customerId,
            UUID eventId, UUID reversedTxId, String description, String idempotencyKey) {

        CreditTransaction tx = new CreditTransaction();
        tx.setCreditPool(pool);
        tx.setAccount(pool.getAccount());
        tx.setTransactionType(txType.name());
        tx.setAmount(delta);
        tx.setBalanceBefore(balanceBefore);
        tx.setBalanceAfter(balanceAfter);
        tx.setDescription(description);
        tx.setIdempotencyKey(idempotencyKey);
        tx.setMetadata(new HashMap<>());

        if (grant != null) {
            tx.setCreditGrant(grant);
        }
        if (subscriptionId != null) {
            tx.setSubscriptionId(subscriptionId);
        }
        if (customerId != null) {
            tx.setCustomerId(customerId);
        }
        if (eventId != null) {
            tx.setEventId(eventId);
        }
        if (reversedTxId != null) {
            tx.setReversedTransactionId(reversedTxId);
        }

        creditTransactionRepository.saveAndFlush(tx);
        return tx;
    }
}
