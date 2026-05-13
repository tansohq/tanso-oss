package com.tansoflow.tansocore.auth;

import com.tansoflow.tansocore.entity.AccountSetting;
import com.tansoflow.tansocore.model.exception.PlatformModeException;
import com.tansoflow.tansocore.repository.AccountSettingRepository;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
public class PlatformModeAspect {
    private final AccountSettingRepository accountSettingRepository;

    @Before("@annotation(com.tansoflow.tansocore.auth.RequiresFullPlatformMode)")
    public void checkPlatformMode() {
        UserContext userContext = (UserContext) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        AccountSetting setting = accountSettingRepository.findAccountSettingById(
                UUID.fromString(userContext.getAccountId()));
        if (setting != null && setting.isObserveMode()) {
            throw new PlatformModeException();
        }
    }
}
