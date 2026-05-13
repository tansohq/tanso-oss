package com.tansoflow.tansocore.service.internal.monetization;

import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.model.credit.CreditGrantDto;
import com.tansoflow.tansocore.model.credit.CreditModelDto;
import com.tansoflow.tansocore.model.credit.CreditPoolDto;
import com.tansoflow.tansocore.model.credit.CreditTransactionDto;
import com.tansoflow.tansocore.model.credit.PlanCreditAllocationDto;
import com.tansoflow.tansocore.model.credit.request.CreateCreditModelRequest;
import com.tansoflow.tansocore.model.credit.request.CreateCreditPoolRequest;
import com.tansoflow.tansocore.model.credit.request.CreditDeductionRequest;
import com.tansoflow.tansocore.model.credit.request.CreditGrantRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface CreditService {

    // ─── Credit Model CRUD ───

    @Transactional
    CreditModelDto createCreditModel(CreateCreditModelRequest request, String accountId);

    List<CreditModelDto> getCreditModelsByAccount(String accountId);

    CreditModelDto getCreditModel(String id, String accountId);

    @Transactional
    void deleteCreditModel(String id, String accountId);

    // ─── Credit Model — Plan Allocation ───

    @Transactional
    void addCreditAllocationToPlan(String planId, String creditModelId, BigDecimal creditAmount, Integer grantExpiresMonths, Boolean hardLimit, String accountId);

    List<PlanCreditAllocationDto> getCreditAllocationsForPlan(String planId, String accountId);

    @Transactional
    void removeCreditAllocationFromPlan(String planId, String creditModelId, String accountId);

    // ─── Pool CRUD ───

    @Transactional
    CreditPoolDto createCreditPool(CreateCreditPoolRequest request, String accountId);

    CreditPoolDto getCreditPool(String poolId, String accountId);

    List<CreditPoolDto> getCreditPoolsByAccount(String accountId);

    List<CreditPoolDto> getCreditPoolsByCustomer(String customerId, String accountId);

    // ─── Grant operations ───

    @Transactional
    CreditGrantDto grantCredits(CreditGrantRequest request, String accountId);

    List<CreditGrantDto> getGrantsByPool(String poolId, String accountId);

    // ─── Deduction ───

    @Transactional
    CreditTransactionDto deductCredits(CreditDeductionRequest request, String accountId);

    // ─── Reversal ───

    @Transactional
    CreditTransactionDto reverseTransaction(String transactionId, String description, String accountId);

    // ─── Pool-subscription linkage ───

    @Transactional
    void linkPoolToSubscription(String poolId, String subscriptionId, String accountId, int drawPriority, BigDecimal drawLimit);

    @Transactional
    void unlinkPoolFromSubscription(String poolId, String subscriptionId, String accountId);

    // ─── Lifecycle hooks ───

    @Transactional
    void processCreditGrantsForSubscription(Subscription subscription);

    @Transactional
    void clawBackPlanIncludedCredits(UUID subscriptionId, UUID accountId);

    @Transactional
    void applyRolloverPolicy(UUID poolId);

    @Transactional
    void applyRolloverPoliciesForSubscription(UUID subscriptionId);

    @Transactional
    void processExpiredGrants();

    // ─── Hard limit check ───

    boolean hasAvailableCredits(UUID poolId, BigDecimal requiredAmount);

    boolean checkHardLimitForSubscription(UUID subscriptionId, UUID accountId, String denomination, BigDecimal requiredAmount);

    // ─── Ledger queries ───

    List<CreditTransactionDto> getTransactionsByPool(String poolId, String accountId);

    // ─── Credit offset for billing ───

    @Transactional
    BigDecimal applyCreditOffset(UUID poolId, BigDecimal totalCharge, UUID subscriptionId, UUID accountId, String description);

    // ─── Delta grants for upgrades ───

    @Transactional
    void grantDeltaCredits(Subscription subscription, String denomination, BigDecimal deltaAmount, UUID accountId);
}
