package com.tansoflow.tansocore.service.internal.account;

import com.tansoflow.tansocore.entity.User;
import com.tansoflow.tansocore.model.account.request.UsernameAndPasswordRequest;
import com.tansoflow.tansocore.model.auth.response.JwtResponse;
import com.tansoflow.tansocore.model.customer.request.CustomerRequest;

import java.util.UUID;

public interface UserService {
    JwtResponse generateJwtTokenForUser(UsernameAndPasswordRequest request);

    JwtResponse generateJwtTokenForUser(User user, String accountId);

    User createUser(CustomerRequest request, String password);

    User createUser(CustomerRequest request, String password, UUID userId, boolean useNativeInsert);

    /**
     * Changes the password for a user after verifying the current password.
     *
     * @param userId          The UUID of the user
     * @param currentPassword The user's current password (plaintext)
     * @param newPassword     The new password (plaintext, will be hashed)
     * @throws IllegalArgumentException if current password is incorrect or user not found
     */
    void changePassword(String userId, String currentPassword, String newPassword);

    /**
     * Updates the password for a user identified by email.
     * Used for cross-environment replication where we identify users by email.
     *
     * @param email       The email address of the user
     * @param newPassword The new password (plaintext, will be hashed)
     * @throws IllegalArgumentException if user not found
     */
    void updatePasswordByEmail(String email, String newPassword);

    boolean existsByEmail(String email);
}
