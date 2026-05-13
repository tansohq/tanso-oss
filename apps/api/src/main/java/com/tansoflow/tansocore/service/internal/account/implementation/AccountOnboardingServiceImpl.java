package com.tansoflow.tansocore.service.internal.account.implementation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tansoflow.tansocore.entity.AccountSetting;
import com.tansoflow.tansocore.entity.User;
import com.tansoflow.tansocore.model.account.IntakeDataDto;
import com.tansoflow.tansocore.model.account.OnboardingProgressDto;
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.repository.AccountSettingRepository;
import com.tansoflow.tansocore.repository.UserRepository;
import com.tansoflow.tansocore.service.internal.account.AccountOnboardingService;
import com.tansoflow.tansocore.service.internal.audit.AuditHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountOnboardingServiceImpl implements AccountOnboardingService {

    private static final Set<String> VALID_STEP_KEYS = Set.of(
            "intake_completed",
            "intake_skipped",
            "mode_selected",
            "sdk_installed",
            "first_customer_created",
            "first_event_sent",
            "mcp_connected",
            "stripe_connected",
            "first_plan_created",
            "first_entitlement_checked"
    );

    private final AccountSettingRepository accountSettingRepository;
    private final UserRepository userRepository;
    private final AuditHelper auditHelper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void saveIntakeData(UUID accountId, UUID actorUserId, IntakeDataDto intakeData) {
        User user = findUserOrThrow(actorUserId);

        @SuppressWarnings("unchecked")
        Map<String, Object> intakeMap = objectMapper.convertValue(intakeData, Map.class);
        user.setIntakeData(intakeMap);

        userRepository.save(user);

        auditHelper.audit("ONBOARDING_INTAKE_SAVED", actorUserId, accountId,
                "User", actorUserId.toString(),
                "role=" + intakeData.getRole() + ", buildingType=" + intakeData.getBuildingType() + ", goal=" + intakeData.getGoal());

        log.debug("Saved onboarding intake data for user {}", actorUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public IntakeDataDto getIntakeData(UUID accountId) {
        // For reading, we need to find the user by account. Use actorUserId from the controller instead.
        // This method signature takes accountId for backward compat but we look up by the first user.
        // TODO: refactor interface to take userId directly
        return null;
    }

    public IntakeDataDto getIntakeDataByUserId(UUID userId) {
        User user = findUserOrThrow(userId);

        if (user.getIntakeData() == null) {
            return null;
        }

        return objectMapper.convertValue(user.getIntakeData(), IntakeDataDto.class);
    }

    @Override
    @Transactional(readOnly = true)
    public OnboardingProgressDto getOnboardingProgress(UUID accountId) {
        AccountSetting setting = findSettingsOrThrow(accountId);

        if (setting.getOnboardingProgress() == null) {
            OnboardingProgressDto dto = new OnboardingProgressDto();
            dto.setCompletedSteps(new ArrayList<>());
            return dto;
        }

        return objectMapper.convertValue(setting.getOnboardingProgress(), OnboardingProgressDto.class);
    }

    @Override
    @Transactional
    public OnboardingProgressDto completeOnboardingStep(UUID accountId, UUID actorUserId, String stepKey) {
        if (!VALID_STEP_KEYS.contains(stepKey)) {
            throw new IllegalArgumentException("Invalid onboarding step key: " + stepKey);
        }

        AccountSetting setting = findSettingsOrThrow(accountId);

        OnboardingProgressDto progress;
        if (setting.getOnboardingProgress() != null) {
            progress = objectMapper.convertValue(setting.getOnboardingProgress(), OnboardingProgressDto.class);
        } else {
            progress = new OnboardingProgressDto();
            progress.setCompletedSteps(new ArrayList<>());
        }

        List<String> steps = progress.getCompletedSteps();
        if (!steps.contains(stepKey)) {
            steps.add(stepKey);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> progressMap = objectMapper.convertValue(progress, Map.class);
        setting.setOnboardingProgress(progressMap);

        accountSettingRepository.save(setting);

        auditHelper.audit("ONBOARDING_STEP_COMPLETED", actorUserId, accountId,
                "AccountSetting", accountId.toString(), "step=" + stepKey);

        log.debug("Completed onboarding step '{}' for account {}", stepKey, accountId);

        return progress;
    }

    private AccountSetting findSettingsOrThrow(UUID accountId) {
        AccountSetting setting = accountSettingRepository.findAccountSettingById(accountId);
        if (setting == null) {
            throw new ResourceNotFoundException("AccountSetting not found for account: " + accountId);
        }
        return setting;
    }

    private User findUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }
}
