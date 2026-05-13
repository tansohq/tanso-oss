package com.tansoflow.tansocore.service.internal.account;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PasswordService {
    private final UserService userService;

    public void changePassword(String userId, String email, String currentPassword, String newPassword) {
        userService.changePassword(userId, currentPassword, newPassword);
    }
}
