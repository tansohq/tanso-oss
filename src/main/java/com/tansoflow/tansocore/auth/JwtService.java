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
package com.tansoflow.tansocore.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.tansoflow.tansocore.model.auth.JwtClaims;
import com.tansoflow.tansocore.property.SecurityProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;

@Service
@Slf4j
@RequiredArgsConstructor
public class JwtService {
    private final SecurityProperty securityProperty;

    private Algorithm algorithm() {
        return Algorithm.HMAC256(securityProperty.getJwt().getSecret());
    }

    private JWTVerifier verifier() {
        return JWT
                .require(algorithm())
                .withIssuer(securityProperty.getJwt().getIssuer())
                .build();
    }

    /**
     * Generate a short-lived access token for UI usage (Retool, future dashboard).
     */
    public String generateAccessToken(String userId,
                                      String accountId,
                                      String email,
                                      String role) {

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(securityProperty.getJwt().getAccessTokenTtlSeconds());

        return JWT.create()
                .withIssuer(securityProperty.getJwt().getIssuer())
                .withSubject(userId)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiresAt))
                .withClaim("account_id", accountId)
                .withClaim("email", email)
                .withClaim("role", role)
                .withClaim("scope", "ui")
                .sign(algorithm());
    }

    /**
     * Parse + validate the token or throw JwtValidationException if invalid/expired.
     */
    public JwtClaims parseAndValidate(String token) {
        DecodedJWT jwt = verifier().verify(token);

        String subject = jwt.getSubject();
        String accountId = jwt.getClaim("account_id").asString();
        String email = jwt.getClaim("email").asString();
        String role = jwt.getClaim("role").asString();

        return new JwtClaims(subject, accountId, email, role);
    }
}

