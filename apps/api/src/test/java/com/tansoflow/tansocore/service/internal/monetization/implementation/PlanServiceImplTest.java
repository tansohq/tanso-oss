package com.tansoflow.tansocore.service.internal.monetization.implementation;

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.mapper.monetization.PlanMapper;
import com.tansoflow.tansocore.model.plan.BillingTiming;
import com.tansoflow.tansocore.model.plan.PlanDto;
import com.tansoflow.tansocore.model.plan.PlanStatus;
import com.tansoflow.tansocore.model.plan.request.PlanRequest;
import com.tansoflow.tansocore.repository.PlanRepository;
import com.tansoflow.tansocore.service.internal.account.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanServiceImplTest {

    @Mock
    private PlanMapper planMapper;
    @Mock
    private PlanRepository planRepository;
    @Mock
    private AccountService accountService;

    @InjectMocks
    private PlanServiceImpl planService;

    private String accountId;
    private Account account;
    private PlanRequest planRequest;
    private Plan planEntity;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID().toString();
        account = new Account();
        account.setId(UUID.fromString(accountId));

        planRequest = new PlanRequest();
        planRequest.setKey("test-plan");
        planRequest.setBillingTiming("IN_ADVANCE");

        planEntity = new Plan();
        planEntity.setKey("test-plan");
        planEntity.setStatus(PlanStatus.DRAFT.name());
    }

    @Test
    void testCreatePlans_NullKey_ThrowsException() {
        // Setup
        when(accountService.retrieveAccount(accountId)).thenReturn(account);
        planEntity.setKey(null);
        when(planMapper.planRequestToPlanEntity(planRequest)).thenReturn(planEntity);

        // Execute & Verify
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                planService.createPlans(accountId, planRequest));
        assertEquals("Plan key cannot be null", exception.getMessage());
    }

    @Test
    void testCreatePlans_DuplicateKey_ThrowsException() {
        // Setup
        when(accountService.retrieveAccount(accountId)).thenReturn(account);
        when(planMapper.planRequestToPlanEntity(planRequest)).thenReturn(planEntity);
        when(planRepository.existsByKeyAndAccountId("test-plan", account.getId())).thenReturn(true);

        // Execute & Verify
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                planService.createPlans(accountId, planRequest));
        assertEquals("Plan with key: test-plan already exists", exception.getMessage());
    }

    @Test
    void testCreatePlans_DefaultBillingTiming_InAdvance() {
        // Setup - no billingTiming set
        planRequest.setBillingTiming(null);

        when(accountService.retrieveAccount(accountId)).thenReturn(account);
        when(planMapper.planRequestToPlanEntity(planRequest)).thenReturn(planEntity);
        when(planRepository.existsByKeyAndAccountId("test-plan", account.getId())).thenReturn(false);
        when(planRepository.save(any(Plan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PlanDto planDto = new PlanDto();
        when(planMapper.planEntityToPlanDto(any(Plan.class))).thenReturn(planDto);

        // Execute
        PlanDto result = planService.createPlans(accountId, planRequest);

        // Verify
        assertEquals(planDto, result);
        assertEquals(BillingTiming.IN_ADVANCE.name(), planEntity.getBillingTiming());
        assertEquals(account, planEntity.getAccount());
        verify(planRepository).save(planEntity);
    }

    @Test
    void testCreatePlans_ExplicitBillingTiming_InArrears() {
        // Setup
        planRequest.setBillingTiming("IN_ARREARS");

        when(accountService.retrieveAccount(accountId)).thenReturn(account);
        when(planMapper.planRequestToPlanEntity(planRequest)).thenReturn(planEntity);
        when(planRepository.existsByKeyAndAccountId("test-plan", account.getId())).thenReturn(false);
        when(planRepository.save(any(Plan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PlanDto planDto = new PlanDto();
        when(planMapper.planEntityToPlanDto(any(Plan.class))).thenReturn(planDto);

        // Execute
        PlanDto result = planService.createPlans(accountId, planRequest);

        // Verify
        assertEquals(planDto, result);
        assertEquals(BillingTiming.IN_ARREARS.name(), planEntity.getBillingTiming());
        assertEquals(account, planEntity.getAccount());
        verify(planRepository).save(planEntity);
    }

    @Test
    void testUpdatePlan_Success() {
        // Setup
        String planId = UUID.randomUUID().toString();
        when(accountService.retrieveAccount(accountId)).thenReturn(account);
        when(planRepository.findByIdAndAccount(UUID.fromString(planId), account)).thenReturn(Optional.of(planEntity));
        when(planRepository.save(planEntity)).thenReturn(planEntity);
        
        PlanDto planDto = new PlanDto();
        when(planMapper.planEntityToPlanDto(planEntity)).thenReturn(planDto);

        // Execute
        PlanDto result = planService.updatePlan(accountId, planId, planRequest);

        // Verify
        assertNotNull(result);
        verify(planMapper).updatePlanFromPlanRequest(planRequest, planEntity);
        verify(planRepository).save(planEntity);
    }

    @Test
    void testUpdatePlan_DuplicateKey_ThrowsException() {
        // Setup
        String planId = UUID.randomUUID().toString();
        planRequest.setKey("new-key");
        planEntity.setKey("old-key");

        when(accountService.retrieveAccount(accountId)).thenReturn(account);
        when(planRepository.findByIdAndAccount(UUID.fromString(planId), account)).thenReturn(Optional.of(planEntity));
        when(planRepository.existsByKeyAndAccountId("new-key", account.getId())).thenReturn(true);

        // Execute & Verify
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                planService.updatePlan(accountId, planId, planRequest));
        assertEquals("Plan with key: new-key already exists", exception.getMessage());
    }

    @Test
    void testCreatePlans_DuplicateNameDifferentKey_Success() {
        // Setup
        planRequest.setKey("key2");
        planRequest.setName("Same Name");
        planEntity.setKey("key2");
        planEntity.setName("Same Name");

        when(accountService.retrieveAccount(accountId)).thenReturn(account);
        when(planMapper.planRequestToPlanEntity(planRequest)).thenReturn(planEntity);
        when(planRepository.existsByKeyAndAccountId("key2", account.getId())).thenReturn(false);
        when(planRepository.save(any(Plan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PlanDto planDto = new PlanDto();
        when(planMapper.planEntityToPlanDto(any(Plan.class))).thenReturn(planDto);

        // Execute
        PlanDto result = planService.createPlans(accountId, planRequest);

        // Verify
        assertNotNull(result);
        verify(planRepository).save(planEntity);
    }

    @Test
    void testUpdatePlan_NotFound_ThrowsException() {
        // Setup
        String planId = UUID.randomUUID().toString();
        when(accountService.retrieveAccount(accountId)).thenReturn(account);
        when(planRepository.findByIdAndAccount(UUID.fromString(planId), account)).thenReturn(Optional.empty());

        // Execute & Verify
        assertThrows(IllegalArgumentException.class, () ->
                planService.updatePlan(accountId, planId, planRequest));
    }

    @Test
    void testIsOwner_True() {
        // Setup
        String planId = UUID.randomUUID().toString();
        planEntity.setAccount(account);
        
        when(accountService.retrieveAccount(accountId)).thenReturn(account);
        when(planRepository.findByIdAndAccount(UUID.fromString(planId), account)).thenReturn(Optional.of(planEntity));

        // Execute
        boolean result = planService.isOwner(accountId, planId);

        // Verify
        assertTrue(result);
    }
}
