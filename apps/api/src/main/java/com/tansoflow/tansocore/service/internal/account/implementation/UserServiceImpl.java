package com.tansoflow.tansocore.service.internal.account.implementation;

import com.tansoflow.tansocore.auth.JwtService;
import com.tansoflow.tansocore.entity.User;
import com.tansoflow.tansocore.model.account.request.UsernameAndPasswordRequest;
import com.tansoflow.tansocore.model.auth.response.JwtResponse;
import com.tansoflow.tansocore.model.customer.request.CustomerRequest;
import com.tansoflow.tansocore.model.exception.AuthenticationException;
import com.tansoflow.tansocore.repository.UserRepository;
import com.tansoflow.tansocore.service.internal.account.UserService;
import com.tansoflow.tansocore.service.internal.audit.AuditHelper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;
    private final AuditHelper auditHelper;

    @Override
    public User createUser(CustomerRequest request, String password) {
        User user = new User();
        user.setUsername(request.getEmail());
        user.setEmail(request.getEmail());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPassword(passwordEncoder.encode(password));
        log.info("Creating new user");
        return userRepository.save(user);
    }

    @Override
    public User createUser(CustomerRequest request, String password, UUID userId, boolean useNativeInsert) {
        log.info("Creating new user with pre-set ID {}", userId);
        if (useNativeInsert) {
            entityManager.createNativeQuery(
                    "INSERT INTO users (user_id, username, password, first_name, last_name, email, created_at, modified_at) " +
                    "VALUES (:id, :username, :password, :firstName, :lastName, :email, NOW(), NOW())")
                    .setParameter("id", userId)
                    .setParameter("username", request.getEmail())
                    .setParameter("password", passwordEncoder.encode(password))
                    .setParameter("firstName", request.getFirstName())
                    .setParameter("lastName", request.getLastName())
                    .setParameter("email", request.getEmail())
                    .executeUpdate();
            return entityManager.find(User.class, userId);
        }
        User user = new User();
        user.setId(userId);
        user.setUsername(request.getEmail());
        user.setEmail(request.getEmail());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPassword(passwordEncoder.encode(password));
        entityManager.persist(user);
        return user;
    }

    @Override
    public JwtResponse generateJwtTokenForUser(UsernameAndPasswordRequest request) {
        User user = userRepository.findUserByUsernameIgnoreCase(request.getUsername());

        if (user == null || !validateUserToRequest(user, request)) {
            auditHelper.audit("LOGIN_FAILED", null, null, "Credential validation failed");
            throw new AuthenticationException("Invalid credentials");
        }

        if (user.getUsersAccounts().isEmpty()) {
            throw new IllegalArgumentException("User has no account linked to it.");
        }
        UUID accountUuid = user.getUsersAccounts().stream().findFirst().get().getAccount().getId();
        String accountId = accountUuid.toString();

        String token = jwtService.generateAccessToken(user.getId().toString(), accountId, user.getEmail(), "ROLE_TANSO_UI");

        log.info("Login successful: userId={}, accountId={}", user.getId(), accountId);
        auditHelper.audit("LOGIN_SUCCESS", user.getId(), accountUuid);

        JwtResponse jwtResponse = new JwtResponse();
        jwtResponse.setToken(token);
        return jwtResponse;
    }

    @Override
    public JwtResponse generateJwtTokenForUser(User user, String accountId) {
        JwtResponse jwtResponse = new JwtResponse();
        String token = jwtService.generateAccessToken(user.getId().toString(), accountId, user.getEmail(), "ROLE_TANSO_UI");

        log.info("Login successful: userId={}, accountId={}", user.getId(), accountId);

        jwtResponse.setToken(token);
        return jwtResponse;
    }

    @Override
    public void changePassword(String userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(java.util.UUID.fromString(userId))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password changed successfully for user: {}", userId);
    }

    @Override
    public void updatePasswordByEmail(String email, String newPassword) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password updated via replication for userId: {}", user.getId());
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email).isPresent();
    }

    private boolean validateUserToRequest(User user, UsernameAndPasswordRequest request) {
        return passwordEncoder.matches(request.getPassword(), user.getPassword());
    }
}
