package com.tansoflow.tansocore.service.internal.account;

import com.tansoflow.tansocore.model.account.IntakeDataDto;
import com.tansoflow.tansocore.model.account.OnboardingProgressDto;

import java.util.UUID;

public interface AccountOnboardingService {

    void saveIntakeData(UUID accountId, UUID actorUserId, IntakeDataDto intakeData);

    IntakeDataDto getIntakeData(UUID accountId);

    IntakeDataDto getIntakeDataByUserId(UUID userId);

    OnboardingProgressDto getOnboardingProgress(UUID accountId);

    OnboardingProgressDto completeOnboardingStep(UUID accountId, UUID actorUserId, String stepKey);
}
