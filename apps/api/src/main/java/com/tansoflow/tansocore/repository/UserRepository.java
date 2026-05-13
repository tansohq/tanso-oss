package com.tansoflow.tansocore.repository;

import com.tansoflow.tansocore.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    User findUserByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);
}
