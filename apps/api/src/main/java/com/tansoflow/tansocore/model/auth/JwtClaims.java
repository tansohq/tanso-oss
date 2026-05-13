package com.tansoflow.tansocore.model.auth;

public record JwtClaims(
        String subject,      // user id
        String accountId,
        String email,
        String role
) {}
