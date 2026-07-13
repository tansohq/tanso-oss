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

    boolean existsByEmail(String email);
}
